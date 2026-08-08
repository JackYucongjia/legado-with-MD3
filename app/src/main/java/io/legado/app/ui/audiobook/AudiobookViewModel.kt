package io.legado.app.ui.audiobook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.AudiobookGateway
import io.legado.app.domain.model.AudiobookHomeSnapshot
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AudiobookViewModel(
    private val audiobookGateway: AudiobookGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AudiobookEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        loadSavedProfile()
    }

    fun onIntent(intent: AudiobookIntent) {
        when (intent) {
            is AudiobookIntent.ServerNameChanged -> updateForm { copy(serverName = intent.value) }
            is AudiobookIntent.BaseUrlChanged -> updateForm { copy(baseUrl = intent.value) }
            is AudiobookIntent.UsernameChanged -> updateForm { copy(username = intent.value) }
            is AudiobookIntent.PasswordChanged -> updateForm { copy(password = intent.value) }
            AudiobookIntent.Connect -> connect()
            AudiobookIntent.Refresh -> refresh()
            AudiobookIntent.EditConnection -> {
                _uiState.update { it.copy(isConnected = false, password = "") }
            }
            AudiobookIntent.RequestForget -> {
                _uiState.update { it.copy(dialog = AudiobookDialog.ConfirmForget) }
            }
            AudiobookIntent.ConfirmForget -> forget()
            AudiobookIntent.DismissDialog -> {
                _uiState.update { it.copy(dialog = null) }
            }
        }
    }

    private fun loadSavedProfile() {
        viewModelScope.launch {
            runCatching { audiobookGateway.getSavedProfile() }
                .onFailure(::showError)
                .getOrNull()
                ?.let { profile ->
                    _uiState.update {
                        it.copy(
                            isLoading = true,
                            profileId = profile.id,
                            serverName = profile.name,
                            baseUrl = profile.baseUrl,
                            username = profile.username,
                            password = ""
                        )
                    }
                    runCatching { audiobookGateway.restore(profile.id) }
                        .onSuccess(::showSnapshot)
                        .onFailure { error ->
                            _uiState.update { it.copy(isLoading = false, isConnected = false) }
                            showError(error)
                        }
                }
        }
    }

    private fun connect() {
        val state = _uiState.value
        if (!state.canConnect || state.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching {
                audiobookGateway.connect(
                    existingProfileId = state.profileId,
                    name = state.serverName,
                    baseUrl = state.baseUrl,
                    username = state.username,
                    password = state.password
                )
            }.onSuccess(::showSnapshot)
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, isConnected = false) }
                    showError(error)
                }
        }
    }

    private fun refresh() {
        val profileId = _uiState.value.profileId ?: return
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching { audiobookGateway.refresh(profileId) }
                .onSuccess(::showSnapshot)
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    showError(error)
                }
        }
    }

    private fun forget() {
        val profileId = _uiState.value.profileId ?: return
        _uiState.update { it.copy(isLoading = true, dialog = null) }
        viewModelScope.launch {
            runCatching { audiobookGateway.forget(profileId) }
                .onSuccess { _uiState.value = AudiobookUiState() }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    showError(error)
                }
        }
    }

    private fun showSnapshot(snapshot: AudiobookHomeSnapshot) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isConnected = true,
                profileId = snapshot.profile.id,
                serverName = snapshot.profile.name,
                baseUrl = snapshot.profile.baseUrl,
                username = snapshot.profile.username,
                password = "",
                serverVersion = snapshot.serverVersion,
                libraries = snapshot.libraries.map { library ->
                    AudiobookLibraryItemUi(
                        id = library.id,
                        name = library.name,
                        mediaType = library.mediaType,
                        icon = library.icon
                    )
                }.toImmutableList(),
                dialog = null
            )
        }
    }

    private fun showError(error: Throwable) {
        _effects.tryEmit(
            AudiobookEffect.ShowMessage(
                error.message?.takeIf { it.isNotBlank() } ?: "有声书服务连接失败"
            )
        )
    }

    private fun updateForm(transform: AudiobookUiState.() -> AudiobookUiState) {
        if (_uiState.value.isLoading) return
        _uiState.update(transform)
    }
}
