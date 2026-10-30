package io.github.saadahmed1122.bibi.spike.s2

/** Per-prompt timing math and /proc parsing. Pure Kotlin, JVM-testable. */
object Metrics {

    /** Time to first token: request sent → first non-empty streamed chunk. */
    fun ttftMs(startNs: Long, firstChunkNs: Long): Double = (firstChunkNs - startNs) / 1e6

    /**
     * Decode speed from the app's clock: tokens after the first one, divided by the time from the
     * first chunk to the last chunk. Null with fewer than 2 tokens or no time between chunks.
     */
    fun decodeTokensPerSecond(tokens: Int, firstChunkNs: Long, lastChunkNs: Long): Double? {
        val seconds = (lastChunkNs - firstChunkNs) / 1e9
        return if (tokens >= 2 && seconds > 0.0) (tokens - 1) / seconds else null
    }

    /** Chat-template tokens a user turn adds around its text (Gemma: end/start-of-turn markers). Rounded up. */
    const val TURN_TEMPLATE_TOKENS = 16

    /**
     * Upper-bound guess of the prefill tokens one user turn adds. The first S2 soak shows about
     * 4.5 characters per token for these English prompts (prompt_chars vs prefill_tokens); 3 is
     * used to stay on the safe side. The real count is logged per turn.
     */
    fun turnTokenEstimate(promptChars: Int): Int = (promptChars + 2) / 3 + TURN_TEMPLATE_TOKENS

    /**
     * Chat mode: true if the next turn (its prompt plus up to [maxNewTokens] of reply) could
     * overflow a KV cache of [contextTokens] that already holds [usedTokens]. A conversation with
     * no turns yet is never reset, so a too-long preface can't loop.
     */
    fun needsContextReset(usedTokens: Int, turnsInConversation: Int, promptChars: Int, maxNewTokens: Int, contextTokens: Int): Boolean =
        turnsInConversation > 0 && usedTokens + turnTokenEstimate(promptChars) + maxNewTokens > contextTokens

    /** A `kB` value from /proc/self/status text, e.g. key "VmHWM" (peak RSS); null if absent. */
    fun procStatusKb(status: String, key: String): Long? =
        status.lineSequence()
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()
            ?.substringBefore(' ')
            ?.toLongOrNull()
}
