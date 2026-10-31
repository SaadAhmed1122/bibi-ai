package io.github.saadahmed1122.bibi.ai.inference.api

import android.os.Bundle
import android.os.Message

/**
 * A Messenger message without Android types, so [InferenceProtocol] is testable on the JVM.
 * [toMessage] and [toEnvelope] are the only Android-facing parts.
 */
data class Envelope(val what: Int, val fields: Map<String, String>)

/** Every message that crosses the binder between the UI process and `:inference`. */
sealed interface ProtocolMessage {
    // Client -> service.
    /** The sender's `Message.replyTo` becomes the service's reply target. */
    data object Register : ProtocolMessage
    data class Load(val modelPath: String) : ProtocolMessage
    data class Send(val requestId: Long, val text: String) : ProtocolMessage
    data class Stop(val requestId: Long) : ProtocolMessage
    data object Reset : ProtocolMessage

    // Service -> client.
    data class State(val state: EngineState) : ProtocolMessage

    /**
     * One [ReplyEvent] for the send with [requestId]. The client drops replies whose id isn't the
     * current request, e.g. tokens still in flight from a stopped one.
     */
    data class Reply(val requestId: Long, val event: ReplyEvent) : ProtocolMessage
}

/** The Messenger protocol: `what` codes, bundle keys, and the codec (SPEC-inference.md). */
object InferenceProtocol {
    // Client -> service.
    const val MSG_REGISTER = 1
    const val MSG_LOAD = 2
    const val MSG_SEND = 3
    const val MSG_STOP = 4
    const val MSG_RESET = 5

    // Service -> client.
    const val MSG_STATE = 101
    const val MSG_TOKEN = 102
    const val MSG_DONE = 103
    const val MSG_STOPPED = 104
    const val MSG_ERROR = 105

    const val KEY_REQUEST_ID = "request_id"
    const val KEY_TEXT = "text"
    const val KEY_MODEL_PATH = "model_path"
    const val KEY_STATE = "state"
    const val KEY_REASON = "reason"

    fun encode(message: ProtocolMessage): Envelope = when (message) {
        ProtocolMessage.Register -> Envelope(MSG_REGISTER, emptyMap())
        is ProtocolMessage.Load -> Envelope(MSG_LOAD, mapOf(KEY_MODEL_PATH to message.modelPath))
        is ProtocolMessage.Send -> Envelope(MSG_SEND, mapOf(id(message.requestId), KEY_TEXT to message.text))
        is ProtocolMessage.Stop -> Envelope(MSG_STOP, mapOf(id(message.requestId)))
        ProtocolMessage.Reset -> Envelope(MSG_RESET, emptyMap())
        is ProtocolMessage.State -> Envelope(MSG_STATE, encodeState(message.state))
        is ProtocolMessage.Reply -> when (val e = message.event) {
            is ReplyEvent.Token -> Envelope(MSG_TOKEN, mapOf(id(message.requestId), KEY_TEXT to e.text))
            is ReplyEvent.Done -> Envelope(MSG_DONE, mapOf(id(message.requestId), KEY_TEXT to e.fullText))
            is ReplyEvent.Stopped -> Envelope(MSG_STOPPED, mapOf(id(message.requestId), KEY_TEXT to e.partialText))
            is ReplyEvent.Error -> Envelope(
                MSG_ERROR,
                mapOf(id(message.requestId), KEY_TEXT to e.partialText, KEY_REASON to e.reason),
            )
        }
    }

    /** Returns null for an unknown code or a missing or malformed field. */
    fun decode(envelope: Envelope): ProtocolMessage? {
        val f = envelope.fields
        val requestId = f[KEY_REQUEST_ID]?.toLongOrNull()
        val text = f[KEY_TEXT]
        fun reply(event: (String) -> ReplyEvent): ProtocolMessage? =
            if (requestId != null && text != null) ProtocolMessage.Reply(requestId, event(text)) else null
        return when (envelope.what) {
            MSG_REGISTER -> ProtocolMessage.Register
            MSG_LOAD -> f[KEY_MODEL_PATH]?.let { ProtocolMessage.Load(it) }
            MSG_SEND -> if (requestId != null && text != null) ProtocolMessage.Send(requestId, text) else null
            MSG_STOP -> requestId?.let { ProtocolMessage.Stop(it) }
            MSG_RESET -> ProtocolMessage.Reset
            MSG_STATE -> decodeState(f)?.let { ProtocolMessage.State(it) }
            MSG_TOKEN -> reply { ReplyEvent.Token(it) }
            MSG_DONE -> reply { ReplyEvent.Done(it) }
            MSG_STOPPED -> reply { ReplyEvent.Stopped(it) }
            MSG_ERROR -> f[KEY_REASON]?.let { reason -> reply { ReplyEvent.Error(reason, it) } }
            else -> null
        }
    }

    private fun id(requestId: Long) = KEY_REQUEST_ID to requestId.toString()

    private fun encodeState(state: EngineState): Map<String, String> = when (state) {
        EngineState.Unbound -> mapOf(KEY_STATE to "unbound")
        EngineState.Loading -> mapOf(KEY_STATE to "loading")
        EngineState.Ready -> mapOf(KEY_STATE to "ready")
        EngineState.Generating -> mapOf(KEY_STATE to "generating")
        is EngineState.Failed -> mapOf(KEY_STATE to "failed", KEY_REASON to state.reason)
    }

    private fun decodeState(f: Map<String, String>): EngineState? = when (f[KEY_STATE]) {
        "unbound" -> EngineState.Unbound
        "loading" -> EngineState.Loading
        "ready" -> EngineState.Ready
        "generating" -> EngineState.Generating
        "failed" -> f[KEY_REASON]?.let { EngineState.Failed(it) }
        else -> null
    }
}

/** The caller sets `replyTo` on [ProtocolMessage.Register] messages. */
fun Envelope.toMessage(): Message = Message.obtain(null, what).also { msg ->
    msg.data = Bundle(fields.size).apply { fields.forEach { (k, v) -> putString(k, v) } }
}

fun Message.toEnvelope(): Envelope {
    val bundle = peekData() ?: return Envelope(what, emptyMap())
    val fields = buildMap {
        for (key in bundle.keySet()) bundle.getString(key)?.let { put(key, it) }
    }
    return Envelope(what, fields)
}
