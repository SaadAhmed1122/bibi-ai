package io.github.saadahmed1122.bibi.ai.inference.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeInferenceClientTest {

    @Test
    fun streamsTokensThenDone() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 10)
        client.enqueueReply("Hel", "lo", "!")

        val events = client.send("hi").toList()

        assertEquals(
            listOf(
                ReplyEvent.Token("Hel"),
                ReplyEvent.Token("lo"),
                ReplyEvent.Token("!"),
                ReplyEvent.Done("Hello!"),
            ),
            events,
        )
        assertEquals(listOf("hi"), client.sentTexts)
        assertEquals(EngineState.Ready, client.state.value)
    }

    @Test
    fun stopMidStreamGivesStoppedWithPartialText() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 100)
        client.enqueueReply("a", "b", "c", "d")

        val events = async { client.send("hi").toList() }
        advanceTimeBy(250) // "a" at 100 ms and "b" at 200 ms are out
        runCurrent()
        assertEquals(EngineState.Generating, client.state.value)
        client.stop()

        assertEquals(
            listOf(ReplyEvent.Token("a"), ReplyEvent.Token("b"), ReplyEvent.Stopped("ab")),
            events.await(),
        )
        assertEquals(EngineState.Ready, client.state.value)
    }

    @Test
    fun scriptedErrorGivesErrorWithPartialText() = runTest {
        val client = FakeInferenceClient()
        client.enqueueError("engine crashed", "par", "tial")

        val events = client.send("hi").toList()

        assertEquals(
            listOf(
                ReplyEvent.Token("par"),
                ReplyEvent.Token("tial"),
                ReplyEvent.Error("engine crashed", "partial"),
            ),
            events,
        )
        assertEquals(EngineState.Ready, client.state.value)
    }

    @Test
    fun secondSendWhileGeneratingIsRejectedAsBusy() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 100)
        client.enqueueReply("a", "b")
        client.enqueueReply("never")

        val first = async { client.send("one").toList() }
        runCurrent()
        val second = client.send("two").toList()

        assertEquals(listOf(ReplyEvent.Error(InferenceClient.ERROR_BUSY, "")), second)
        assertEquals(
            listOf(ReplyEvent.Token("a"), ReplyEvent.Token("b"), ReplyEvent.Done("ab")),
            first.await(),
        )
        assertEquals(listOf("one"), client.sentTexts)
    }

    @Test
    fun sendWhenNotReadyIsRejected() = runTest {
        val client = FakeInferenceClient(initialState = EngineState.Loading)
        client.enqueueReply("a")

        assertEquals(
            listOf(ReplyEvent.Error(InferenceClient.ERROR_NOT_READY, "")),
            client.send("hi").toList(),
        )
    }

    @Test
    fun stopBeforeSendDoesNotAffectTheNextReply() = runTest {
        val client = FakeInferenceClient()
        client.enqueueReply("ok")
        client.stop()

        assertEquals(listOf(ReplyEvent.Token("ok"), ReplyEvent.Done("ok")), client.send("hi").toList())
    }

    @Test
    fun resetConversationIsCounted() {
        val client = FakeInferenceClient()
        client.resetConversation()
        assertEquals(1, client.resetCount)
    }
}
