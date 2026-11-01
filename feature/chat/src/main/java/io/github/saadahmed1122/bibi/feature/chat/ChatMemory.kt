package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.domain.usecase.NeverStore
import io.github.saadahmed1122.bibi.domain.usecase.Recall
import io.github.saadahmed1122.bibi.domain.usecase.Remember

/** Bibi's words about memory; the app supplies them from string resources. */
interface MemoryTexts {
    /** Bibi's reply to a typed "remember that …". */
    fun saved(): String

    /** Bibi's reply when the text is something it never stores. */
    fun blocked(reason: NeverStore.Reason): String

    /** The short notice after "Remember this" on a message. */
    fun noticeSaved(): String

    /** Bibi's reply / notice when Safe mode stops a save. */
    fun safeMode(): String = "Safe mode is on, so I won't save that."
}

/**
 * What the chat needs for memory (SPEC-memory.md): saving, and [recall] to add matching notes to
 * what the model sees. Null in [ChatViewModel] means no memory.
 */
class ChatMemory(
    val remember: Remember,
    val texts: MemoryTexts,
    val recall: Recall? = null,
    /** False in Safe mode (FR-076): nothing new is saved; what is saved can still be used. */
    val writesAllowed: () -> Boolean = { true },
)
