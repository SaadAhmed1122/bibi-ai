package io.github.saadahmed1122.bibi.spike.s4

import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool

/**
 * LiteRT-LM tool-calling adapter: the ONLY S4 file that imports com.google.ai.edge.litertlm.
 * Gemma 3 1B int4 on CPU, one tool exposed per conversation, `automaticToolCalling = false`: the
 * library returns `Message.toolCalls` and never runs a tool. The calls are handed back as
 * [ModelTurn]s; [LlmToolRouter] re-serializes and validates them, and only the Permission Engine
 * and [ToolExecutor] can act.
 *
 * API checked against the 0.16.1 jar (javap) and the v0.16.1 sources and Kotlin guide in
 * github.com/google-ai-edge/LiteRT-LM (Tool.kt: ToolSet, @Tool, @ToolParam, tool(ToolSet);
 * Config.kt: ConversationConfig(tools, automaticToolCalling, …); Conversation.kt: sendMessage
 * returns the model Message with toolCalls when automaticToolCalling is false; Message.kt:
 * ToolCall(name, arguments)). Engine/EngineConfig usage follows spikes/s2-llm LiteRtLmEngine.kt.
 *
 * Not thread-safe: load and send from one thread (the caller's single-thread dispatcher).
 */
class LiteRtToolLlm(private val cacheDir: String) : AutoCloseable {
    private var engine: Engine? = null

    @Volatile
    private var running: Conversation? = null

    val isLoaded: Boolean get() = engine != null

    /** Slow (seconds): never on the main thread. */
    @OptIn(ExperimentalApi::class)
    fun load(modelPath: String) {
        check(engine == null) { "already loaded" }
        // Read when an Engine is created: makes Conversation.getBenchmarkInfo() (TTFT) available.
        ExperimentalFlags.enableBenchmark = true
        val e = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir))
        try {
            e.initialize()
        } catch (t: Throwable) {
            e.close()
            throw t
        }
        engine = e
    }

    /** One request: a fresh conversation that sees only [tool]. Close it after the (at most one) repair. */
    @OptIn(ExperimentalApi::class)
    fun open(tool: ToolName): Session {
        val e = checkNotNull(engine) { "load() first" }
        // Read when a Conversation is created. true is the default; set so wire names never depend on it:
        // openApp → open_app, setAlarm → set_alarm, startTimer → start_timer.
        ExperimentalFlags.convertCamelToSnakeCaseInToolDescription = true
        val config = ConversationConfig(
            systemInstruction = Contents.of(ToolRouting.systemInstruction(tool)),
            tools = listOf(tool(toolSet(tool))),
            // topK = 1 is greedy decoding, as in S2, so runs are repeatable.
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0),
            automaticToolCalling = false,
            maxOutputToken = MAX_OUTPUT_TOKENS,
        )
        return Session(e.createConversation(config))
    }

    inner class Session(private val conversation: Conversation) : AutoCloseable {
        /** TTFT of the first message, from the runtime's benchmark info (ms); null if unavailable. */
        var firstTtftMs: Double? = null
            private set

        /** Wall time of all [send] calls (ms). */
        var totalMs = 0.0
            private set

        @OptIn(ExperimentalApi::class) // getBenchmarkInfo()
        fun send(text: String): ModelTurn {
            running = conversation
            val t0 = SystemClock.elapsedRealtimeNanos()
            val message = try {
                conversation.sendMessage(text)
            } finally {
                running = null
                totalMs += (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
            }
            if (firstTtftMs == null) {
                firstTtftMs = runCatching { conversation.getBenchmarkInfo().timeToFirstTokenInSecond * 1000.0 }.getOrNull()
            }
            return ModelTurn(
                toolCalls = message.toolCalls.map { ModelToolCall(it.name, it.arguments) },
                text = message.contents.toString(),
            )
        }

        override fun close() = conversation.close()
    }

    /** Stops a running generation (from any thread). */
    fun cancel() {
        running?.let { runCatching { it.cancelProcess() } }
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        /** Must match app/build.gradle.kts. */
        const val LIBRARY_VERSION = "0.16.1"
        const val MODEL_FILE = "gemma3-1b-it-int4.litertlm"

        /** A tool call is short; this caps a model that chats instead [VERIFY it caps the reply]. */
        const val MAX_OUTPUT_TOKENS = 128

        private fun toolSet(tool: ToolName): ToolSet = when (tool) {
            ToolName.OPEN_APP -> OpenAppTool()
            ToolName.SET_ALARM -> SetAlarmTool()
            ToolName.START_TIMER -> StartTimerTool()
            ToolName.FLASHLIGHT -> FlashlightTool()
            ToolName.DIAL -> DialTool()
        }
    }
}

// Schemas only. With automaticToolCalling = false LiteRT-LM never calls these (Kotlin guide, "Manual
// Tool Calling"); if anything ever did, they throw instead of acting. Actions run only through
// LlmToolRouter → PermissionEngine → ToolExecutor. Descriptions match ToolName.description.

private fun inert(): Nothing =
    throw UnsupportedOperationException("S4 tool schemas are inert; actions run only through the Permission Engine")

private class OpenAppTool : ToolSet {
    @Tool(description = "Open an installed app by name or package name.")
    fun openApp(@ToolParam(description = "App name, e.g. maps or camera, or a package name") app: String): String = inert()
}

private class SetAlarmTool : ToolSet {
    @Tool(description = "Set an alarm at a time of day (24-hour clock).")
    fun setAlarm(
        @ToolParam(description = "Hour, 0 to 23") hour: Int,
        @ToolParam(description = "Minute, 0 to 59") minute: Int,
    ): String = inert()
}

private class StartTimerTool : ToolSet {
    @Tool(description = "Start a countdown timer.")
    fun startTimer(@ToolParam(description = "Length in seconds, 1 to 86400") seconds: Int): String = inert()
}

private class FlashlightTool : ToolSet {
    @Tool(description = "Turn the flashlight on or off.")
    fun flashlight(@ToolParam(description = "true to turn it on, false to turn it off") on: Boolean): String = inert()
}

private class DialTool : ToolSet {
    @Tool(description = "Open the dialer with a number prefilled; the user presses Call.")
    fun dial(@ToolParam(description = "Phone number: digits, optional leading +") number: String): String = inert()
}
