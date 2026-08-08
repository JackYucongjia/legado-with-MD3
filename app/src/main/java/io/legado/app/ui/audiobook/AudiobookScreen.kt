package io.legado.app.ui.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SettingItemWithDivider
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AudiobookRouteScreen(
    onBackClick: () -> Unit,
    viewModel: AudiobookViewModel = koinViewModel()
) {
    AudiobookScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookScreen(
    state: AudiobookUiState,
    effects: Flow<AudiobookEffect>,
    onIntent: (AudiobookIntent) -> Unit,
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AudiobookEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.audiobookshelf_title),
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
                actions = {
                    if (state.isConnected) {
                        TopBarActionButton(
                            onClick = { onIntent(AudiobookIntent.Refresh) },
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.audiobookshelf_refresh)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 48.dp
                )
            ) {
                item {
                    if (state.isConnected) {
                        ConnectedContent(state = state, onIntent = onIntent)
                    } else {
                        ConnectionForm(state = state, onIntent = onIntent)
                    }
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    AppAlertDialog(
        show = state.dialog == AudiobookDialog.ConfirmForget,
        onDismissRequest = { onIntent(AudiobookIntent.DismissDialog) },
        title = stringResource(R.string.audiobookshelf_forget_title),
        text = stringResource(R.string.audiobookshelf_forget_message),
        confirmText = stringResource(R.string.audiobookshelf_forget_confirm),
        onConfirm = { onIntent(AudiobookIntent.ConfirmForget) },
        dismissText = stringResource(R.string.audiobookshelf_cancel),
        onDismiss = { onIntent(AudiobookIntent.DismissDialog) }
    )
}

@Composable
private fun ConnectionForm(
    state: AudiobookUiState,
    onIntent: (AudiobookIntent) -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    SplicedColumnGroup(title = stringResource(R.string.audiobookshelf_server_section)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppTextField(
                value = state.serverName,
                onValueChange = { onIntent(AudiobookIntent.ServerNameChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = stringResource(R.string.audiobookshelf_server_name),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            AppTextField(
                value = state.baseUrl,
                onValueChange = { onIntent(AudiobookIntent.BaseUrlChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = stringResource(R.string.audiobookshelf_server_url),
                placeholder = { AppText(stringResource(R.string.audiobookshelf_server_url_example)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                )
            )
            if (state.baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                AppText(
                    text = stringResource(R.string.audiobookshelf_http_warning),
                    style = LegadoTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            AppTextField(
                value = state.username,
                onValueChange = { onIntent(AudiobookIntent.UsernameChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = stringResource(R.string.audiobookshelf_username),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            AppTextField(
                value = state.password,
                onValueChange = { onIntent(AudiobookIntent.PasswordChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                label = stringResource(R.string.audiobookshelf_password),
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = null
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )
            PrimaryButton(
                onClick = { onIntent(AudiobookIntent.Connect) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canConnect && !state.isLoading,
                text = stringResource(
                    if (state.profileId == null) {
                        R.string.audiobookshelf_connect
                    } else {
                        R.string.audiobookshelf_reconnect
                    }
                )
            )
            if (state.profileId != null) {
                SecondaryButton(
                    onClick = { onIntent(AudiobookIntent.RequestForget) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading,
                    text = stringResource(R.string.audiobookshelf_forget_confirm)
                )
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    state: AudiobookUiState,
    onIntent: (AudiobookIntent) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SplicedColumnGroup(title = stringResource(R.string.audiobookshelf_server_section)) {
            SettingItemWithDivider {
                SettingItem(
                    title = state.serverName,
                    description = state.baseUrl,
                    option = stringResource(
                        R.string.audiobookshelf_server_version,
                        state.serverVersion.ifBlank { "-" }
                    ),
                    imageVector = Icons.Default.Headphones
                )
            }
        }

        SplicedColumnGroup(title = stringResource(R.string.audiobookshelf_actions_section)) {
            ClickableSettingItem(
                title = stringResource(R.string.audiobookshelf_refresh),
                imageVector = Icons.Default.Refresh,
                onClick = { onIntent(AudiobookIntent.Refresh) }
            )
            ClickableSettingItem(
                title = stringResource(R.string.audiobookshelf_edit_connection),
                imageVector = Icons.Default.Edit,
                onClick = { onIntent(AudiobookIntent.EditConnection) }
            )
            ClickableSettingItem(
                title = stringResource(R.string.audiobookshelf_forget_confirm),
                imageVector = Icons.Default.DeleteOutline,
                onClick = { onIntent(AudiobookIntent.RequestForget) }
            )
        }

        SplicedColumnGroup(title = stringResource(R.string.audiobookshelf_library_section)) {
            if (state.libraries.isEmpty()) {
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(R.string.audiobookshelf_no_libraries),
                        description = stringResource(R.string.audiobookshelf_no_libraries_desc),
                        imageVector = Icons.Default.LibraryMusic
                    )
                }
            } else {
                state.libraries.forEach { library ->
                    SettingItemWithDivider {
                        SettingItem(
                            title = library.name,
                            description = when (library.mediaType) {
                                "book" -> stringResource(R.string.audiobookshelf_library_books)
                                "podcast" -> stringResource(R.string.audiobookshelf_library_podcasts)
                                else -> library.mediaType
                            },
                            imageVector = Icons.Default.LibraryMusic
                        )
                    }
                }
            }
        }
    }
}
