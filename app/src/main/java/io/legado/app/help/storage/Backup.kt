package io.legado.app.help.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.model.BookCover
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFolderIfNotExist
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.data.repository.dataStore
import io.legado.app.utils.externalFiles
import kotlinx.coroutines.flow.first
import io.legado.app.utils.getFile
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.inputStream
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 备份
 */
object Backup {

    val backupPath: String by lazy {
        appCtx.filesDir.getFile("backup").createFolderIfNotExist().absolutePath
    }
    val zipFilePath = "${appCtx.externalFiles.absolutePath}${File.separator}tmp_backup.zip"

    private const val TAG = "Backup"

    const val fontsDirName = "fonts"
    const val localBooksDirName = "localBooks"
    const val fontMapFileName = "fonts.json"
    const val localBooksMapFileName = "localBooks.json"

    private val backupFileNames by lazy {
        arrayOf(
            "bookshelf.json",
            "bookmark.json",
            "bookGroup.json",
            "bookSource.json",
            "rssSources.json",
            "rssStar.json",
            "replaceRule.json",
            HighlightRuleRepository.backupFileName,
            "readRecord.json",
            "readRecordDetail.json",
            "readRecordSession.json",
            "searchHistory.json",
            "sourceSub.json",
            "txtTocRule.json",
            "httpTTS.json",
            "keyboardAssists.json",
            "dictRule.json",
            "servers.json",
            DirectLinkUpload.ruleFileName,
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            ThemeConfigStore.configFileName,
            BookCover.configFileName,
            "config.xml"
        )
    }

    private fun getNowZipFileName(): String {
        val backupDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val deviceName = AppConfig.webDavDeviceName
        return if (deviceName?.isNotBlank() == true) {
            "backup${backupDate}-${deviceName}.zip"
        } else {
            "backup${backupDate}.zip"
        }.normalizeFileName()
    }

    private fun shouldBackup(): Boolean {
        val lastBackup = LocalConfig.lastBackup
        return lastBackup + TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis()
    }

    fun autoBack(context: Context) {
        if (shouldBackup()) {
            Coroutine.async {
                BackupRestoreLock.withLock {
                    if (shouldBackup()) {
                        val backupZipFileName = getNowZipFileName()
                        if (!AppWebDav.hasBackUp(backupZipFileName)) {
                            backup(context, AppConfig.backupPath)
                        } else {
                            LocalConfig.lastBackup = System.currentTimeMillis()
                        }
                    }
                }
            }.onError {
                AppLog.put("自动备份失败\n${it.localizedMessage}")
            }
        }
    }

    suspend fun backupLocked(context: Context, path: String?, mode: String = "both") {
        BackupRestoreLock.withLock {
            withContext(IO) {
                backup(context, path, mode)
            }
        }
    }

