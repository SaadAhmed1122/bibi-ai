package io.github.saadahmed1122.bibi.ai.inference.litert

/** One finished exchange, carried into a reopened conversation. */
internal data class Turn(val user: String, val reply: String)

/**
 * Tracks how full the conversation's KV cache is and which recent turns to carry over when it is
 * reopened (SPEC-inference.md, tasks/plan.md: last 6 turns). Pure Kotlin, JVM-tested.
 *
 * Token counts are estimates from characters, as in S2 (`Metrics.needsContextReset`): about
 * 4.5 characters per token was measured, 3 is used to stay on the safe side, plus [TURN_TEMPLATE_TOKENS]
 * for the chat template around each message.
 *
 * Not thread-safe: used on the service's engine thread only.
 */
internal class ContextBudget(
    prefaceChars: Int,
    private val contextTokens: Int = LiteRtLmEngine.MAX_NUM_TOKENS,
    private val maxReplyTokens: Int = ReplyOutcome.MAX_REPLY_TOKENS,
    private val keepTurns: Int = KEEP_TURNS,
    private val maxCarryTokens: Int = MAX_CARRY_TOKENS,
) {
    private val prefaceTokens = estimate(prefaceChars) + TURN_TEMPLATE_TOKENS

    /** Recent finished turns, newest last; at most [keepTurns]. */
    private val recent = ArrayDeque<Turn>()

    /** Estimated tokens in the current conversation, preface included. */
    var usedTokens = prefaceTokens
        private set

    /** Turns sent in the current conversation, carried ones included. */
    private var turnsInConversation = 0

    /**
     * True if a turn with [userText] plus a full-length reply could overflow the cache. A
     * conversation with no turns is never reset, so an oversized preface can't loop.
     */
    fun needsReset(userText: String): Boolean =
        turnsInConversation > 0 && usedTokens + turnTokens(userText) + maxReplyTokens > contextTokens

    /** Records a finished turn. A turn with an empty reply is not carried over. */
    fun record(user: String, reply: String) {
        usedTokens += turnTokens(user) + turnTokens(reply)
        turnsInConversation++
        if (reply.isBlank()) return
        recent.addLast(Turn(user, reply))
        while (recent.size > keepTurns) recent.removeFirst()
    }

    /**
     * The turns to prefill into a reopened conversation: the newest of the last [keepTurns] that
     * fit in [maxCarryTokens] and still leave room for a maximum-length message and reply, so the
     * next turn never needs a reset straight away. Also resets the counters to that conversation.
     */
    fun reopen(): List<Turn> {
        val reserve = turnTokens("x".repeat(MAX_PROMPT_CHARS)) + maxReplyTokens
        var used = prefaceTokens
        val carried = ArrayDeque<Turn>()
        for (turn in recent.reversed()) {
            val cost = turnTokens(turn.user) + turnTokens(turn.reply)
            if (used + cost - prefaceTokens > maxCarryTokens || used + cost + reserve > contextTokens) break
            used += cost
            carried.addFirst(turn)
        }
        usedTokens = used
        turnsInConversation = carried.size
        return carried.toList()
    }

    /** Clear chat: forget every turn. */
    fun clear() {
        recent.clear()
        usedTokens = prefaceTokens
        turnsInConversation = 0
    }

    private fun turnTokens(text: String) = estimate(text.length) + TURN_TEMPLATE_TOKENS

    companion object {
        const val KEEP_TURNS = 6

        /**
         * Reopening prefills about 50 estimated tokens per second on the vivo (2026-10-31: 7 s for
         * the preface, plus 39–58 s for 2,000–2,750 carried tokens). 400 keeps a reopen near 15 s:
         * six short turns, or one long answer.
         */
        const val MAX_CARRY_TOKENS = 400

        /** The chat input cap (feature:chat MAX_INPUT_CHARS), the longest message a turn can add. */
        const val MAX_PROMPT_CHARS = 2_000

        /** Chat-template tokens around one message (S2 Metrics). */
        const val TURN_TEMPLATE_TOKENS = 16

        private fun estimate(chars: Int) = (chars + 2) / 3
    }
}
