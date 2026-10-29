package io.github.saadahmed1122.bibi.spike.s2

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * LiteRT-LM adapter: the ONLY file that imports com.google.ai.edge.litertlm.
 *
 * Written from (no Context7, no build; Google Maven was unreachable, so this file is NOT
 * type-checked, see tools/typecheck.sh):
 *  - LiteRT-LM Kotlin guide, docs/api/kotlin/getting_started.md in github.com/google-ai-edge/LiteRT-LM
 *  - the v0.16.1 tag sources: Config.kt (EngineConfig, Backend.CPU, ConversationConfig,
 *    SamplerConfig), Engine.kt, Conversation.kt (sendMessageAsync with MessageCallback,
 *    cancelProcess, getBenchmarkInfo), Benchmark.kt (BenchmarkInfo), ExperimentalFlags.kt.
 *
 * CPU backend only: PRD 0.5 says not to rely on GPU/NPU on the floor device.
 */
class LiteRtLmEngine(
    /** Writable dir; the guide says it "can improve 2nd load time". */
    private val cacheDir: String,
    private val systemPrompt: String,
    /** null = the runtime's default thread count [VERIFY what that is on the Helio G85]. */
    private val cpuThreads: Int? = null,
) : LlmEngine {

    override val label: String = "litert-lm $LIBRARY_VERSION CPU threads=${cpuThreads ?: "default"}"

    private var engine: Engine? = null

    @Volatile
    private var running: Conversation? = null

    @OptIn(ExperimentalApi::class)
    override fun load(path: String) {
        check(engine == null) { "already loaded" }
        // Read when an Engine is created; makes Conversation.getBenchmarkInfo() available.
        ExperimentalFlags.enableBenchmark = true
        val e = Engine(
            EngineConfig(
                modelPath = path,
                backend = Backend.CPU(threadCount = cpuThreads),
                cacheDir = cacheDir,
            ),
        )
        e.initialize() // "can take a significant amount of time (e.g., 10 seconds)"
        engine = e
    }

    @OptIn(ExperimentalApi::class)
    override fun generate(prompt: String, maxTokens: Int, onToken: (String) -> Unit): EngineCounts {
        val e = checkNotNull(engine) { "load() first" }
        val config = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            // topK = 1 is greedy decoding, so runs are repeatable across models and devices.
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0),
            // Docstring: "maximum number of output tokens per decode step" [VERIFY it caps the reply].
            maxOutputToken = maxTokens,
        )
        e.createConversation(config).use { conversation ->
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            running = conversation
            try {
                conversation.sendMessageAsync(
                    prompt,
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val text = message.toString()
                            if (text.isNotEmpty()) onToken(text)
                        }

                        override fun onDone() = done.countDown()

                        override fun onError(throwable: Throwable) {
                            failure = throwable
                            done.countDown()
                        }
                    },
                )
                if (!done.await(TIMEOUT_S, TimeUnit.SECONDS)) {
                    conversation.cancelProcess()
                    done.await(5, TimeUnit.SECONDS)
                    throw TimeoutException("no onDone within $TIMEOUT_S s")
                }
            } finally {
                running = null
            }
            failure?.let { throw it }
            val info = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
            return EngineCounts(
                prefillTokens = info?.lastPrefillTokenCount,
                decodeTokens = info?.lastDecodeTokenCount,
                ttftMs = info?.timeToFirstTokenInSecond?.times(1000.0),
                decodeTokensPerSecond = info?.lastDecodeTokensPerSecond,
            )
        }
    }

    override fun cancel() {
        running?.let { runCatching { it.cancelProcess() } }
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        /** Must match app/build.gradle.kts. */
        const val LIBRARY_VERSION = "0.16.1"

        /** Per-prompt safety limit; 128 tokens at 1 token/s would still finish. */
        const val TIMEOUT_S = 300L
    }
}
