package io.legado.audiobookshelf.client

data class AudiobookshelfServerStatus(
    val version: String,
    val initialized: Boolean,
    val authMethods: List<String>
)

data class AudiobookshelfAuthSession(
    val username: String,
    val accessToken: String,
    val refreshToken: String
)

data class AudiobookshelfLibrary(
    val id: String,
    val name: String,
    val mediaType: String,
    val icon: String?
)
