package io.github.saadahmed1122.bibi.core.designsystem

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Bibi's four MVP states (SPEC-avatar-states.md, docs/design/character/states/). Each has its own
 * pose, not only a color, and a screen-reader line from states.json.
 */
enum class BibiMood(@DrawableRes val frame: Int, @StringRes val description: Int) {
    IDLE(R.drawable.bibi_idle, R.string.bibi_mood_idle),
    THINKING(R.drawable.bibi_thinking, R.string.bibi_mood_thinking),
    SPEAKING(R.drawable.bibi_speaking, R.string.bibi_mood_speaking),
    CONFUSED(R.drawable.bibi_confused, R.string.bibi_mood_confused),
    ;

    companion object {
        /** The idle loop's blink frame. */
        val BLINK_FRAME = R.drawable.bibi_idle_blink

        /** The one to show when several chats report at once: something happening beats idle. */
        fun strongest(moods: Collection<BibiMood>): BibiMood = moods.maxByOrNull { it.priority } ?: IDLE

        private val BibiMood.priority get() = when (this) {
            IDLE -> 0
            THINKING -> 1
            SPEAKING -> 2
            CONFUSED -> 3
        }
    }
}
