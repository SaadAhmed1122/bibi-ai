package io.github.saadahmed1122.bibi.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.domain.model.ModelFile
import io.github.saadahmed1122.bibi.domain.model.ModelState

/** Stateless first-run screen; the app owns the file picker and the permission request. */
@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    modelState: ModelState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onPickModel: () -> Unit,
    onCancelImport: () -> Unit,
    onOpenTerms: () -> Unit,
    onAllowNotifications: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.onb_step, step.ordinal + 1), style = MaterialTheme.typography.labelLarge)
            when (step) {
                OnboardingStep.Welcome -> {
                    Title(R.string.onb_welcome_title)
                    Text(stringResource(R.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onNext) { Text(stringResource(R.string.onb_next)) }
                }
                OnboardingStep.Model -> {
                    Title(R.string.onb_model_title)
                    Text(stringResource(R.string.onb_model_body, ModelFile.NAME), style = MaterialTheme.typography.bodyLarge)
                    ModelPart(modelState, onPickModel, onCancelImport)
                    TextButton(onClick = onOpenTerms) { Text(stringResource(R.string.onb_terms)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.onb_back)) }
                        if (modelState is ModelState.Ready) {
                            Button(onClick = onNext) { Text(stringResource(R.string.onb_next)) }
                        } else if (modelState !is ModelState.Importing) {
                            TextButton(onClick = onNext) { Text(stringResource(R.string.onb_model_skip)) }
                        }
                    }
                }
                OnboardingStep.Notifications -> {
                    Title(R.string.onb_notif_title)
                    Text(stringResource(R.string.onb_notif_body), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onAllowNotifications) { Text(stringResource(R.string.onb_notif_allow)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.onb_back)) }
                        TextButton(onClick = onNotNow) { Text(stringResource(R.string.onb_notif_not_now)) }
                    }
                }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun Title(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun ModelPart(state: ModelState, onPick: () -> Unit, onCancel: () -> Unit) {
    when (state) {
        ModelState.Missing -> Button(onClick = onPick) { Text(stringResource(R.string.onb_model_choose)) }
        is ModelState.Importing -> {
            Text(stringResource(R.string.onb_model_importing, (state.progress * 100).toInt()))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.onb_model_cancel)) }
        }
        is ModelState.Ready -> Text(stringResource(R.string.onb_model_ready), style = MaterialTheme.typography.titleMedium)
        is ModelState.Invalid -> {
            Text(stringResource(R.string.onb_model_invalid, ModelFile.NAME), color = MaterialTheme.colorScheme.error)
            Button(onClick = onPick) { Text(stringResource(R.string.onb_model_choose)) }
        }
    }
}

@Composable
private fun OnboardingPreview(step: OnboardingStep, model: ModelState = ModelState.Missing) {
    BibiTheme { OnboardingScreen(step, model, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview
@Composable
private fun WelcomePreview() = OnboardingPreview(OnboardingStep.Welcome)

@Preview
@Composable
private fun ModelMissingPreview() = OnboardingPreview(OnboardingStep.Model)

@Preview
@Composable
private fun ModelImportingPreview() = OnboardingPreview(OnboardingStep.Model, ModelState.Importing(0.4f))

@Preview
@Composable
private fun ModelReadyPreview() = OnboardingPreview(OnboardingStep.Model, ModelState.Ready("/m"))

@Preview
@Composable
private fun NotificationsPreview() = OnboardingPreview(OnboardingStep.Notifications)
