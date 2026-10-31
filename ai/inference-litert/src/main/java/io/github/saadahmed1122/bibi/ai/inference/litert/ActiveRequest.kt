package io.github.saadahmed1122.bibi.ai.inference.litert

import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent

/**
 * The client's one in-flight send. Routes replies by request id: events for any other id (e.g.
 * tokens still in flight from a stopped request) are dropped. Thread-safe; the sink is called
 * under the lock, so it must not block.
 */
internal class ActiveRequest {
    private var id: Long? = null
    private var sink: ((ReplyEvent) -> Unit)? = null
    private val partial = StringBuilder()

    /** False if a request is already active. */
    @Synchronized
    fun start(requestId: Long, sink: (ReplyEvent) -> Unit): Boolean {
        if (id != null) return false
        id = requestId
        this.sink = sink
        partial.setLength(0)
        return true
    }

    @Synchronized
    fun currentId(): Long? = id

    /** Returns false if [event] was dropped. A terminal event ends the request. */
    @Synchronized
    fun deliver(requestId: Long, event: ReplyEvent): Boolean {
        if (requestId != id) return false
        if (event is ReplyEvent.Token) partial.append(event.text)
        val target = sink
        if (event !is ReplyEvent.Token) clear(requestId)
        target?.invoke(event)
        return true
    }

    /** Ends the active request, if any, with an error carrying the text so far. */
    @Synchronized
    fun fail(reason: String) {
        val requestId = id ?: return
        deliver(requestId, ReplyEvent.Error(reason, partial.toString()))
    }

    /** Forgets [requestId] without an event, e.g. when its collector went away. */
    @Synchronized
    fun clear(requestId: Long) {
        if (requestId != id) return
        id = null
        sink = null
    }
}
