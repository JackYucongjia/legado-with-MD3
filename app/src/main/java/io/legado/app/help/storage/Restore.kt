package io.legado.app.help.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppConst.androidId
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.upType
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.BookCover
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.ACache
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getFile
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.openInputStream
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import splitties.init.appCtx
import java.io.File
import java.io.FileInputStream

/**
 * 恢复
 */
object Restore : KoinComponent {

    private val settingsRepository: SettingsRepository by inject()
    private const val TAG = "Restore"

    suspend fun restore(context: Context, uri: Uri) {
        BackupRestoreLock.withLock {
            LogUtils.d(TAG, "开始恢复备份 uri:$uri")
            val unzipResult = kotlin.runCatching {
                FileUtils.delete(Backup.backupPath)
                if (uri.isContentScheme()) {
                    DocumentFile.fromSingleUri(context, uri)!!.openInputStream()!!.use {
                        ZipUtils.unZipToPath(it, Backup.backupPath)
                    }
                } else {
                    ZipUtils.unZipToPath(File(uri.path!!), Backup.backupPath)
                }
            }.onFailure {
                AppLog.put("复制解压文件出错\n${it.localizedMessage}", it)
            }
            if (unzipResult.isSuccess) {
                kotlin.runCatching {
                    restoreUnzipped(Backup.backupPath)
                    LocalConfig.lastBackup = System.currentTimeMillis()
                }.onFailure {
                    appCtx.toastOnUi("恢复备份出错\n${it.localizedMessage}")
                    AppLog.put("恢复备份出错\n${it.localizedMessage}", it)
                }
            }
        }
    }

    suspend fun restoreLocked(path: String) {
        BackupRestoreLock.withLock {
            restoreUnzipped(path)
        }
    }

    internal suspend fun restoreUnzipped(path: String) {
        restore(path)
    }

