package io.github.saadahmed1122.bibi.companion.overlay

import androidx.compose.runtime.Composable

/**
 * Implemented by the Application: the chat card's content. Lets this module show the app's chat
 * without depending on it.
 */
interface ChatCardHost {
    @Composable
    fun ChatCardContent(onClose: () -> Unit)
}
