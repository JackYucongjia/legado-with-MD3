package io.legado.app.data.audiobook

import io.legado.audiobookshelf.client.AudiobookshelfApi
import io.legado.audiobookshelf.client.AudiobookshelfApiException
import io.legado.app.data.dao.ServerDao
import io.legado.app.data.entities.Server
import io.legado.app.domain.gateway.AudiobookAuthenticationRequiredException
import io.legado.app.domain.gateway.AudiobookGateway
import io.legado.app.domain.model.AudiobookHomeSnapshot
import io.legado.app.domain.model.AudiobookLibrarySummary
import io.legado.app.domain.model.AudiobookServerProfile
import io.legado.app.utils.GSON
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AudiobookRepository(
    private val serverDao: ServerDao,
    private val api: AudiobookshelfApi,
    private val tokenStore: AudiobookTokenStore
) : AudiobookGateway {

    private val accessTokens = ConcurrentHashMap<Long, String>()
    private val refreshMutex = Mutex()

    override suspend fun getSavedProfile(): AudiobookServerProfile? = withContext(Dispatchers.IO) {
        serverDao.all.firstNotNullOfOrNull { server -> server.toAudiobookProfileOrNull() }
    }

    override suspend fun connect(
        existingProfileId: Long?,
        name: String,
        baseUrl: String,
        username: String,
        password: String
    ): AudiobookHomeSnapshot = withContext(Dispatchers.IO) {
        require(username.isNotBlank()) { "请输入用户名" }
        require(password.isNotBlank()) { "请输入密码" }

        val normalizedBaseUrl = AudiobookshelfApi.normalizeBaseUrl(baseUrl)
        val status = api.getStatus(normalizedBaseUrl)
        require(status.initialized) { "Audiobookshelf 服务尚未完成初始化" }

        val auth = api.login(
            baseUrl = normalizedBaseUrl,
            username = username.trim(),
            password = password
        )
        val libraries = api.getLibraries(normalizedBaseUrl, auth.accessToken)
        val existingServer = existingProfileId
            ?.let(serverDao::get)
            ?.takeIf { it.type == Server.TYPE.AUDIOBOOKSHELF }
        val profileId = existingServer?.id ?: System.currentTimeMillis()
        val profileName = name.trim().ifEmpty { "Audiobookshelf" }
        val savedUsername = auth.username.ifBlank { username.trim() }
        val server = Server(
            id = profileId,
            name = profileName,
            type = Server.TYPE.AUDIOBOOKSHELF,
            config = GSON.toJson(
                Server.AudiobookshelfConfig(
                    url = normalizedBaseUrl,
                    username = savedUsername
                )
            ),
            sortNumber = existingServer?.sortNumber ?: nextSortNumber()
        )

        tokenStore.saveRefreshToken(profileId, auth.refreshToken)
        serverDao.insert(server)
        accessTokens[profileId] = auth.accessToken

        buildSnapshot(server.toAudiobookProfile(), status.version, libraries)
    }

    override suspend fun restore(profileId: Long): AudiobookHomeSnapshot {
        return refresh(profileId)
    }

    override suspend fun refresh(profileId: Long): AudiobookHomeSnapshot = withContext(Dispatchers.IO) {
        val profile = requireProfile(profileId)
        val status = api.getStatus(profile.baseUrl)
        var accessToken = accessTokens[profileId]
            ?: refreshAccessToken(profileId, profile.baseUrl)

        val libraries = try {
            api.getLibraries(profile.baseUrl, accessToken)
        } catch (error: AudiobookshelfApiException) {
            if (error.statusCode != 401) throw error
            accessToken = refreshAccessToken(
                profileId = profileId,
                baseUrl = profile.baseUrl,
                rejectedAccessToken = accessToken
            )
            api.getLibraries(profile.baseUrl, accessToken)
        }

        buildSnapshot(profile, status.version, libraries)
    }

    override suspend fun forget(profileId: Long) = withContext(Dispatchers.IO) {
        serverDao.get(profileId)
            ?.takeIf { it.type == Server.TYPE.AUDIOBOOKSHELF }
            ?.let(serverDao::delete)
        accessTokens.remove(profileId)
        tokenStore.delete(profileId)
    }

    private suspend fun refreshAccessToken(
        profileId: Long,
        baseUrl: String,
        rejectedAccessToken: String? = null
    ): String = refreshMutex.withLock {
        val current = accessTokens[profileId]
        if (current != null && current != rejectedAccessToken) return@withLock current

        val refreshToken = tokenStore.readRefreshToken(profileId)
            ?: throw AudiobookAuthenticationRequiredException()
        try {
            val auth = api.refresh(baseUrl, refreshToken)
            tokenStore.saveRefreshToken(profileId, auth.refreshToken)
            accessTokens[profileId] = auth.accessToken
            auth.accessToken
        } catch (error: AudiobookshelfApiException) {
            if (error.statusCode == 401) {
                tokenStore.delete(profileId)
                accessTokens.remove(profileId)
                throw AudiobookAuthenticationRequiredException()
            }
            throw error
        }
    }

    private fun requireProfile(profileId: Long): AudiobookServerProfile {
        return serverDao.get(profileId)?.toAudiobookProfileOrNull()
            ?: throw IllegalArgumentException("未找到有声书服务器配置")
    }

    private fun nextSortNumber(): Int {
        return (serverDao.all.maxOfOrNull(Server::sortNumber) ?: -1) + 1
    }

    private fun Server.toAudiobookProfileOrNull(): AudiobookServerProfile? {
        if (type != Server.TYPE.AUDIOBOOKSHELF) return null
        return getAudiobookshelfConfig()?.let { config ->
            AudiobookServerProfile(
                id = id,
                name = name,
                baseUrl = config.url,
                username = config.username
            )
        }
    }

    private fun Server.toAudiobookProfile(): AudiobookServerProfile {
        return requireNotNull(toAudiobookProfileOrNull())
    }

    private fun buildSnapshot(
        profile: AudiobookServerProfile,
        serverVersion: String,
        libraries: List<io.legado.audiobookshelf.client.AudiobookshelfLibrary>
    ): AudiobookHomeSnapshot {
        return AudiobookHomeSnapshot(
            profile = profile,
            serverVersion = serverVersion,
            libraries = libraries.map { library ->
                AudiobookLibrarySummary(
                    id = library.id,
                    name = library.name,
                    mediaType = library.mediaType,
                    icon = library.icon
                )
            }
        )
    }
}
