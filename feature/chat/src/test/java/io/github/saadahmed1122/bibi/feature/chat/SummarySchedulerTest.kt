package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.FakeInferenceClient
import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.usecase.NeverStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarySchedulerTest {
    private val history = FakeChatHistory()
    private val memory = FakeMemory()
    private val client = FakeInferenceClient().apply { summaryResult = "You asked about sleep and said your sister is Sara." }
    private var last = 0L

    private fun TestScope.scheduler() =
        SummaryScheduler(this, client, history, memory, { last }, { last = it }, clock = { 5 }, idleMs = 1_000, minTurns = 2)

    private suspend fun turns(n: Int) = repeat(n) {
        history.insert(Message(0, Message.Role.USER, "question $it", 0, Message.Status.DONE))
        history.insert(Message(0, Message.Role.BIBI, "answer $it", 0, Message.Status.DONE))
    }

    @Test
    fun afterIdleWithEnoughTurnsASummaryIsSaved() = runTest {
        turns(2)
        val s = scheduler()
        s.onTurnDone()
        advanceTimeBy(1_100)
        assertEquals(listOf(MemoryItem.Kind.SUMMARY to "You asked about sleep and said your sister is Sara."), memory.items.map { it.kind to it.text })
        assertEquals("User: question 0\nBibi: answer 0\nUser: question 1\nBibi: answer 1", client.summarized.single())
        assertEquals(4L, last)
    }

    @Test
    fun aNewMessageCancelsTheWait() = runTest {
        turns(2)
        val s = scheduler()
        s.onTurnDone()
        advanceTimeBy(500)
        s.onUserActivity()
        advanceTimeBy(2_000)
        assertTrue(client.summarized.isEmpty())
    }

    @Test
    fun tooFewTurnsOrAlreadySummarizedDoNothing() = runTest {
        turns(1)
        assertEquals(SummaryScheduler.Result.NotEnough, scheduler().runNow())
        turns(1)
        val s = scheduler()
        s.runNow()
        assertEquals(SummaryScheduler.Result.NotEnough, s.runNow())
        assertEquals(1, memory.items.size)
    }

    @Test
    fun aBusyEngineSavesNothingAndKeepsTheTurnsForLater() = runTest {
        turns(2)
        client.summaryResult = null
        assertEquals(SummaryScheduler.Result.Unavailable, scheduler().runNow())
        assertEquals(0L, last)
        assertTrue(memory.items.isEmpty())
    }

    @Test
    fun aSummaryWithASecretIsNotSaved() = runTest {
        turns(2)
        client.summaryResult = "You said your PIN is 4821."
        assertEquals(SummaryScheduler.Result.Refused(NeverStore.Reason.PASSWORD), scheduler().runNow())
        assertTrue(memory.items.isEmpty())
        assertEquals(4L, last)
    }

    @Test
    fun theTranscriptKeepsTheNewestLinesUnderTheCap() = runTest {
        repeat(60) {
            history.insert(Message(0, Message.Role.USER, "message number $it with some words", 0, Message.Status.DONE))
            history.insert(Message(0, Message.Role.BIBI, "reply number $it with some words", 0, Message.Status.DONE))
        }
        scheduler().runNow()
        val t = client.summarized.single()
        assertTrue(t.length <= SummaryScheduler.MAX_TRANSCRIPT_CHARS)
        assertTrue(t.endsWith("Bibi: reply number 59 with some words"))
        runCurrent()
    }
}
