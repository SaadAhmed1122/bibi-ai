package io.github.saadahmed1122.bibi.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.domain.model.ModelFile
import io.github.saadahmed1122.bibi.domain.model.ModelState

/** [onPick] opens the system file picker; the caller turns the result into [ModelImportViewModel.import]. */
@Composable
fun ModelImportRoute(
    viewModel: ModelImportViewModel,
    onPick: () -> Unit,
    onOpenChat: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModelImportScreen(state, onPick, viewModel::cancel, viewModel::remove, onOpenChat, onBack, modifier)
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
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.chat_back)) }
            Text(stringResource(R.string.model_title), style = MaterialTheme.typography.headlineSmall)
            when (state) {
                ModelState.Missing -> {
                    Text(stringResource(R.string.model_missing, ModelFile.NAME))
                    Button(onClick = onPick) { Text(stringResource(R.string.model_choose)) }
                }
                is ModelState.Importing -> {
                    val percent = (state.progress * 100).toInt()
                    Text(
                        stringResource(R.string.model_importing, percent),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.model_cancel)) }
                }
                is ModelState.Ready -> {
                    Text(stringResource(R.string.model_ready))
                    Button(onClick = onOpenChat) { Text(stringResource(R.string.model_open_chat)) }
                    OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.model_remove)) }
                }
                is ModelState.Invalid -> {
                    val message = when (state.reason) {
                        "size" -> stringResource(R.string.model_invalid_size, ModelFile.NAME)
                        "checksum" -> stringResource(R.string.model_invalid_checksum, ModelFile.NAME)
                        else -> stringResource(R.string.model_invalid_read)
                    }
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onPick) { Text(stringResource(R.string.model_choose_other)) }
                }
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

@Preview
@Composable
private fun ModelInvalidPreview() = ModelPreview(ModelState.Invalid("checksum"))
