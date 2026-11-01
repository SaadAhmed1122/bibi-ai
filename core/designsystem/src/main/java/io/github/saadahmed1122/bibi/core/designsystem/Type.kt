package io.github.saadahmed1122.bibi.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Variable fonts in res/font (SIL Open Font License 1.1, credited in the Licenses screen). */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Bricolage Grotesque: titles and Bibi's big moments, used sparingly. */
val DisplayFont = FontFamily(variable(R.font.bricolage_grotesque, 500), variable(R.font.bricolage_grotesque, 700))

/** Figtree: everything people read. */
val TextFont = FontFamily(
    variable(R.font.figtree, 400),
    variable(R.font.figtree, 500),
    variable(R.font.figtree, 600),
)

private fun display(size: Int, line: Int, weight: Int = 700) =
    TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp, letterSpacing = (-0.02).em)

private fun text(size: Int, line: Int, weight: Int = 400) =
    TextStyle(fontFamily = TextFont, fontWeight = FontWeight(weight), fontSize = size.sp, lineHeight = line.sp)

val BibiTypography = Typography(
    displayLarge = display(52, 58),
    displayMedium = display(44, 50),
    displaySmall = display(36, 42),
    headlineLarge = display(32, 38),
    headlineMedium = display(28, 34),
    headlineSmall = display(24, 30),
    titleLarge = display(22, 28, 500),
    titleMedium = text(17, 24, 600),
    titleSmall = text(15, 20, 600),
    bodyLarge = text(17, 25),
    bodyMedium = text(15, 22),
    bodySmall = text(13, 18),
    labelLarge = text(15, 20, 600),
    labelMedium = text(13, 18, 600),
    labelSmall = text(12, 16, 500),
)
