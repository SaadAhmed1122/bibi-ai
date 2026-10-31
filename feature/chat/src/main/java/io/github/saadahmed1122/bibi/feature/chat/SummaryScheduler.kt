package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.InferenceClient
import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import io.github.saadahmed1122.bibi.domain.usecase.MemoryRepository
import io.github.saadahmed1122.bibi.domain.usecase.NeverStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Automatic summaries (SPEC-memory.md): after [idleMs] with no new message and at least [minTurns]
 * new answers since the last summary, the model writes a one- or two-sentence summary that is saved
 * as a SUMMARY memory. Never during a reply: any new message cancels the wait, and the service
 * cancels a summary that is already running. Main thread.
 */
class SummaryScheduler(
    private val scope: CoroutineScope,
    private val client: InferenceClient,
    private val history: ChatHistoryRepository,
    private val memory: MemoryRepository,
    /** The id of the newest message already summarized; persisted by the caller. */
    private val lastSummarized: () -> Long,
    private val setLastSummarized: (Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idleMs: Long = IDLE_MS,
    private val minTurns: Int = MIN_TURNS,
) {
    sealed interface Result {
        data class Saved(val text: String) : Result
        data object NotEnough : Result
        data object Unavailable : Result
        data class Refused(val reason: NeverStore.Reason) : Result
    }

    private var waiting: Job? = null

    /** The user is sending: no summary now. */
    fun onUserActivity() {
        waiting?.cancel()
    }

    /** A turn finished: start (or restart) the idle wait. */
    fun onTurnDone() {
        waiting?.cancel()
        waiting = scope.launch {
            delay(idleMs)
            runNow()
        }
    }

    /** Summarizes the new turns now; [force] ignores [minTurns] (debug screen). */
    suspend fun runNow(force: Boolean = false): Result {
        val since = lastSummarized()
        val fresh = history.observeAll().first().filter { it.id > since && it.text.isNotBlank() }
        val answers = fresh.count { it.role == Message.Role.BIBI }
        if (fresh.isEmpty() || (!force && answers < minTurns)) return Result.NotEnough
        val summary = client.summarize(transcript(fresh)) ?: return Result.Unavailable
        // Done with these turns either way, so a refused summary isn't retried forever.
        setLastSummarized(fresh.maxOf { it.id })
        NeverStore.check(summary)?.let { return Result.Refused(it) }
        val text = summary.lines().joinToString(" ").trim().take(MAX_SUMMARY_CHARS)
        memory.add(MemoryItem.Kind.SUMMARY, text, clock())
        return Result.Saved(text)
    }

    /** The newest lines that fit, oldest first: "User: …" and "Bibi: …" ("You:" made the model mix up who is who). */
    private fun transcript(messages: List<Message>): String {
        val lines = ArrayDeque<String>()
        var used = 0
        for (m in messages.asReversed()) {
            val line = (if (m.role == Message.Role.USER) "User: " else "Bibi: ") + m.text.replace('\n', ' ').trim()
            if (used + line.length + 1 > MAX_TRANSCRIPT_CHARS) break
            lines.addFirst(line)
            used += line.length + 1
        }
        return lines.joinToString("\n")
    }

    companion object {
        const val IDLE_MS = 5 * 60_000L
        const val MIN_TURNS = 4

        /** Matches the service's cap. */
        const val MAX_TRANSCRIPT_CHARS = 1_800
        const val MAX_SUMMARY_CHARS = 300
    }
}
