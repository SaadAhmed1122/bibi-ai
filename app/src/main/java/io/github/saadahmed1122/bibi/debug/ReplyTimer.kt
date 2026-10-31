package io.github.saadahmed1122.bibi.debug

/**
 * S2-style timing for one reply, from the UI process's clock: TTFT is send to first token, speed
 * is tokens after the first over first-to-last token time. Counts streamed chunks as tokens.
 */
class ReplyTimer(private val startNs: Long) {
    var tokens = 0
        private set
    private var firstNs = 0L
    private var lastNs = 0L

    fun token(nowNs: Long) {
        if (tokens == 0) firstNs = nowNs
        lastNs = nowNs
        tokens++
    }

    fun ttftMs(): Double? = if (tokens > 0) (firstNs - startNs) / 1e6 else null

    fun tokensPerSecond(): Double? {
        val seconds = (lastNs - firstNs) / 1e9
        return if (tokens >= 2 && seconds > 0.0) (tokens - 1) / seconds else null
    }
}
