package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.EngineState
import io.github.saadahmed1122.bibi.ai.inference.api.FakeInferenceClient
import io.github.saadahmed1122.bibi.domain.model.ModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkModeTest {
    private val history = FakeChatHistory()
    private val model = MutableStateFlow<ModelState>(ModelState.Ready("/model"))
    private val ready = MutableStateFlow(true)
    private val aloud = MutableStateFlow(false)

    /** 0.1 s pieces at a constant loudness. */
    private fun tenths(level: Float, n: Int) = List(n) { FloatArray(1_600) { i -> if (i % 2 == 0) level else -level } }

    private val utterance = tenths(0.004f, 3) + tenths(0.05f, 8) + tenths(0.004f, 10)
    private val silence = tenths(0.004f, 80)

    /**
     * Each start plays the next take; [onStart] runs first (the test sets what the recognizer will
     * "hear" there). The recorder delivers synchronously, like a recorder that heard all of it at once.
     */
    private class ScriptedAudio(private val takes: List<List<FloatArray>>, private val onStart: (Int) -> Unit = {}) : AudioSource {
        var starts = 0
        var stops = 0
        var running = false
        override fun start(onChunk: (FloatArray) -> Unit): Boolean {
            val take = takes.getOrNull(starts) ?: emptyList()
            onStart(starts)
            starts++
            running = true
            take.forEach(onChunk)
            return true
        }
        override fun stop() {
            if (running) stops++
            running = false
        }
    }

    private fun TestScope.viewModel(client: FakeInferenceClient, audio: AudioSource) = ChatViewModel(
        client, history, model,
        voice = ChatVoice(audio, ready, aloud, { aloud.value = it }, notHeard = "not heard"),
        scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
    ).also { runCurrent() }

    @Test
    fun aTapStartsAConversationThatAnswersAloudAndListensAgain() = runTest {
        val client = FakeInferenceClient().apply { enqueueReply("It's noon.") }
        val said = listOf("what time is it", "bye")
        val audio = ScriptedAudio(listOf(utterance, utterance)) { client.transcriptResult = said[it] }
        val vm = viewModel(client, audio)
        vm.onIntent(ChatIntent.StartTalk)
        advanceUntilIdle()
        assertEquals(listOf("what time is it"), client.sentTexts)
        // Spoken even though "Read aloud" is off: talking is spoken by nature.
        assertEquals(listOf("It's noon."), client.spokenTexts)
        assertEquals(2, audio.starts)
        // "bye" ended it; nothing was sent for it.
        assertNull(vm.state.value.talk)
        assertEquals(2, audio.stops)
        // The speech, with a little audio from before it started, reached the recognizer.
        assertTrue(client.heard.isNotEmpty())
    }

    @Test
    fun eightQuietSecondsEndTheConversation() = runTest {
        val client = FakeInferenceClient()
        val audio = ScriptedAudio(listOf(silence))
        val vm = viewModel(client, audio)
        vm.onIntent(ChatIntent.StartTalk)
        advanceUntilIdle()
        assertNull(vm.state.value.talk)
        assertTrue(client.sentTexts.isEmpty())
        assertTrue(client.heard.isEmpty())
    }

    @Test
    fun sayingOpenChatAsksForTheChatAndEnds() = runTest {
        val client = FakeInferenceClient().apply { transcriptResult = "Open chat" }
        val vm = viewModel(client, ScriptedAudio(listOf(utterance)))
        var opened = 0
        backgroundScope.launch { vm.openChat.collect { opened++ } }
        vm.onIntent(ChatIntent.StartTalk)
        advanceUntilIdle()
        runCurrent() // advanceUntilIdle doesn't wait for backgroundScope
        assertEquals(1, opened)
        assertNull(vm.state.value.talk)
        assertTrue(client.sentTexts.isEmpty())
    }

    @Test
    fun withoutAVoicePackATapOpensTheChat() = runTest {
        ready.value = false
        val audio = ScriptedAudio(listOf(utterance))
        val vm = viewModel(FakeInferenceClient(), audio)
        var opened = 0
        backgroundScope.launch { vm.openChat.collect { opened++ } }
        vm.onIntent(ChatIntent.StartTalk)
        advanceUntilIdle()
        runCurrent() // advanceUntilIdle doesn't wait for backgroundScope
        assertEquals(1, opened)
        assertEquals(0, audio.starts)
    }

    @Test
    fun whileTheModelLoadsBibiListensAndAnswersOnceReady() = runTest {
        val client = FakeInferenceClient(initialState = EngineState.Loading).apply {
            enqueueReply("Paris.")
        }
        val said = listOf("what is the capital of france", "bye")
        val audio = ScriptedAudio(listOf(utterance, utterance)) { client.transcriptResult = said[it] }
        val vm = viewModel(client, audio)
        var opened = 0
        backgroundScope.launch { vm.openChat.collect { opened++ } }
        vm.onIntent(ChatIntent.StartTalk)
        advanceUntilIdle()
        // Heard, but waiting for the model: nothing sent yet, and the chat didn't open.
        assertTrue(client.sentTexts.isEmpty())
        assertEquals(TalkPhase.THINKING, vm.state.value.talk)
        client.setState(EngineState.Ready)
        advanceUntilIdle()
        runCurrent()
        assertEquals(listOf("what is the capital of france"), client.sentTexts)
        assertEquals(listOf("Paris."), client.spokenTexts)
        assertEquals(0, opened)
        assertNull(vm.state.value.talk)
    }

    @Test
    fun stopTalkTurnsTheMicOff() = runTest {
        // A recorder that hasn't heard anything yet keeps the conversation waiting.
        val audio = ScriptedAudio(listOf(tenths(0.004f, 5)))
        val vm = viewModel(FakeInferenceClient(), audio)
        vm.onIntent(ChatIntent.StartTalk)
        runCurrent()
        assertEquals(TalkPhase.LISTENING, vm.state.value.talk)
        assertTrue(!vm.state.value.canTalk)
        vm.onIntent(ChatIntent.StopTalk)
        advanceUntilIdle()
        assertNull(vm.state.value.talk)
        assertEquals(1, audio.stops)
    }
}