    private suspend fun restore(path: String) {
        val aes = BackupAES()
        fileToListT<Book>(path, "bookshelf.json")?.let {
            it.forEach { book ->
                book.upType()
            }
            it.filter { book -> book.isLocal }
                .forEach { book ->
                    book.coverUrl = LocalBook.getCoverPath(book)
                }
            val newBooks = arrayListOf<Book>()
            val ignoreLocalBook = BackupConfig.ignoreLocalBook
            it.forEach { book ->
                if (ignoreLocalBook && book.isLocal) {
                    return@forEach
                }
                if (appDb.bookDao.has(book.bookUrl)) {
                    try {
                        appDb.bookDao.update(book)
                    } catch (_: SQLiteConstraintException) {
                        appDb.bookDao.insert(book)
                    }
                } else {
                    newBooks.add(book)
                }
            }
            appDb.bookDao.insert(*newBooks.toTypedArray())
        }
        fileToListT<Bookmark>(path, "bookmark.json")?.let {
            try {
                appDb.bookmarkDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<BookGroup>(path, "bookGroup.json")?.let {
            try {
                appDb.bookGroupDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<BookSource>(path, "bookSource.json")?.let {
            try {
                appDb.bookSourceDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        } ?: run {
            val bookSourceFile = File(path, "bookSource.json")
            if (bookSourceFile.exists()) {
                val json = bookSourceFile.readText()
                ImportOldData.importOldSource(json)
            }
        }
        fileToListT<RssSource>(path, "rssSources.json")?.let {
            try {
                appDb.rssSourceDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<RssStar>(path, "rssStar.json")?.let {
            try {
                appDb.rssStarDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
            try {
                appDb.replaceRuleDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
            try {
                appDb.searchKeywordDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<RuleSub>(path, "sourceSub.json")?.let {
            try {
                appDb.ruleSubDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
            try {
                appDb.txtTocRuleDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
            try {
                appDb.httpTTSDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<DictRule>(path, "dictRule.json")?.let {
            try {
                appDb.dictRuleDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
            try {
                appDb.keyboardAssistsDao.insert(*it.toTypedArray())
            } catch (_: SQLiteConstraintException) {
            }
        }
        fileToListT<ReadRecord>(path, "readRecord.json")?.let {
            it.forEach { readRecord ->
                val restoredRecord = readRecord.copy(
                    bookUrl = resolveBookUrl(
                        readRecord.bookUrl,
                        readRecord.bookName,
                        readRecord.bookAuthor,
                    )
                )
                if (restoredRecord.deviceId != androidId) {
                    try {
                        appDb.readRecordDao.insert(restoredRecord)
                    } catch (_: SQLiteConstraintException) {
                    }
                } else {
                    val time = appDb.readRecordDao
                        .getReadTime(
                            restoredRecord.deviceId,
                            restoredRecord.bookName,
                            restoredRecord.bookAuthor,
                        )
                    if (time == null || time < restoredRecord.readTime) {
                        try {
                            appDb.readRecordDao.insert(restoredRecord)
                        } catch (_: SQLiteConstraintException) {
                        }
                    }
                }
            }
        }
        fileToListT<ReadRecordDetail>(path, "readRecordDetail.json")?.let {
            it.forEach { detail ->
                try {
                    appDb.readRecordDao.insertDetail(
                        detail.copy(
                            bookUrl = resolveBookUrl(
                                detail.bookUrl,
                                detail.bookName,
                                detail.bookAuthor,
                            )
                        )
                    )
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        fileToListT<ReadRecordSession>(path, "readRecordSession.json")?.let {
            it.forEach { session ->
                try {
                    appDb.readRecordDao.insertSession(
                        session.copy(
                            bookUrl = resolveBookUrl(
                                session.bookUrl,
                                session.bookName,
                                session.bookAuthor,
                            )
                        )
                    )
                } catch (_: SQLiteConstraintException) {
                }
            }
        }
        File(path, "servers.json").takeIf {
            it.exists()
        }?.runCatching {
            var json = readText()
            if (!json.isJsonArray()) {
                json = aes.decryptStr(json)
            }
            GSON.fromJsonArray<Server>(json).getOrNull()?.let {
                try {
                    appDb.serverDao.insert(*it.toTypedArray())
                } catch (_: SQLiteConstraintException) {
                }
            }
        }?.onFailure {
            AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it)
        }
        File(path, DirectLinkUpload.ruleFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            ACache.get(cacheDir = false).put(DirectLinkUpload.ruleFileName, json)
        }?.onFailure {
            AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it)
        }
        //恢复主题配置
        if (!BackupConfig.ignoreThemeConfig) {
            File(path, ThemeConfigStore.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ThemeConfigStore.configFilePath)
                copyTo(File(ThemeConfigStore.configFilePath))
                ThemeConfigStore.upConfig()
            }?.onFailure {
                AppLog.put("恢复主题出错\n${it.localizedMessage}", it)
            }
        }
        File(path, BookCover.configFileName).takeIf {
            it.exists() && !BackupConfig.ignoreCoverConfig
        }?.runCatching {
            val json = readText()
            BookCover.saveCoverRule(json)
        }?.onFailure {
            AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it)
        }
        if (!BackupConfig.ignoreReadConfig) {
            //恢复阅读界面配置
            File(path, ReadBookConfig.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.configFilePath)
                copyTo(File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
            File(path, ReadBookConfig.shareConfigFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                copyTo(File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
        }
        // 恢复配置文件 (手动解析 XML，替代反射逻辑)
        val configFile = File(path, "config.xml")
        var restoredConfigMap: Map<String, Any?> = emptyMap()
        if (configFile.exists()) {
            try {
                restoredConfigMap = readXmlToMap(configFile)
                if (restoredConfigMap.isNotEmpty()) {
                    applyConfigMap(restoredConfigMap, aes)
                }
            } catch (e: Exception) {
                AppLog.put("恢复配置 XML 出错\n${e.localizedMessage}", e)
            }
        }

        if (!BackupConfig.ignoreReadConfig) {
            kotlin.runCatching {
                restoreHighlightRules(File(path, HighlightRuleRepository.backupFileName))
            }.onFailure {
                AppLog.put("恢复高亮规则出错\n${it.localizedMessage}", it)
            }
        }
        //恢复自定义字体
        kotlin.runCatching {
            restoreFonts(
                fontsDir = File(path, Backup.fontsDirName),
                restoreReadFonts = !BackupConfig.ignoreReadConfig,
                restoreThemeFont = !BackupConfig.ignoreThemeConfig,
                backedAppFontPath = restoredConfigMap[PreferKey.appFontPath] as? String,
            )
        }.onFailure {
            AppLog.put("恢复自定义字体出错\n${it.localizedMessage}", it)
        }
        //恢复本地书籍文件
        if (!BackupConfig.ignoreLocalBook) {
            kotlin.runCatching {
                restoreLocalBooks(File(path, Backup.localBooksDirName))
            }.onFailure {
                AppLog.put("恢复本地书籍出错\n${it.localizedMessage}", it)
            }
        }

        appCtx.toastOnUi(R.string.restore_success)
        withContext(Main) {
            delay(100)
            if (!BuildConfig.DEBUG) {
                LauncherIconHelp.changeIcon(appCtx.getPrefString(PreferKey.launcherIcon))
            }
            ThemeConfigStore.applyDayNight(appCtx)
        }
    }

    /**
     * 恢复高亮规则及其开关配置。旧备份没有该文件时直接跳过。
     */
    private fun restoreHighlightRules(file: File) {
        if (!file.exists()) return
        val backupData = GSON.fromJsonObject<HighlightRuleRepository.BackupData>(
            file.readText()
        ).getOrNull() ?: return
        appDb.highlightRuleDao.replaceAll(backupData.rules)
        appCtx.defaultSharedPreferences.edit {
            putBoolean(PreferKey.highlightRuleDialog, backupData.dialogEnabled)
            putBoolean(PreferKey.highlightRuleBookTitle, backupData.bookTitleEnabled)
            putBoolean(PreferKey.highlightRuleBracketNote, backupData.bracketNoteEnabled)
        }
    }

    /**
     * 恢复自定义字体文件, 并按映射重写排版配置中的字体路径。
     * 阅读配置和主题配置分别遵守恢复忽略项，避免只恢复资源却修改了被忽略的配置。
     */
    private fun restoreFonts(
        fontsDir: File,
        restoreReadFonts: Boolean,
        restoreThemeFont: Boolean,
        backedAppFontPath: String?,
    ) {
        if (!fontsDir.exists() || (!restoreReadFonts && !restoreThemeFont)) return
        val targetFontDir = appCtx.externalFiles.getFile("font")
        targetFontDir.mkdirs()
        fontsDir.listFiles()?.forEach { fontFile ->
            if (fontFile.isFile && fontFile.name != Backup.fontMapFileName) {
                // 用户已选择恢复字体，备份内容应覆盖目标设备同名旧字体。
                fontFile.copyTo(targetFontDir.getFile(fontFile.name), overwrite = true)
            }
        }
        //原始字体路径到备份文件名的映射
        val fontMap = File(fontsDir, Backup.fontMapFileName)
            .takeIf { it.exists() }
            ?.runCatching {
                GSON.fromJsonObject<Map<String, String>>(readText()).getOrNull()
            }?.getOrNull().orEmpty()

        fun rewriteFont(fontPath: String?): String? {
            if (fontPath.isNullOrBlank()) return fontPath
            val fileName = fontMap[fontPath] ?: File(fontPath).name
            if (fileName.isBlank()) return fontPath
            val newFont = targetFontDir.getFile(fileName)
            return if (newFont.exists()) newFont.absolutePath else fontPath
        }

        var changed = false
        fun rewriteConfig(config: ReadBookConfig.Config) {
            rewriteFont(config.textFont)?.let {
                if (it != config.textFont) { config.textFont = it; changed = true }
            }
            rewriteFont(config.titleFont)?.let {
                if (it != config.titleFont) { config.titleFont = it; changed = true }
            }
            rewriteFont(config.headerFont)?.let {
                if (it != config.headerFont) { config.headerFont = it; changed = true }
            }
            rewriteFont(config.footerFont)?.let {
                if (it != config.footerFont) { config.footerFont = it; changed = true }
            }
            config.highlightRules.forEach { rule ->
                val newFontPath = rewriteFont(rule.fontPath)
                if (newFontPath != rule.fontPath) { rule.fontPath = newFontPath; changed = true }
            }
        }
        if (restoreReadFonts) {
            ReadBookConfig.configList.forEach(::rewriteConfig)
            rewriteConfig(ReadBookConfig.shareConfig)
            // 高亮规则当前单独存储在 Room 中，不只存在于 ReadBookConfig JSON。
            appDb.highlightRuleDao.getAll().forEach { rule ->
                val newFontPath = rewriteFont(rule.fontPath)
                if (newFontPath != rule.fontPath) {
                    appDb.highlightRuleDao.update(rule.copy(fontPath = newFontPath))
                }
            }
        }
        if (restoreThemeFont) {
            // 直接使用备份 XML 中的值，避免 DataStore observer 尚未刷新时读取到旧 appFontPath。
            val sourceAppFontPath = backedAppFontPath ?: ThemeConfig.appFontPath
            val newAppFontPath = rewriteFont(sourceAppFontPath)
            if (newAppFontPath != ThemeConfig.appFontPath) {
                ThemeConfig.appFontPath = newAppFontPath
            }
        }
        if (changed) {
            ReadBookConfig.save()
        }
    }

    /**
     * 恢复本地书籍文件到书籍保存目录，并立即重绑书架及阅读记录中的 bookUrl。
     * 新版备份使用 localBooks.json 映射；没有映射的旧备份仍按原文件名兼容恢复。
     */
    private suspend fun restoreLocalBooks(localBooksDir: File) {
        if (!localBooksDir.exists()) return
        val defaultBookTreeUri = OtherConfig.defaultBookTreeUri
        if (defaultBookTreeUri.isNullOrBlank()) {
            appCtx.toastOnUi(R.string.no_books_dir)
            return
        }

        val bookMap = File(localBooksDir, Backup.localBooksMapFileName)
            .takeIf { it.exists() }
            ?.runCatching {
                GSON.fromJsonObject<Map<String, String>>(readText()).getOrNull()
            }?.getOrNull().orEmpty()
        val entries = if (bookMap.isNotEmpty()) {
            bookMap.entries.map { it.key to it.value }
        } else {
            localBooksDir.listFiles()
                ?.filter { it.isFile && it.name != Backup.localBooksMapFileName }
                ?.map { null to it.name }
                .orEmpty()
        }
        if (entries.isEmpty()) return

        var restored = 0
        entries.forEach { (oldBookUrl, fileName) ->
            val bookFile = File(localBooksDir, fileName)
            if (!bookFile.isFile) return@forEach
            kotlin.runCatching {
                val restoredUri = FileInputStream(bookFile).use { input ->
                    // 使用备份目录中的唯一文件名，避免目标目录中存在同名旧书时串书。
                    LocalBook.saveBookFile(input, fileName)
                }
                val newBookUrl = FileDoc.fromUri(restoredUri, false).toString()
                if (!oldBookUrl.isNullOrBlank() && oldBookUrl != newBookUrl) {
                    rebindRestoredBook(oldBookUrl, newBookUrl)
                }
                restored++
            }.onFailure {
                AppLog.put("恢复本地书籍 ${bookFile.name} 出错\n${it.localizedMessage}", it)
            }
        }
        LogUtils.d(TAG, "恢复本地书籍 $restored 本")
    }

    private fun rebindRestoredBook(oldBookUrl: String, newBookUrl: String) {
        val oldBook = appDb.bookDao.getBook(oldBookUrl) ?: return
        val newBook = oldBook.copy(
            bookUrl = newBookUrl,
            coverUrl = LocalBook.getCoverPath(oldBook.copy(bookUrl = newBookUrl))
        )
        appDb.runInTransaction {
            appDb.bookDao.replace(oldBook, newBook)
            BookHelp.updateCacheFolder(oldBook, newBook)
            appDb.readRecordDao.replaceBookUrl(oldBookUrl, newBookUrl)
        }
    }

    private suspend fun applyConfigMap(map: Map<String, Any?>, aes: BackupAES) {
        val finalMap = mutableMapOf<String, Any>()
        appCtx.defaultSharedPreferences.edit {
            map.forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key)) {
                    when (key) {
                        PreferKey.webDavPassword -> {
                            val password = kotlin.runCatching {
                                aes.decryptStr(value.toString())
                            }.getOrNull() ?: let {
                                if (appCtx.getPrefString(PreferKey.webDavPassword).isNullOrBlank()) {
                                    value.toString()
                                } else null
                            }
                            password?.let {
                                putString(key, it)
                                finalMap[key] = it
                            }
                        }

                        else -> {
                            if (value != null) {
                                when (value) {
                                    is Int -> { putInt(key, value); finalMap[key] = value }
                                    is Boolean -> { putBoolean(key, value); finalMap[key] = value }
                                    is Long -> { putLong(key, value); finalMap[key] = value }
                                    is Double -> { // JSON 数字会被解析为 Double
                                        val floatValue = value.toFloat()
                                        putFloat(key, floatValue)
                                        finalMap[key] = floatValue
                                    }
                                    is Float -> { putFloat(key, value); finalMap[key] = value }
                                    is String -> { putString(key, value); finalMap[key] = value }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 同步恢复到 DataStore
        settingsRepository.batchPutFromMap(finalMap)
    }

    private fun readXmlToMap(file: File): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            FileInputStream(file).use { fis ->
                parser.setInput(fis, "UTF-8")
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val tagName = parser.name
                        val name = parser.getAttributeValue(null, "name")
                        if (name != null) {
                            when (tagName) {
                                "string" -> map[name] = parser.nextText()
                                "int" -> map[name] = parser.getAttributeValue(null, "value").toInt()
                                "long" -> map[name] = parser.getAttributeValue(null, "value").toLong()
                                "float" -> map[name] = parser.getAttributeValue(null, "value").toFloat()
                                "boolean" -> map[name] = parser.getAttributeValue(null, "value").toBoolean()
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private suspend fun resolveBookUrl(
        existingBookUrl: String?,
        bookName: String,
        bookAuthor: String,
    ): String? {
        if (!existingBookUrl.isNullOrBlank()) return existingBookUrl
        return appDb.readRecordDao.findMatchingBookUrls(bookName, bookAuthor).singleOrNull()
    }

    private inline fun <reified T> fileToListT(path: String, fileName: String): List<T>? {
        try {
            val file = File(path, fileName)
            if (file.exists()) {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件大小 ${file.length()}")
                FileInputStream(file).use {
                    return GSON.fromJsonArray<T>(it).getOrThrow().also { list ->
                        LogUtils.d(TAG, "阅读恢复备份 $fileName 列表大小 ${list.size}")
                    }
                }
            } else {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件不存在")
            }
        } catch (e: Exception) {
            AppLog.put("$fileName\n读取解析出错\n${e.localizedMessage}", e)
            appCtx.toastOnUi("$fileName\n读取文件出错\n${e.localizedMessage}")
        }
        return null
    }

}
