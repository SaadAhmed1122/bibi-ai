package io.github.saadahmed1122.bibi.ai.inference.litert

import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent

/** How a turn ends, decided from what happened during it. Pure Kotlin, JVM-tested. */
internal object ReplyOutcome {

    /**
     * Answer cap (SPEC-inference.md "Short answers"). The engine enforces it through
     * `ConversationConfig.maxOutputToken` (LiteRtLmEngine). The service also counts streamed
     * chunks as a backstop, a few past the cap so the engine's own clean stop comes first: a
     * chunk is at most one decode step, and the backstop's cancel costs a conversation reopen.
     */
    const val MAX_REPLY_TOKENS = 256

    private const val BACKSTOP_MARGIN = 8

    fun reachedCap(chunks: Int): Boolean = chunks >= MAX_REPLY_TOKENS + BACKSTOP_MARGIN

    /** True when the turn was cancelled or failed, which leaves 0.16.1's conversation unusable. */
    fun needsNewConversation(terminal: ReplyEvent, capped: Boolean): Boolean = capped || terminal !is ReplyEvent.Done

    /**
     * A requested stop wins: `cancelProcess()` ends the turn through the error callback, and that
     * error is the stop itself. A reply cut by the backstop cap counts as finished.
     */
    fun terminal(text: String, stopRequested: Boolean, capped: Boolean, failure: String?): ReplyEvent = when {
        stopRequested -> ReplyEvent.Stopped(text)
        capped || failure == null -> ReplyEvent.Done(text)
        else -> ReplyEvent.Error(failure, text)
    }
}
