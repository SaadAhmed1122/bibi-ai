package io.github.saadahmed1122.bibi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.saadahmed1122.bibi.companion.overlay.FloatingState
import io.github.saadahmed1122.bibi.core.designsystem.BibiAvatar
import io.github.saadahmed1122.bibi.core.designsystem.BibiCard
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.designsystem.BibiScreen
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.core.designsystem.ListRow
import io.github.saadahmed1122.bibi.core.designsystem.LocalBibiAccents
import io.github.saadahmed1122.bibi.core.designsystem.PrimaryButton
import io.github.saadahmed1122.bibi.core.designsystem.SectionHeader
import io.github.saadahmed1122.bibi.core.designsystem.Space
import io.github.saadahmed1122.bibi.core.designsystem.SwitchRow
import io.github.saadahmed1122.bibi.core.designsystem.R as DsR

/**
 * Home: how to reach Bibi, the floating switches and the model. Memory and Settings are in the tab
 * bar. [onOpenCrashLog], [onOpenLlmDebug] and [onOpenActionsDebug] are null in release builds.
 */
@Composable
fun HomeScreen(
    onOpenChat: () -> Unit,
    onOpenModel: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenCrashLog: (() -> Unit)?,
    onOpenLlmDebug: (() -> Unit)?,
    floating: FloatingState,
    onFloatingSwitch: (Boolean) -> Unit,
    onCalmSwitch: (Boolean) -> Unit,
    onExplainerContinue: () -> Unit,
    onExplainerDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenActionsDebug: (() -> Unit)? = null,
) {
    BibiScreen(title = stringResource(R.string.home_title), modifier = modifier) {
        Text(stringResource(R.string.home_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Hero(floatingOn = floating.enabled, onOpenChat = onOpenChat)
        SectionHeader(stringResource(R.string.home_section_floating))
        BibiCard {
            SwitchRow(stringResource(R.string.home_floating), floating.enabled, onFloatingSwitch, subtitle = stringResource(R.string.home_floating_hint))
            SwitchRow(stringResource(R.string.home_calm), floating.calm, onCalmSwitch, subtitle = stringResource(R.string.home_calm_hint))
        }
        SectionHeader(stringResource(R.string.home_section_brain))
        BibiCard {
            ListRow(stringResource(R.string.home_model), subtitle = stringResource(R.string.home_model_hint), onClick = onOpenModel)
            ListRow(stringResource(R.string.terms_link), onClick = onOpenTerms)
        }
        if (onOpenCrashLog != null || onOpenLlmDebug != null || onOpenActionsDebug != null) {
            SectionHeader(stringResource(R.string.home_section_debug))
            BibiCard {
                onOpenCrashLog?.let { ListRow(stringResource(R.string.crash_log_title), onClick = it) }
                onOpenLlmDebug?.let { ListRow(stringResource(R.string.llm_debug_title), onClick = it) }
                onOpenActionsDebug?.let { ListRow(stringResource(R.string.actions_debug_title), onClick = it) }
            }
        }
    }
    if (floating.showExplainer) {
        AlertDialog(
            onDismissRequest = onExplainerDismiss,
            title = { Text(stringResource(R.string.floating_explainer_title)) },
            text = { Text(stringResource(R.string.floating_explainer_body)) },
            confirmButton = { TextButton(onClick = onExplainerContinue) { Text(stringResource(R.string.floating_explainer_open)) } },
            dismissButton = { TextButton(onClick = onExplainerDismiss) { Text(stringResource(R.string.floating_explainer_not_now)) } },
        )
    }
}

/** Bibi's card: how to talk to it right now, and the way into the chat. */
@Composable
private fun Hero(floatingOn: Boolean, onOpenChat: () -> Unit) {
    val accents = LocalBibiAccents.current
    Surface(color = accents.hero, contentColor = accents.onHero, shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(Space.xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                Text(
                    stringResource(if (floatingOn) R.string.home_hero_floating else R.string.home_hero_app),
                    style = MaterialTheme.typography.headlineSmall,
                )
                PrimaryButton(stringResource(R.string.home_chat), onClick = onOpenChat, icon = DsR.drawable.ic_chat)
            }
            BibiAvatar(BibiMood.IDLE, size = Space.avatarLarge, decorative = true)
        }
    }
}

@Composable
private fun HomePreview(floating: FloatingState, debug: Boolean = true, dark: Boolean = false) {
    BibiTheme(darkTheme = dark) {
        HomeScreen(
            onOpenChat = {}, onOpenModel = {}, onOpenTerms = {},
            onOpenCrashLog = if (debug) ({}) else null, onOpenLlmDebug = if (debug) ({}) else null,
            floating = floating, onFloatingSwitch = {}, onCalmSwitch = {}, onExplainerContinue = {}, onExplainerDismiss = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenDebugPreview() = HomePreview(FloatingState(enabled = true, calm = false))

@Preview
@Composable
private fun HomeScreenReleaseDarkPreview() = HomePreview(FloatingState(enabled = false, calm = false), debug = false, dark = true)

@Preview(fontScale = 2f)
@Composable
private fun HomeScreenLargeFontPreview() = HomePreview(FloatingState(enabled = true, calm = true), debug = false)

@Preview
@Composable
private fun HomeScreenExplainerPreview() = HomePreview(FloatingState(enabled = false, calm = false, showExplainer = true))
