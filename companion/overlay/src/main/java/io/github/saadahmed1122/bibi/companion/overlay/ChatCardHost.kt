package io.github.saadahmed1122.bibi.companion.overlay

import androidx.compose.runtime.Composable
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import kotlinx.coroutines.flow.StateFlow

/**
 * Implemented by the Application: the chat card's and talk bubble's content and Bibi's current mood. Lets this module
 * show the app's chat and state without depending on it.
 */
interface ChatCardHost {
    @Composable
    fun ChatCardContent(onClose: () -> Unit)

    /**
     * Tap-to-talk (#118): starts a spoken conversation while shown and stops it when removed.
     * [onOpenChat] asks for the chat card instead; [onEnd] says the conversation is over.
     */
    @Composable
    fun TalkContent(onOpenChat: () -> Unit, onEnd: () -> Unit)

    /** What floating Bibi shows (SPEC-avatar-states.md). */
    val bibiMood: StateFlow<BibiMood>
}
