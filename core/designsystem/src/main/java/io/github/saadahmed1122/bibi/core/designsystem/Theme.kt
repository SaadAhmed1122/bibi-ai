package io.github.saadahmed1122.bibi.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Bibi's palette (docs/design/bibi-character-gemini.md). Violet is the seed color. */
object BibiColors {
    val Violet = Color(0xFF7C5CFF)
    val Fur = Color(0xFFF4A54A)
    val Stripe = Color(0xFFE07B2A)
    val Cream = Color(0xFFFFF4E0)
    val Outline = Color(0xFF5A2E1E)
    val Eyes = Color(0xFF4A1E14)
}

private val LightColors = lightColorScheme(
    primary = BibiColors.Violet,
    secondary = BibiColors.Stripe,
    tertiary = BibiColors.Fur,
    background = BibiColors.Cream,
    surface = BibiColors.Cream,
    onBackground = BibiColors.Eyes,
    onSurface = BibiColors.Eyes,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9A6FF),
    secondary = BibiColors.Fur,
    tertiary = BibiColors.Fur,
)

@Composable
fun BibiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
