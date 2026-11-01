package io.github.saadahmed1122.bibi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.saadahmed1122.bibi.ai.inference.api.Persona
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SettingsRoute(
    persona: PersonaSetting,
    safeMode: StateFlow<Boolean>,
    onSafeMode: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by persona.current.collectAsStateWithLifecycle()
    val safe by safeMode.collectAsStateWithLifecycle()
    SettingsScreen(current, persona::choose, onBack, modifier, safe, onSafeMode, onOpenPrivacy)
}

/** Settings (SPEC-personality.md). The Privacy dashboard and Safe mode join it later (SPEC-privacy.md). */
@Composable
fun SettingsScreen(
    persona: String,
    onPersona: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    safeMode: Boolean = false,
    onSafeMode: (Boolean) -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.settings_personality), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                PersonaOption(Persona.FRIENDLY, R.string.persona_friendly, R.string.persona_friendly_desc, persona, onPersona)
                PersonaOption(Persona.CONCISE, R.string.persona_concise, R.string.persona_concise_desc, persona, onPersona)
            }
            Text(stringResource(R.string.settings_personality_note), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_safe_mode), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_safe_mode_desc), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = safeMode, onCheckedChange = onSafeMode)
            }
            HorizontalDivider()
            TextButton(onClick = onOpenPrivacy) { Text(stringResource(R.string.settings_privacy)) }
        }
    }
}

@Composable
private fun PersonaOption(id: String, title: Int, description: Int, selected: String, onPersona: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = id == selected, onClick = { onPersona(id) }, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = id == selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview
@Composable
private fun SettingsFriendlyPreview() = BibiTheme { SettingsScreen(Persona.FRIENDLY, {}, {}) }

@Preview
@Composable
private fun SettingsConcisePreview() = BibiTheme { SettingsScreen(Persona.CONCISE, {}, {}) }
