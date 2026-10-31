package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.InferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Longest message the user can send (tasks/plan.md: 2,000 characters). */
const val MAX_INPUT_CHARS = 2_000

/**
 * One chat turn (SPEC-chat.md): saves the user message, streams Bibi's reply, and saves the reply
 * once with its final status. The partial reply is never written token by token
 * (SPEC-chat-history.md).
 *
 * Lives here rather than in `domain:usecase` because [InferenceClient] is in an Android module.
 */
class SendMessage(
    private val client: InferenceClient,
    private val history: ChatHistoryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val running = AtomicBoolean(false)

    /**
     * Streams the reply events for [text]. Blank text, or a send while another turn is running,
     * emits nothing and saves nothing. If the collector is cancelled mid-reply, the partial text
     * is saved as [Message.Status.STOPPED].
     */
    operator fun invoke(text: String): Flow<ReplyEvent> = flow {
        val message = text.trim().take(MAX_INPUT_CHARS)
        if (message.isEmpty() || !running.compareAndSet(false, true)) return@flow
        val partial = StringBuilder()
        var saved = false
        try {
            history.insert(Message(0, Message.Role.USER, message, clock(), Message.Status.DONE))
            client.send(message).collect { event ->
                when (event) {
                    is ReplyEvent.Token -> partial.append(event.text)
                    is ReplyEvent.Done -> { saveReply(event.fullText, Message.Status.DONE); saved = true }
                    is ReplyEvent.Stopped -> { saveReply(event.partialText, Message.Status.STOPPED); saved = true }
                    is ReplyEvent.Error -> { saveReply(event.partialText, Message.Status.ERROR); saved = true }
                }
                emit(event)
            }
        } finally {
            if (!saved && partial.isNotEmpty()) {
                withContext(NonCancellable) { saveReply(partial.toString(), Message.Status.STOPPED) }
            }
            running.set(false)
        }
    }

    private suspend fun saveReply(text: String, status: Message.Status) {
        history.insert(Message(0, Message.Role.BIBI, text, clock(), status))
    }
}
