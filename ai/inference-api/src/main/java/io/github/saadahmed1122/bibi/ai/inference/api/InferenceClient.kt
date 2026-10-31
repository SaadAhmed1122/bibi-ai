package io.github.saadahmed1122.bibi.ai.inference.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The UI process's view of the on-device LLM (SPEC-inference.md). One conversation, one
 * generation at a time.
 */
interface InferenceClient {
    val state: StateFlow<EngineState>

    /**
     * Streams the reply to [text]: zero or more [ReplyEvent.Token], then exactly one terminal
     * event ([ReplyEvent.Done], [ReplyEvent.Stopped] or [ReplyEvent.Error]), then completes.
     *
     * Only one generation runs at a time. A send while another is generating is not queued: its
     * flow emits `Error(ERROR_BUSY, "")` and completes. A send when the engine isn't [EngineState.Ready]
     * emits `Error(ERROR_NOT_READY, "")`.
     */
    fun send(text: String): Flow<ReplyEvent>

    /** Stops the current generation; its flow ends with [ReplyEvent.Stopped]. No-op when idle. */
    fun stop()

    /** Starts a fresh conversation (system prompt and primer only). */
    fun resetConversation()

    /**
     * Asks the action router which tool [text] is for. Never throws; [RouteResult.Unavailable] when
     * the router isn't loaded or `:inference` isn't connected. It never runs an action (SPEC-tools.md).
     */
    suspend fun route(text: String): RouteResult

    companion object {
        const val ERROR_BUSY = "busy"
        const val ERROR_NOT_READY = "not ready"
    }
}

sealed interface EngineState {
    data object Unbound : EngineState
    data object Loading : EngineState
    data object Ready : EngineState
    data object Generating : EngineState
    data class Failed(val reason: String) : EngineState
}

sealed interface RouteResult {
    /** [label] is a tool wire name or "none"; [confidence] as the Decision Maker reports it. */
    data class Label(val label: String, val confidence: Float) : RouteResult
    data class Unavailable(val reason: String) : RouteResult
}

sealed interface ReplyEvent {
    data class Token(val text: String) : ReplyEvent
    data class Done(val fullText: String) : ReplyEvent
    data class Stopped(val partialText: String) : ReplyEvent
    data class Error(val reason: String, val partialText: String) : ReplyEvent
}
