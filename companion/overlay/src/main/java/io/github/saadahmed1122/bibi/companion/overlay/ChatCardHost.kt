package io.github.saadahmed1122.bibi.companion.overlay

import androidx.compose.runtime.Composable
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import kotlinx.coroutines.flow.StateFlow

/**
 * Implemented by the Application: the chat card's content and Bibi's current mood. Lets this module
 * show the app's chat and state without depending on it.
 */
interface ChatCardHost {
    @Composable
    fun ChatCardContent(onClose: () -> Unit)

    /** What floating Bibi shows (SPEC-avatar-states.md). */
    val bibiMood: StateFlow<BibiMood>
}
