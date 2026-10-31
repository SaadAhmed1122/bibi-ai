package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.FakeInferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.domain.model.Message.Role.BIBI
import io.github.saadahmed1122.bibi.domain.model.Message.Role.USER
import io.github.saadahmed1122.bibi.domain.model.Message.Status.DONE
import io.github.saadahmed1122.bibi.domain.model.Message.Status.ERROR
import io.github.saadahmed1122.bibi.domain.model.Message.Status.STOPPED
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendMessageTest {
    private val history = FakeChatHistory()

    @Test
    fun doneSavesBothMessages() = runTest {
        val client = FakeInferenceClient().apply { enqueueReply("Hi", " there") }
        val events = SendMessage(client, history)("  Hello  ").toList()

        assertEquals(ReplyEvent.Done("Hi there"), events.last())
        assertEquals(listOf("Hello"), client.sentTexts)
        assertEquals(listOf(Triple(USER, "Hello", DONE), Triple(BIBI, "Hi there", DONE)), history.saved())
    }

    @Test
    fun stopSavesThePartialAnswerAsStopped() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 10).apply { enqueueReply("Once", " upon", " a", " time") }
        val job = launch { SendMessage(client, history)("Story").toList() }
        advanceTimeBy(25) // two tokens in
        client.stop()
        job.join()

        assertEquals(listOf(Triple(USER, "Story", DONE), Triple(BIBI, "Once upon", STOPPED)), history.saved())
    }

    @Test
    fun errorSavesTheUserMessageAndAnErrorEntry() = runTest {
        val client = FakeInferenceClient().apply { enqueueError("native crash", "Half") }
        SendMessage(client, history)("Hello").toList()

        assertEquals(listOf(Triple(USER, "Hello", DONE), Triple(BIBI, "Half", ERROR)), history.saved())
    }

    @Test
    fun aSecondSendWhileGeneratingIsIgnored() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 10).apply { enqueueReply("A", "B"); enqueueReply("never") }
        val send = SendMessage(client, history)
        val first = launch { send("one").toList() }
        runCurrent()
        val second = send("two").toList()
        first.join()

        assertTrue(second.isEmpty())
        assertEquals(listOf("one"), client.sentTexts)
        assertEquals(listOf(Triple(USER, "one", DONE), Triple(BIBI, "AB", DONE)), history.saved())
    }

    @Test
    fun blankTextIsIgnored() = runTest {
        val client = FakeInferenceClient()
        assertTrue(SendMessage(client, history)("   ").toList().isEmpty())
        assertTrue(history.saved().isEmpty())
    }

    @Test
    fun textIsCappedAtTheInputLimit() = runTest {
        val client = FakeInferenceClient().apply { enqueueReply("ok") }
        SendMessage(client, history)("x".repeat(MAX_INPUT_CHARS + 50)).toList()
        assertEquals(MAX_INPUT_CHARS, client.sentTexts.single().length)
    }

    @Test
    fun cancellingMidReplySavesThePartialAsStopped() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 10).apply { enqueueReply("Once", " upon", " a", " time") }
        val job = launch { SendMessage(client, history)("Story").toList() }
        advanceTimeBy(25)
        job.cancel()
        advanceUntilIdle()

        assertEquals(listOf(Triple(USER, "Story", DONE), Triple(BIBI, "Once upon", STOPPED)), history.saved())
    }
}
