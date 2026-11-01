package io.github.saadahmed1122.bibi

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.core.designsystem.R as DsR

/** The four places one tap away (SPEC-design.md navigation). */
enum class Tab(val label: Int, val icon: Int) {
    HOME(R.string.tab_home, DsR.drawable.ic_home),
    CHAT(R.string.tab_chat, DsR.drawable.ic_chat),
    MEMORY(R.string.tab_memory, DsR.drawable.ic_memory),
    SETTINGS(R.string.tab_settings, DsR.drawable.ic_settings),
}

@Composable
fun AppNavBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(modifier = modifier, containerColor = colors.surfaceContainer) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = colors.primaryContainer,
                    selectedIconColor = colors.onPrimaryContainer,
                    selectedTextColor = colors.onSurface,
                ),
            )
        }
    }
}

/** The same places as a side rail, for landscape. */
@Composable
fun AppNavRail(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    NavigationRail(modifier = modifier, containerColor = colors.surfaceContainer) {
        Tab.entries.forEach { tab ->
            NavigationRailItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = colors.primaryContainer,
                    selectedIconColor = colors.onPrimaryContainer,
                    selectedTextColor = colors.onSurface,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun AppNavBarPreview() = BibiTheme { AppNavBar(Tab.HOME, {}) }

@Preview(heightDp = 400)
@Composable
private fun AppNavRailPreview() = BibiTheme(darkTheme = true) { AppNavRail(Tab.MEMORY, {}) }
