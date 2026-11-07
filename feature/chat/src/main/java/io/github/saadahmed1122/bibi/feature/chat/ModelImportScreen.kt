package io.github.saadahmed1122.bibi.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.saadahmed1122.bibi.core.designsystem.R as DsR
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.BibiScreen
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.core.designsystem.ModelDownloadBlock
import io.github.saadahmed1122.bibi.domain.model.ModelDownloadState
import io.github.saadahmed1122.bibi.core.designsystem.PrimaryButton
import io.github.saadahmed1122.bibi.core.designsystem.SecondaryButton
import io.github.saadahmed1122.bibi.core.designsystem.Space
import io.github.saadahmed1122.bibi.domain.model.ModelFile
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.model.RouterModelFile

/**
 * [onPick] opens the system file picker; the caller turns the result into [ModelImportViewModel.import].
 * [router] and [onPickRouter], when given, add the optional action-router section.
 */
@Composable
fun ModelImportRoute(
    viewModel: ModelImportViewModel,
    onPick: () -> Unit,
    onOpenChat: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    router: ModelImportViewModel? = null,
    onPickRouter: () -> Unit = {},
    voice: VoicePackViewModel? = null,
    onPickVoice: () -> Unit = {},
    download: ModelDownloadViewModel? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloadState = download?.state?.collectAsStateWithLifecycle()?.value
    ModelImportScreen(
        state, onPick, viewModel::cancel, viewModel::remove, onOpenChat, onBack, modifier,
        download = downloadState, onDownload = { download?.start() }, onCancelDownload = { download?.cancel() },
    ) {
        if (router != null) {
            val routerState by router.state.collectAsStateWithLifecycle()
            RouterModelSection(routerState, onPickRouter, router::cancel, router::remove)
        }
        if (voice != null) {
            val voiceState by voice.state.collectAsStateWithLifecycle()
            VoicePackSection(voiceState, onPickVoice, voice::cancel, voice::remove)
        }
    }
}

