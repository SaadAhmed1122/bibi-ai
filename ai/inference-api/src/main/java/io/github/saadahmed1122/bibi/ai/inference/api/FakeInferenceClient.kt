package io.github.saadahmed1122.bibi.ai.inference.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Scripted [InferenceClient] for tests. Each [send] takes the next scripted reply in order; with
 * none left it emits `Error("no scripted reply", "")`. [stop] takes effect at the next token
 * boundary, like the real engine's cancel.
 */
class FakeInferenceClient(
    initialState: EngineState = EngineState.Ready,
    private val tokenDelayMillis: Long = 0,
) : InferenceClient {

    private class Script(val tokens: List<String>, val errorReason: String?)

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val scripts = ArrayDeque<Script>()

    @Volatile private var stopRequested = false

    /** Texts of the sends that started a generation, in order. */
    val sentTexts = mutableListOf<String>()

    var resetCount = 0
        private set

    /** What [route] answers; the texts it was asked about go to [routedTexts]. */
    var routeResult: RouteResult = RouteResult.Unavailable("no router in the fake")
    val routedTexts = mutableListOf<String>()

    fun enqueueReply(vararg tokens: String) {
        scripts.addLast(Script(tokens.toList(), errorReason = null))
    }

    /** The reply streams [tokensBefore], then fails with [reason]. */
    fun enqueueError(reason: String, vararg tokensBefore: String) {
        scripts.addLast(Script(tokensBefore.toList(), reason))
    }

    /** Sets the state, e.g. to simulate a load or a crash. */
    fun setState(state: EngineState) {
        _state.value = state
    }

    override fun send(text: String): Flow<ReplyEvent> = flow {
        if (!_state.compareAndSet(EngineState.Ready, EngineState.Generating)) {
            val busy = _state.value == EngineState.Generating
            emit(ReplyEvent.Error(if (busy) InferenceClient.ERROR_BUSY else InferenceClient.ERROR_NOT_READY, ""))
            return@flow
        }
        try {
            stopRequested = false
            sentTexts += text
            val script = scripts.removeFirstOrNull() ?: Script(emptyList(), "no scripted reply")
            val partial = StringBuilder()
            for (token in script.tokens) {
                if (tokenDelayMillis > 0) delay(tokenDelayMillis)
                if (stopRequested) {
                    emit(ReplyEvent.Stopped(partial.toString()))
                    return@flow
                }
                partial.append(token)
                emit(ReplyEvent.Token(token))
            }
            emit(
                if (script.errorReason != null) {
                    ReplyEvent.Error(script.errorReason, partial.toString())
                } else {
                    ReplyEvent.Done(partial.toString())
                },
            )
        } finally {
            _state.compareAndSet(EngineState.Generating, EngineState.Ready)
        }
    }

    override fun stop() {
        if (_state.value == EngineState.Generating) stopRequested = true
    }

    override fun resetConversation() {
        resetCount++
    }

    /** What [summarize] answers; the transcripts it got go to [summarized]. */
    var summaryResult: String? = null
    val summarized = mutableListOf<String>()

    override suspend fun summarize(transcript: String): String? {
        summarized += transcript
        return summaryResult
    }

    override suspend fun route(text: String): RouteResult {
        routedTexts += text
        return routeResult
    }
}
