package io.legado.audiobookshelf.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AudiobookshelfApi(
    private val client: OkHttpClient = OkHttpClient.Builder().build()
) {

    fun getStatus(baseUrl: String): AudiobookshelfServerStatus {
        val request = requestBuilder(baseUrl, "status").get().build()
        val dto = execute<StatusDto>(request)
        if (dto.app != AUDIOBOOKSHELF_APP) {
            throw IllegalArgumentException("目标地址不是 Audiobookshelf 服务")
        }
        return AudiobookshelfServerStatus(
            version = dto.serverVersion,
            initialized = dto.isInit,
            authMethods = dto.authMethods
        )
    }

    fun login(baseUrl: String, username: String, password: String): AudiobookshelfAuthSession {
        val body = json.encodeToString(LoginRequestDto(username, password))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = requestBuilder(baseUrl, "login")
            .header(RETURN_TOKENS_HEADER, "true")
            .post(body)
            .build()
        return execute<AuthResponseDto>(request).user.toSession()
    }

    fun refresh(baseUrl: String, refreshToken: String): AudiobookshelfAuthSession {
        val request = requestBuilder(baseUrl, "auth/refresh")
            .header(REFRESH_TOKEN_HEADER, refreshToken)
            .post(EMPTY_BODY)
            .build()
        return execute<AuthResponseDto>(request).user.toSession()
    }

    fun getLibraries(baseUrl: String, accessToken: String): List<AudiobookshelfLibrary> {
        val request = requestBuilder(baseUrl, "api/libraries")
            .header(AUTHORIZATION, "Bearer $accessToken")
            .get()
            .build()
        return execute<LibrariesResponseDto>(request).libraries.map { library ->
            AudiobookshelfLibrary(
                id = library.id,
                name = library.name,
                mediaType = library.mediaType,
                icon = library.icon
            )
        }
    }

    private fun requestBuilder(baseUrl: String, path: String): Request.Builder {
        return Request.Builder()
            .url("${normalizeBaseUrl(baseUrl)}/${path.trimStart('/')}")
            .header(ACCEPT, "application/json")
    }

    private inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw AudiobookshelfApiException(
                    statusCode = response.code,
                    message = extractErrorMessage(responseBody)
                        ?: "Audiobookshelf 请求失败（HTTP ${response.code}）"
                )
            }
            return runCatching { json.decodeFromString<T>(responseBody) }
                .getOrElse { error ->
                    throw AudiobookshelfApiException(
                        statusCode = response.code,
                        message = "Audiobookshelf 返回了无法识别的数据：${error.message}"
                    )
                }
        }
    }

    private fun extractErrorMessage(body: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val AUDIOBOOKSHELF_APP = "audiobookshelf"
        private const val ACCEPT = "Accept"
        private const val AUTHORIZATION = "Authorization"
        private const val RETURN_TOKENS_HEADER = "x-return-tokens"
        private const val REFRESH_TOKEN_HEADER = "x-refresh-token"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun normalizeBaseUrl(input: String): String {
            val value = input.trim().trimEnd('/')
            val parsed = value.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("请输入完整的 HTTP 或 HTTPS 服务器地址")
            require(parsed.query == null && parsed.fragment == null) {
                "服务器地址不能包含查询参数或片段"
            }
            return parsed.toString().trimEnd('/')
        }
    }
}

@Serializable
private data class StatusDto(
    val app: String,
    val serverVersion: String = "",
    val isInit: Boolean = false,
    val authMethods: List<String> = emptyList()
)

@Serializable
private data class LoginRequestDto(
    val username: String,
    val password: String
)

@Serializable
private data class AuthResponseDto(
    val user: AuthUserDto
)

@Serializable
private data class AuthUserDto(
    val username: String = "",
    val accessToken: String,
    val refreshToken: String
) {
    fun toSession(): AudiobookshelfAuthSession {
        return AudiobookshelfAuthSession(
            username = username,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }
}

@Serializable
private data class LibrariesResponseDto(
    val libraries: List<LibraryDto> = emptyList()
)

@Serializable
private data class LibraryDto(
    val id: String,
    val name: String,
    val mediaType: String,
    @SerialName("icon") val icon: String? = null
)