    private suspend fun backup(context: Context, path: String?, mode: String = "both") {
        LogUtils.d(TAG, "开始备份 path:$path")
        LocalConfig.lastBackup = System.currentTimeMillis()
        val aes = BackupAES()
        FileUtils.delete(backupPath)
        //打包自定义字体与本地书籍(在写入书架前执行,本地书籍查找会同步更新书架中的路径)
        val extraBackupPaths = arrayListOf<String>()
        if (AppConfig.backupFonts) {
            backupFontsTo(File(backupPath))?.let { extraBackupPaths.add(it) }
        }
        if (AppConfig.backupLocalBooks) {
            backupLocalBooksTo(File(backupPath))?.let { extraBackupPaths.add(it) }
        }
        writeListToJson(appDb.bookDao.all, "bookshelf.json", backupPath)
        writeListToJson(appDb.bookmarkDao.all, "bookmark.json", backupPath)
        writeListToJson(appDb.bookGroupDao.all, "bookGroup.json", backupPath)
        writeListToJson(appDb.bookSourceDao.all, "bookSource.json", backupPath)
        writeListToJson(appDb.rssSourceDao.all, "rssSources.json", backupPath)
        writeListToJson(appDb.rssStarDao.all, "rssStar.json", backupPath)
        writeListToJson(appDb.replaceRuleDao.all, "replaceRule.json", backupPath)
        val highlightRuleBackup = HighlightRuleRepository.BackupData(
            rules = appDb.highlightRuleDao.getAll(),
            dialogEnabled = appCtx.defaultSharedPreferences.getBoolean(
                PreferKey.highlightRuleDialog,
                true
            ),
            bookTitleEnabled = appCtx.defaultSharedPreferences.getBoolean(
                PreferKey.highlightRuleBookTitle,
                true
            ),
            bracketNoteEnabled = appCtx.defaultSharedPreferences.getBoolean(
                PreferKey.highlightRuleBracketNote,
                true
            )
        )
        FileUtils.createFileIfNotExist(
            backupPath + File.separator + HighlightRuleRepository.backupFileName
        ).writeText(GSON.toJson(highlightRuleBackup))
        writeListToJson(appDb.readRecordDao.all, "readRecord.json", backupPath)
        writeListToJson(appDb.readRecordDao.allDetail, "readRecordDetail.json", backupPath)
        writeListToJson(appDb.readRecordDao.allSession, "readRecordSession.json", backupPath)
        writeListToJson(appDb.searchKeywordDao.all, "searchHistory.json", backupPath)
        writeListToJson(appDb.ruleSubDao.all, "sourceSub.json", backupPath)
        writeListToJson(appDb.txtTocRuleDao.all, "txtTocRule.json", backupPath)
        writeListToJson(appDb.httpTTSDao.all, "httpTTS.json", backupPath)
        writeListToJson(appDb.keyboardAssistsDao.all, "keyboardAssists.json", backupPath)
        writeListToJson(appDb.dictRuleDao.all, "dictRule.json", backupPath)
        GSON.toJson(appDb.serverDao.all).let { json ->
            aes.runCatching {
                encryptBase64(json)
            }.getOrDefault(json).let {
                FileUtils.createFileIfNotExist(backupPath + File.separator + "servers.json")
                    .writeText(it)
            }
        }
        currentCoroutineContext().ensureActive()
        GSON.toJson(ReadBookConfig.configList).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ReadBookConfig.configFileName)
                .writeText(it)
        }
        GSON.toJson(ReadBookConfig.shareConfig).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ReadBookConfig.shareConfigFileName)
                .writeText(it)
        }
        GSON.toJson(ThemeConfigStore.configList).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ThemeConfigStore.configFileName)
                .writeText(it)
        }
        DirectLinkUpload.getConfig()?.let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + DirectLinkUpload.ruleFileName)
                .writeText(GSON.toJson(it))
        }
        BookCover.getConfig()?.let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + BookCover.configFileName)
                .writeText(GSON.toJson(it))
        }
        currentCoroutineContext().ensureActive()
        val configMap = appCtx.dataStore.data.first()
            .asMap()
            .mapKeys { it.key.name }
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
        xmlBuilder.append("<map>\n")
        configMap.forEach { (key, value) ->
            if (BackupConfig.keyIsNotIgnore(key, true)) {
                val finalValue = if (key == PreferKey.webDavPassword) {
                    aes.runCatching { encryptBase64(value.toString()) }.getOrDefault(value.toString())
                } else value

                when (finalValue) {
                    is String -> xmlBuilder.append("    <string name=\"$key\">${finalValue.replace("&", "&amp;").replace("<", "&lt;")}</string>\n")
                    is Int -> xmlBuilder.append("    <int name=\"$key\" value=\"$finalValue\" />\n")
                    is Long -> xmlBuilder.append("    <long name=\"$key\" value=\"$finalValue\" />\n")
                    is Float -> xmlBuilder.append("    <float name=\"$key\" value=\"$finalValue\" />\n")
                    is Boolean -> xmlBuilder.append("    <boolean name=\"$key\" value=\"$finalValue\" />\n")
                }
            }
        }
        xmlBuilder.append("</map>")
        FileUtils.createFileIfNotExist(backupPath + File.separator + "config.xml")
            .writeText(xmlBuilder.toString())

        currentCoroutineContext().ensureActive()
        val zipFileName = getNowZipFileName()
        val paths = arrayListOf(*backupFileNames)
        for (i in 0 until paths.size) {
            paths[i] = backupPath + File.separator + paths[i]
        }
        paths.addAll(extraBackupPaths)
        FileUtils.delete(zipFilePath)
        FileUtils.delete(zipFilePath.replace("tmp_", ""))
        val backupFileName = if (AppConfig.onlyLatestBackup) {
            "backup.zip"
        } else {
            zipFileName
        }
        if (ZipUtils.zipFiles(paths, zipFilePath)) {
            if (mode == "both" || mode == "local") {
                when {
                    path.isNullOrBlank() -> {
                        copyBackup(context.getExternalFilesDir(null)!!, backupFileName)
                    }

                    path.isContentScheme() -> {
                        copyBackup(context, path.toUri(), backupFileName)
                    }

                    else -> {
                        copyBackup(File(path), backupFileName)
                    }
                }
            }
            if (mode == "both" || mode == "webdav") {
                try {
                    AppWebDav.backUpWebDav(zipFileName)
                } catch (e: Exception) {
                    AppLog.put("上传备份至webdav失败\n$e", e)
                }
            }
        }
        FileUtils.delete(backupPath)
        FileUtils.delete(zipFilePath)
        currentCoroutineContext().ensureActive()
        ReadBookConfig.getAllPicBgStr().map {
            if (it.contains(File.separator)) {
                File(it)
            } else {
                appCtx.externalFiles.getFile("bg", it)
            }
        }.let {
            AppWebDav.upBgs(it.toTypedArray())
        }
    }

    private suspend fun writeListToJson(list: List<Any>, fileName: String, path: String) {
        currentCoroutineContext().ensureActive()
        withContext(IO) {
            if (list.isNotEmpty()) {
                LogUtils.d(TAG, "阅读备份 $fileName 列表大小 ${list.size}")
                val file = FileUtils.createFileIfNotExist(path + File.separator + fileName)
                file.outputStream().buffered().use {
                    GSON.writeToOutputStream(it, list)
                }
                LogUtils.d(TAG, "阅读备份 $fileName 写入大小 ${file.length()}")
            } else {
                LogUtils.d(TAG, "阅读备份 $fileName 列表为空")
            }
        }
    }

    /**
     * 备份自定义字体, 返回打包目录路径
     */
    private fun backupFontsTo(backupDir: File): String? = kotlin.runCatching {
        val fontPathSet = hashSetOf<String>()
        ReadBookConfig.configList.forEach { config ->
            config.highlightRules.forEach { rule ->
                rule.fontPath?.takeIf { it.isNotBlank() }?.let(fontPathSet::add)
            }
            listOf(config.textFont, config.titleFont, config.headerFont, config.footerFont)
                .filter { it.isNotBlank() }
                .forEach(fontPathSet::add)
        }
        listOf(
            ReadBookConfig.shareConfig.textFont,
            ReadBookConfig.shareConfig.titleFont,
            ReadBookConfig.shareConfig.headerFont,
            ReadBookConfig.shareConfig.footerFont
        ).filter { it.isNotBlank() }.forEach(fontPathSet::add)
        ReadBookConfig.shareConfig.highlightRules.forEach { rule ->
            rule.fontPath?.takeIf { it.isNotBlank() }?.let(fontPathSet::add)
        }
        appDb.highlightRuleDao.getAll().forEach { rule ->
            rule.fontPath?.takeIf { it.isNotBlank() }?.let(fontPathSet::add)
        }
        ThemeConfig.appFontPath?.takeIf { it.isNotBlank() }?.let(fontPathSet::add)

        val fontsDir = backupDir.getFile(fontsDirName)
        //原始字体路径到备份文件名的映射, 恢复时用于重写字体路径
        val fontPathMap = hashMapOf<String, String>()
        var count = 0
        var failed = 0
        fontPathSet.forEach { fontPath ->
            val sourceUri = fontPath.toUri()
            val fileName = kotlin.runCatching {
                FileDoc.fromUri(sourceUri, false).name
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@forEach
            val target = fontsDir.getFile(uniqueBackupFileName(fileName, fontPath))
            val copied = kotlin.runCatching {
                sourceUri.inputStream(appCtx).getOrThrow().use { input ->
                    fontsDir.mkdirs()
                    FileOutputStream(target, false).use { output ->
                        input.copyTo(output)
                    }
                }
            }.onFailure {
                failed++
                AppLog.put("备份字体 $fileName 出错\n${it.localizedMessage}", it)
            }.isSuccess
            if (copied) {
                count++
                fontPathMap[fontPath] = target.name
            }
        }
        if (fontPathMap.isEmpty()) return@runCatching null
        fontsDir.mkdirs()
        fontsDir.getFile(fontMapFileName).writeText(GSON.toJson(fontPathMap))
        if (failed > 0) {
            AppLog.put("备份字体完成，成功 $count 个，跳过 $failed 个")
        }
        LogUtils.d(TAG, "备份字体 $count 个，跳过 $failed 个")
        fontsDir.absolutePath
    }.getOrElse {
        AppLog.put("备份字体出错\n${it.localizedMessage}", it)
        null
    }

    /**
     * 备份本地书籍文件, 返回打包目录路径
     */
    private fun backupLocalBooksTo(backupDir: File): String? = kotlin.runCatching {
        val books = appDb.bookDao.all.filter { it.isLocal }
        if (books.isEmpty()) return@runCatching null
        val localBooksDir = backupDir.getFile(localBooksDirName)
        val bookUrlMap = linkedMapOf<String, String>()
        var count = 0
        var failed = 0
        books.forEach { book ->
            val originalBookUrl = book.bookUrl
            val originalName = book.originName.ifBlank { book.name }
            val targetName = uniqueBackupFileName(originalName, originalBookUrl)
            val copied = kotlin.runCatching {
                //读取实际书籍内容。对于从压缩包导入的书籍，这里备份解压后的书籍文件，
                //避免依赖源设备上的压缩包路径，恢复后可直接重绑定。
                LocalBook.getBookInputStream(book).use { input ->
                    localBooksDir.mkdirs()
                    FileOutputStream(localBooksDir.getFile(targetName), false).use { output ->
                        input.copyTo(output)
                    }
                    count++
                }
            }.onFailure {
                failed++
                AppLog.put("备份本地书籍 ${book.name} 出错\n${it.localizedMessage}", it)
            }.isSuccess
            if (copied) {
                // getBookInputStream 可能触发旧设备路径重绑，使用重绑后的 URL 写入映射。
                bookUrlMap[book.bookUrl] = targetName
            }
        }
        if (bookUrlMap.isEmpty()) return@runCatching null
        localBooksDir.getFile(localBooksMapFileName).writeText(GSON.toJson(bookUrlMap))
        if (failed > 0) {
            AppLog.put("备份本地书籍完成，成功 $count 本，跳过 $failed 本")
        }
        LogUtils.d(TAG, "备份本地书籍 $count 本，跳过 $failed 本")
        localBooksDir.absolutePath
    }.getOrElse {
        AppLog.put("备份本地书籍出错\n${it.localizedMessage}", it)
        null
    }

    private fun uniqueBackupFileName(originalName: String, key: String): String {
        val source = File(originalName)
        val baseName = source.nameWithoutExtension.ifBlank { "resource" }
        val extension = source.extension.takeIf { it.isNotBlank() }?.let { ".${it}" }.orEmpty()
        return "${baseName}_${MD5Utils.md5Encode16(key)}$extension".normalizeFileName()
    }

    @Throws(Exception::class)
    @Suppress("SameParameterValue")
    private fun copyBackup(context: Context, uri: Uri, fileName: String) {
        val treeDoc = DocumentFile.fromTreeUri(context, uri)!!
        treeDoc.findFile(fileName)?.delete()
        val fileDoc = treeDoc.createFile("", fileName)
            ?: throw NoStackTraceException("创建文件失败")
        val outputS = fileDoc.openOutputStream()
            ?: throw NoStackTraceException("打开OutputStream失败")
        outputS.use {
            FileInputStream(zipFilePath).use { inputS ->
                inputS.copyTo(outputS)
            }
        }
    }

    @Throws(Exception::class)
    @Suppress("SameParameterValue")
    private fun copyBackup(rootFile: File, fileName: String) {
        FileInputStream(File(zipFilePath)).use { inputS ->
            val file = FileUtils.createFileIfNotExist(rootFile, fileName)
            FileOutputStream(file).use { outputS ->
                inputS.copyTo(outputS)
            }
        }
    }

    fun clearCache() {
        FileUtils.delete(backupPath)
        FileUtils.delete(zipFilePath)
    }
}
