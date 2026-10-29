package io.github.saadahmed1122.bibi.spike.s2

/**
 * The only surface the benchmark sees of an LLM runtime. Every library-specific call lives in one
 * adapter ([LiteRtLmEngine]); a llama.cpp adapter would implement the same interface [OPEN].
 *
 * All calls are blocking and must run on the one engine thread (PRD §12.7), except [cancel].
 */
interface LlmEngine : AutoCloseable {

    /** Runtime and backend label for the logs, e.g. "litert-lm 0.16.1 CPU". */
    val label: String

    /** Loads the model file. Throws on failure. Call once per instance. */
    fun load(path: String)

    /**
     * Generates a reply to [prompt] in a fresh single-turn chat, at most [maxTokens] new tokens.
     * [onToken] gets each streamed text chunk on the runtime's callback thread, in order.
     * Returns the runtime's own counters, any of which may be null if the runtime can't report it.
     * Throws on error or timeout.
     */
    fun generate(prompt: String, maxTokens: Int, onToken: (String) -> Unit): EngineCounts

    /** Asks a running [generate] to stop early. Safe to call from any thread; no-op when idle. */
    fun cancel()

    /** Frees the model. The instance can't be used afterwards. */
    override fun close()
}

/** What the runtime itself reports about the last [LlmEngine.generate] call. */
data class EngineCounts(
    val prefillTokens: Int? = null,
    val decodeTokens: Int? = null,
    val ttftMs: Double? = null,
    val decodeTokensPerSecond: Double? = null,
)
