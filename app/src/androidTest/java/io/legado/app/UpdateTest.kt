package io.legado.app

import com.google.gson.Gson
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.update.GiteaRelease
import io.legado.app.utils.fromJsonArray
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {

    private val lastReleaseUrl =
        "https://gitea.yamby.cn/api/v1/repos/yusheng/QieKan-3.0/releases"

    private val lastBetaReleaseUrl =
        "https://gitea.yamby.cn/api/v1/repos/yusheng/QieKan-3.0/releases"

    @Test
    fun updateApp_beta() {
        val body = okHttpClient.newCall(Request.Builder().url(lastBetaReleaseUrl).build()).execute()
            .body!!.string()

        val releaseList = Gson().fromJsonArray<GiteaRelease>(body)
            .getOrElse {
                throw NoStackTraceException("获取新版本出错 " + it.localizedMessage)
            }
            .filter { it.isPreRelease }
            .flatMap { it.toAppReleaseInfo() }
            .sortedByDescending { it.createdAt }

        assertTrue(releaseList.isNotEmpty())
        assertTrue(releaseList.all { it.downloadUrl.isNotBlank() })
        assertTrue(releaseList.all { it.versionName.isNotBlank() })
    }

    @Test
    fun updateApp() {
        val body = okHttpClient.newCall(Request.Builder().url(lastReleaseUrl).build()).execute()
            .body!!.string()

        val releaseList = Gson().fromJsonArray<GiteaRelease>(body)
            .getOrElse {
                throw NoStackTraceException("获取新版本出错 " + it.localizedMessage)
            }
            .filter { !it.isPreRelease }
            .flatMap { it.toAppReleaseInfo() }
            .sortedByDescending { it.createdAt }

        assertTrue(releaseList.isNotEmpty())
        assertTrue(releaseList.all { it.downloadUrl.isNotBlank() })
        assertTrue(releaseList.all { it.versionName.isNotBlank() })
    }

}
