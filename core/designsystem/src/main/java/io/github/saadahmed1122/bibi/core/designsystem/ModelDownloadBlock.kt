package io.github.saadahmed1122.bibi.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.saadahmed1122.bibi.domain.model.ModelDownloadState
import io.github.saadahmed1122.bibi.domain.model.ModelFile

/**
 * The in-app download's part of a model card (SPEC-model-download.md), shared by first run and the
 * model screen: a Download button when idle, progress and Cancel while running, the reason after a
 * failure. Stateless; the caller owns the ViewModel.
 */
@Composable
fun ModelDownloadBlock(state: ModelDownloadState, onDownload: () -> Unit, onCancel: () -> Unit) {
    val totalMb = (ModelFile.SIZE_BYTES / MB).toInt()
    when (state) {
        ModelDownloadState.Idle -> {
            PrimaryButton(stringResource(R.string.model_download, totalMb), onClick = onDownload)
            Text(stringResource(R.string.model_download_wifi_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is ModelDownloadState.Downloading -> {
            val done = (state.done / MB).toInt()
            val total = ((state.total ?: ModelFile.SIZE_BYTES) / MB).toInt()
            val text = when {
                state.waiting -> stringResource(R.string.model_download_waiting, done, total)
                state.total == null && state.done == 0L -> stringResource(R.string.model_downloading_start)
                else -> stringResource(R.string.model_downloading, done, total)
            }
            Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            val progress = state.progress
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.model_download_cancel), onClick = onCancel)
        }
        is ModelDownloadState.Failed -> {
            val message = when (state.kind) {
                ModelDownloadState.Kind.NeedsLogin -> stringResource(R.string.model_download_failed_login, state.code)
                ModelDownloadState.Kind.NotFound -> stringResource(R.string.model_download_failed_missing, state.code)
                ModelDownloadState.Kind.NoSpace -> stringResource(R.string.model_download_failed_space)
                ModelDownloadState.Kind.Network -> stringResource(R.string.model_download_failed_network, state.code)
            }
            Text(message, color = MaterialTheme.colorScheme.error)
            PrimaryButton(stringResource(R.string.model_download_retry), onClick = onDownload)
        }
    }
}

private const val MB = 1_000_000L
