package io.github.saadahmed1122.bibi.ai.inference.litert

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * LiteRT-LM 0.16.1 adapter: the ONLY file that imports `com.google.ai.edge.litertlm`.
 *
 * Ported from spikes/s2-llm (LiteRtLmEngine.kt, chat mode), which ran on the vivo Y17s. API
 * checked with `javap` against the cached litertlm-android-0.16.1.aar (Context7 not available).
 *
 * - CPU backend only (PRD 0.5: don't rely on GPU/NPU on the floor device).
 * - One conversation. `prefillPrefaceOnInit = true` makes `createConversation()` prefill the
 *   preface before it returns, and each later turn prefills only its new text. Gemma 3's template
 *   has no system turn: a preface with only a system instruction renders to just the BOS token
 *   (S2 device run 2026-10-30), so [openChat] adds the scripted primer exchange as
 *   `initialMessages`. The system prompt then sits in the KV cache before the first real turn.
 * - Answer cap: `ConversationConfig.maxOutputToken`. It caps the whole reply: S2's Qwen3 replies
 *   stopped at exactly 128 with it set to 128, and on the vivo (2026-10-31) a "1000 words" request
 *   ended cleanly at 256 streamed chunks with it set to 256.
 *
 * Not thread-safe: call everything but [cancel] from one thread (InferenceService's engine thread).
 */
internal class LiteRtLmEngine(private val cacheDir: String) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    /** The turn in progress; read by [cancel] from another thread. */
    @Volatile private var running: Conversation? = null

    /** Blocks for the model load (seconds). */
    fun load(modelPath: String) {
        check(engine == null) { "already loaded" }
        val e = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                maxNumTokens = MAX_NUM_TOKENS,
                cacheDir = cacheDir,
            ),
        )
        e.initialize()
        engine = e
        // The runtime sets up the decode graph on the first decode step (~2 s on the vivo, device
        // run 2026-10-31), which would land in turn 1's TTFT. One throwaway token moves it here.
        e.createConversation(ConversationConfig(maxOutputToken = 1)).use { it.sendMessage(PRIMER_USER) }
    }

    /** Opens a fresh conversation with the preface prefilled; closes the old one. Blocks. */
    fun openChat(systemPrompt: String) {
        val e = checkNotNull(engine) { "load() first" }
        closeChat()
        conversation = e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                initialMessages = listOf(Message.user(PRIMER_USER), Message.model(PRIMER_REPLY)),
                // Greedy decoding, as in S2, so turns are repeatable [OPEN: tune for chat].
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0),
                maxOutputToken = ReplyOutcome.MAX_REPLY_TOKENS,
                prefillPrefaceOnInit = true,
            ),
        )
    }

    /** One blocking turn; streams non-empty chunks to [onChunk] on the runtime's callback thread. */
    fun chat(text: String, onChunk: (String) -> Unit) {
        val c = checkNotNull(conversation) { "openChat() first" }
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        running = c
        try {
            c.sendMessageAsync(
                text,
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val chunk = message.toString()
                        if (chunk.isNotEmpty()) onChunk(chunk)
                    }

                    override fun onDone() = done.countDown()

                    override fun onError(throwable: Throwable) {
                        failure = throwable
                        done.countDown()
                    }
                },
            )
            if (!done.await(TIMEOUT_S, TimeUnit.SECONDS)) {
                c.cancelProcess()
                done.await(5, TimeUnit.SECONDS)
                throw TimeoutException("no reply end within $TIMEOUT_S s")
            }
        } finally {
            running = null
        }
        failure?.let { throw it }
    }

    /**
     * Thread-safe: cancels the running turn, which then ends through the error callback.
     *
     * After a cancel the conversation is unusable: 0.16.1's session_advanced.h says CancelProcess
     * "leaves the session state poisoned", and on the vivo the next turn failed at once with
     * `CANCELLED: Task cancelled`. The caller must [openChat] again before the next turn.
     */
    fun cancel() {
        running?.let { runCatching { it.cancelProcess() } }
    }

    fun close() {
        closeChat()
        engine?.close()
        engine = null
    }

    private fun closeChat() {
        conversation?.let { runCatching { it.close() } }
        conversation = null
    }

    companion object {
        /** KV cache size, input plus output tokens (S2 chat mode). */
        const val MAX_NUM_TOKENS = 4096

        /** Scripted first exchange, prefilled with the system prompt and never decoded (S2). */
        const val PRIMER_USER = "Hi Bibi."
        const val PRIMER_REPLY = "Hi! What can I help you with?"

        /** Per-turn safety limit; 256 tokens at 1 token/s would still finish. */
        const val TIMEOUT_S = 300L
    }
}
