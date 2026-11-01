package io.github.saadahmed1122.bibi.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.saadahmed1122.bibi.core.designsystem.BibiAvatar
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.designsystem.PrimaryButton
import io.github.saadahmed1122.bibi.core.designsystem.SecondaryButton
import io.github.saadahmed1122.bibi.core.designsystem.Space
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
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = Space.readable).verticalScroll(rememberScrollState()).padding(Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                Steps(step)
                BibiAvatar(
                    when (step) {
                        OnboardingStep.Welcome -> BibiMood.SPEAKING
                        OnboardingStep.Model -> BibiMood.THINKING
                        OnboardingStep.Notifications -> BibiMood.IDLE
                    },
                    size = Space.avatarLarge,
                    decorative = true,
                )
                when (step) {
                    OnboardingStep.Welcome -> {
                        Title(R.string.onb_welcome_title)
                        Body(stringResource(R.string.onb_welcome_body))
                        PrimaryButton(stringResource(R.string.onb_next), onClick = onNext)
                    }
                    OnboardingStep.Model -> {
                        Title(R.string.onb_model_title)
                        Body(stringResource(R.string.onb_model_body, ModelFile.NAME))
                        ModelPart(modelState, onPickModel, onCancelImport)
                        TextButton(onClick = onOpenTerms) { Text(stringResource(R.string.onb_terms)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                            SecondaryButton(stringResource(R.string.onb_back), onClick = onBack)
                            if (modelState is ModelState.Ready) {
                                PrimaryButton(stringResource(R.string.onb_next), onClick = onNext)
                            } else if (modelState !is ModelState.Importing) {
                                TextButton(onClick = onNext) { Text(stringResource(R.string.onb_model_skip)) }
                            }
                        }
                    }
                    OnboardingStep.Notifications -> {
                        Title(R.string.onb_notif_title)
                        Body(stringResource(R.string.onb_notif_body))
                        PrimaryButton(stringResource(R.string.onb_notif_allow), onClick = onAllowNotifications)
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                            SecondaryButton(stringResource(R.string.onb_back), onClick = onBack)
                            TextButton(onClick = onNotNow) { Text(stringResource(R.string.onb_notif_not_now)) }
                        }
                    }
                }
            }
        }
    }
}

/** Three short bars, the current one filled; TalkBack reads "Step 2 of 3". */
@Composable
private fun Steps(step: OnboardingStep) {
    val label = stringResource(R.string.onb_step, step.ordinal + 1)
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(Space.xs + Space.xs / 2)) {
        OnboardingStep.entries.forEach {
            Box(
                Modifier
                    .width(Space.xxl)
                    .height(Space.xs + Space.xs / 2)
                    .clip(CircleShape)
                    .background(if (it.ordinal <= step.ordinal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun Title(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ModelPart(state: ModelState, onPick: () -> Unit, onCancel: () -> Unit) {
    when (state) {
        ModelState.Missing -> PrimaryButton(stringResource(R.string.onb_model_choose), onClick = onPick)
        is ModelState.Importing -> {
            Text(stringResource(R.string.onb_model_importing, (state.progress * 100).toInt()))
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.onb_model_cancel), onClick = onCancel)
        }
        is ModelState.Ready -> Text(stringResource(R.string.onb_model_ready), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        is ModelState.Invalid -> {
            Text(stringResource(R.string.onb_model_invalid, ModelFile.NAME), color = MaterialTheme.colorScheme.error)
            PrimaryButton(stringResource(R.string.onb_model_choose), onClick = onPick)
        }
    }
}

@Composable
private fun OnboardingPreview(step: OnboardingStep, model: ModelState = ModelState.Missing, dark: Boolean = false) {
    BibiTheme(darkTheme = dark) { OnboardingScreen(step, model, {}, {}, {}, {}, {}, {}, {}) }
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

@Preview
@Composable
private fun WelcomeDarkPreview() = OnboardingPreview(OnboardingStep.Welcome, dark = true)

@Preview(fontScale = 2f)
@Composable
private fun ModelLargeFontPreview() = OnboardingPreview(OnboardingStep.Model, ModelState.Invalid("checksum"))
