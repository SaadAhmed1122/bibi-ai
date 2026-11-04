package io.github.saadahmed1122.bibi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.ai.inference.api.Persona
import io.github.saadahmed1122.bibi.core.designsystem.BibiCard
import io.github.saadahmed1122.bibi.core.designsystem.BibiScreen
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.core.designsystem.ListRow
import io.github.saadahmed1122.bibi.core.designsystem.SectionHeader
import io.github.saadahmed1122.bibi.core.designsystem.Space
import io.github.saadahmed1122.bibi.core.designsystem.SwitchRow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SettingsRoute(
    persona: PersonaSetting,
    safeMode: StateFlow<Boolean>,
    onSafeMode: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenPolicy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenUsage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by persona.current.collectAsStateWithLifecycle()
    val safe by safeMode.collectAsStateWithLifecycle()
    SettingsScreen(current, persona::choose, modifier, safe, onSafeMode, onOpenPrivacy, onOpenPolicy, onOpenLicenses, onOpenUsage)
}

/** Settings (SPEC-personality.md): persona, Safe mode, and links to Privacy, the policy and licenses (SPEC-privacy.md). A tab: no Back. */
@Composable
fun SettingsScreen(
    persona: String,
    onPersona: (String) -> Unit,
    modifier: Modifier = Modifier,
    safeMode: Boolean = false,
    onSafeMode: (Boolean) -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenPolicy: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    onOpenUsage: () -> Unit = {},
) {
    BibiScreen(title = stringResource(R.string.settings_title), modifier = modifier) {
        SectionHeader(stringResource(R.string.settings_personality))
        BibiCard(Modifier.selectableGroup()) {
            PersonaOption(Persona.FRIENDLY, R.string.persona_friendly, R.string.persona_friendly_desc, persona, onPersona)
            PersonaOption(Persona.CONCISE, R.string.persona_concise, R.string.persona_concise_desc, persona, onPersona)
        }
        Text(
            stringResource(R.string.settings_personality_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.l),
        )
        SectionHeader(stringResource(R.string.settings_section_safety))
        BibiCard {
            SwitchRow(stringResource(R.string.settings_safe_mode), safeMode, onSafeMode, subtitle = stringResource(R.string.settings_safe_mode_desc))
            ListRow(stringResource(R.string.settings_privacy), onClick = onOpenPrivacy)
        }
        SectionHeader(stringResource(R.string.settings_section_about))
        BibiCard {
            ListRow(stringResource(R.string.policy_title), onClick = onOpenPolicy)
            ListRow(stringResource(R.string.licenses_title), onClick = onOpenLicenses)
            ListRow(stringResource(R.string.usage_title), onClick = onOpenUsage)
        }
    }
}

@Composable
private fun PersonaOption(id: String, title: Int, description: Int, selected: String, onPersona: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = id == selected, onClick = { onPersona(id) }, role = Role.RadioButton)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = id == selected, onClick = null)
    }
}

@Preview
@Composable
private fun SettingsFriendlyPreview() = BibiTheme { SettingsScreen(Persona.FRIENDLY, {}) }

@Preview
@Composable
private fun SettingsConciseDarkPreview() = BibiTheme(darkTheme = true) { SettingsScreen(Persona.CONCISE, {}, safeMode = true) }

@Preview(fontScale = 2f)
@Composable
private fun SettingsLargeFontPreview() = BibiTheme { SettingsScreen(Persona.FRIENDLY, {}) }