@Composable
fun ModelImportScreen(
    state: ModelState,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onOpenChat: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    download: ModelDownloadState? = null,
    onDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    BibiScreen(title = stringResource(R.string.model_title), modifier = modifier, onBack = onBack) {
        PackCard(stringResource(R.string.model_section_chat)) {
        when (state) {
            ModelState.Missing -> {
                Text(stringResource(R.string.model_missing, ModelFile.NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (download != null) ModelDownloadBlock(download, onDownload, onCancelDownload)
                if (download !is ModelDownloadState.Downloading) SecondaryButton(stringResource(R.string.model_choose), onClick = onPick)
            }
            is ModelState.Importing -> {
                val percent = (state.progress * 100).toInt()
                Text(
                    stringResource(R.string.model_importing, percent),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.model_cancel), onClick = onCancel)
            }
            is ModelState.Ready -> {
                Text(stringResource(R.string.model_ready))
                PrimaryButton(stringResource(R.string.model_open_chat), onClick = onOpenChat)
                SecondaryButton(stringResource(R.string.model_remove), onClick = onRemove)
            }
            is ModelState.Invalid -> {
                val message = when (state.reason) {
                    "size" -> stringResource(R.string.model_invalid_size, ModelFile.NAME)
                    "checksum" -> stringResource(R.string.model_invalid_checksum, ModelFile.NAME)
                    else -> stringResource(R.string.model_invalid_read)
                }
                Text(message, color = MaterialTheme.colorScheme.error)
                if (download != null) ModelDownloadBlock(download, onDownload, onCancelDownload)
                if (download !is ModelDownloadState.Downloading) SecondaryButton(stringResource(R.string.model_choose_other), onClick = onPick)
            }
        }
        }
        extra()
    }
}

/** One model or pack: title, then its state and buttons, on a rounded card. */
@Composable
private fun PackCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

/** The optional action router (SPEC-tools.md): without it only the shortcut phrases trigger actions. */
@Composable
fun RouterModelSection(
    state: ModelState,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PackCard(stringResource(R.string.router_title), modifier) {
        when (state) {
            ModelState.Missing -> {
                Text(stringResource(R.string.router_missing, RouterModelFile.NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(stringResource(R.string.router_choose), onClick = onPick)
            }
            is ModelState.Importing -> {
                Text(
                    stringResource(R.string.model_importing, (state.progress * 100).toInt()),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.model_cancel), onClick = onCancel)
            }
            is ModelState.Ready -> {
                Text(stringResource(R.string.router_ready))
                SecondaryButton(stringResource(R.string.router_remove), onClick = onRemove)
            }
            is ModelState.Invalid -> {
                val message = when (state.reason) {
                    "size" -> stringResource(R.string.model_invalid_size, RouterModelFile.NAME)
                    "checksum" -> stringResource(R.string.model_invalid_checksum, RouterModelFile.NAME)
                    else -> stringResource(R.string.model_invalid_read)
                }
                Text(message, color = MaterialTheme.colorScheme.error)
                SecondaryButton(stringResource(R.string.model_choose_other), onClick = onPick)
            }
        }
    }
}

/** The optional voice pack (SPEC-voice.md): Parakeet and Piper, picked as a folder. */
@Composable
fun VoicePackSection(state: ModelState, onPick: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    PackCard(stringResource(R.string.voice_title), modifier) {
        when (state) {
            ModelState.Missing -> {
                Text(stringResource(R.string.voice_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(stringResource(R.string.voice_choose), onClick = onPick)
            }
            is ModelState.Importing -> {
                Text(stringResource(R.string.model_importing, (state.progress * 100).toInt()), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.model_cancel), onClick = onCancel)
            }
            is ModelState.Ready -> {
                Text(stringResource(R.string.voice_ready))
                SecondaryButton(stringResource(R.string.voice_remove), onClick = onRemove)
            }
            is ModelState.Invalid -> {
                val message = when {
                    state.reason.startsWith("missing:") -> stringResource(R.string.voice_invalid_missing, state.reason.removePrefix("missing:"))
                    state.reason == "checksum" || state.reason == "size" -> stringResource(R.string.voice_invalid_file)
                    else -> stringResource(R.string.model_invalid_read)
                }
                Text(message, color = MaterialTheme.colorScheme.error)
                SecondaryButton(stringResource(R.string.voice_choose), onClick = onPick)
            }
        }
    }
}

@Composable
private fun ModelPreview(state: ModelState) {
    BibiTheme { ModelImportScreen(state, {}, {}, {}, {}, {}) }
}

@Preview
@Composable
private fun ModelMissingPreview() = ModelPreview(ModelState.Missing)

@Preview
@Composable
private fun ModelImportingPreview() = ModelPreview(ModelState.Importing(0.42f))

@Preview
@Composable
private fun ModelReadyPreview() = ModelPreview(ModelState.Ready("/data/models/m"))

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelReadyDarkPreview() = BibiTheme(darkTheme = true) {
    ModelImportScreen(ModelState.Ready("/data/models/m"), {}, {}, {}, {}, {}) {
        VoicePackSection(ModelState.Importing(0.3f), {}, {}, {})
    }
}

@Preview
@Composable
private fun ModelDownloadIdlePreview() = BibiTheme { ModelImportScreen(ModelState.Missing, {}, {}, {}, {}, {}, download = ModelDownloadState.Idle) }

@Preview
@Composable
private fun ModelDownloadingPreview() = BibiTheme {
    ModelImportScreen(ModelState.Missing, {}, {}, {}, {}, {}, download = ModelDownloadState.Downloading(245_000_000, 584_417_280, waiting = false))
}

@Preview
@Composable
private fun ModelDownloadFailedPreview() = BibiTheme { ModelImportScreen(ModelState.Missing, {}, {}, {}, {}, {}, download = ModelDownloadState.Failed(401)) }

@Preview(fontScale = 2f)
@Composable
private fun ModelInvalidPreview() = ModelPreview(ModelState.Invalid("checksum"))

@Preview
@Composable
private fun ModelWithRouterMissingPreview() = BibiTheme {
    ModelImportScreen(ModelState.Ready("/data/models/m"), {}, {}, {}, {}, {}) {
        RouterModelSection(ModelState.Missing, {}, {}, {})
    }
}

@Preview
@Composable
private fun ModelWithRouterReadyPreview() = BibiTheme {
    ModelImportScreen(ModelState.Ready("/data/models/m"), {}, {}, {}, {}, {}) {
        RouterModelSection(ModelState.Ready("/data/models/r"), {}, {}, {})
    }
}
