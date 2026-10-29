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

    /** A `kB` value from /proc/self/status text, e.g. key "VmHWM" (peak RSS); null if absent. */
    fun procStatusKb(status: String, key: String): Long? =
        status.lineSequence()
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()
            ?.substringBefore(' ')
            ?.toLongOrNull()
}
