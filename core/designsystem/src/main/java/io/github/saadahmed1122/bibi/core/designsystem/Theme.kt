package io.github.saadahmed1122.bibi.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Direction A, "Expressive Tabby" (SPEC-design.md, picked by the owner 2026-11-01): tangerine from
 * Bibi's fur, violet from the collar, warm neutrals. Text pairs are WCAG AA (checked 2026-11-01).
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFFB8490A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9B8),
    onPrimaryContainer = Color(0xFF3A1D0C),
    secondary = Color(0xFF6D4CF0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF22125C),
    tertiary = Color(0xFF7A5200),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE3A8),
    onTertiaryContainer = Color(0xFF2E1F00),
    background = Color(0xFFFBF6F1),
    onBackground = Color(0xFF2B1A12),
    surface = Color(0xFFFBF6F1),
    onSurface = Color(0xFF2B1A12),
    surfaceVariant = Color(0xFFF3E7DD),
    onSurfaceVariant = Color(0xFF6E5649),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFBF8),
    surfaceContainer = Color(0xFFF7EEE7),
    surfaceContainerHigh = Color(0xFFF3E7DD),
    surfaceContainerHighest = Color(0xFFEEDFD3),
    outline = Color(0xFF9C8274),
    outlineVariant = Color(0xFFE3D3C6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68A),
    onPrimary = Color(0xFF4E2200),
    primaryContainer = Color(0xFF6E3410),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFCABEFF),
    onSecondary = Color(0xFF2F1B85),
    secondaryContainer = Color(0xFF4A33B8),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = Color(0xFFF1C069),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5E4100),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF1D1410),
    onBackground = Color(0xFFF7E9DF),
    surface = Color(0xFF1D1410),
    onSurface = Color(0xFFF7E9DF),
    surfaceVariant = Color(0xFF3D2E25),
    onSurfaceVariant = Color(0xFFD8C2B4),
    surfaceContainerLowest = Color(0xFF170F0B),
    surfaceContainerLow = Color(0xFF251B16),
    surfaceContainer = Color(0xFF2A1F19),
    surfaceContainerHigh = Color(0xFF352821),
    surfaceContainerHighest = Color(0xFF40332A),
    outline = Color(0xFFA08D80),
    outlineVariant = Color(0xFF52433A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Colours outside the Material roles: the listening ring and Bibi's hero card (decoration, never text). */
data class BibiAccents(val listening: Color, val hero: Color, val onHero: Color)

private val LightAccents = BibiAccents(listening = Color(0xFFE8661C), hero = Color(0xFFFFD9B8), onHero = Color(0xFF3A1D0C))
private val DarkAccents = BibiAccents(listening = Color(0xFFFF8A3D), hero = Color(0xFF4A2A17), onHero = Color(0xFFFFE3CF))

val LocalBibiAccents = staticCompositionLocalOf { LightAccents }

/** Material 3's Expressive theme and motion are internal in Compose BOM 2026.06.01, so this uses the stable theme. */
@Composable
fun BibiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBibiAccents provides if (darkTheme) DarkAccents else LightAccents) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = BibiShapes,
            typography = BibiTypography,
            content = content,
        )
    }
}
