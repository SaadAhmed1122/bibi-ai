package io.github.saadahmed1122.bibi.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Very round, "Expressive" corners. Pills come from [CircleShape][androidx.compose.foundation.shape.CircleShape]. */
val BibiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Spacing scale (4 dp grid) and fixed sizes. Screens use these, never raw numbers. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Side margin of every screen. */
    val gutter = 20.dp

    /** Minimum touch target. */
    val touch = 48.dp

    /** Readable width for text-heavy content in landscape. */
    val readable = 640.dp

    val avatarSmall = 40.dp
    val avatarLarge = 112.dp
}

/** Motion durations in ms, for things the motion scheme doesn't cover (Bibi's bubble, notices). */
object Durations {
    const val QUICK = 150
    const val STANDARD = 300
    const val NOTICE = 3_000
}
