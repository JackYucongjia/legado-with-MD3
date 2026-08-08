package io.legado.app.ui.audiobook

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AudiobookUiState(
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val profileId: Long? = null,
    val serverName: String = "Audiobookshelf",
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val serverVersion: String = "",
    val libraries: ImmutableList<AudiobookLibraryItemUi> = persistentListOf(),
    val dialog: AudiobookDialog? = null
) {
    val canConnect: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

@Stable
data class AudiobookLibraryItemUi(
    val id: String,
    val name: String,
    val mediaType: String,
    val icon: String?
)

sealed interface AudiobookIntent {
    data class ServerNameChanged(val value: String) : AudiobookIntent
    data class BaseUrlChanged(val value: String) : AudiobookIntent
    data class UsernameChanged(val value: String) : AudiobookIntent
    data class PasswordChanged(val value: String) : AudiobookIntent
    data object Connect : AudiobookIntent
    data object Refresh : AudiobookIntent
    data object EditConnection : AudiobookIntent
    data object RequestForget : AudiobookIntent
    data object ConfirmForget : AudiobookIntent
    data object DismissDialog : AudiobookIntent
}

sealed interface AudiobookEffect {
    data class ShowMessage(val message: String) : AudiobookEffect
}

sealed interface AudiobookDialog {
    data object ConfirmForget : AudiobookDialog
}
