package io.legado.app.domain.model

data class AudiobookServerProfile(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val username: String
)

data class AudiobookLibrarySummary(
    val id: String,
    val name: String,
    val mediaType: String,
    val icon: String?
)

data class AudiobookHomeSnapshot(
    val profile: AudiobookServerProfile,
    val serverVersion: String,
    val libraries: List<AudiobookLibrarySummary>
)
