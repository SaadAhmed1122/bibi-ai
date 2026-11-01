package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.FakeInferenceClient
import io.github.saadahmed1122.bibi.domain.model.Message.Role.USER
import io.github.saadahmed1122.bibi.domain.model.ModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatVoiceTest {
    private val history = FakeChatHistory()
    private val model = MutableStateFlow<ModelState>(ModelState.Ready("/model"))
    private val ready = MutableStateFlow(true)
    private val aloud = MutableStateFlow(false)

    /** Delivers [samples] when started, like a recorder that heard something. */
    private class FakeAudio(private val samples: FloatArray = FloatArray(20_000) { 0.1f }) : AudioSource {
        var starts = 0
        var stops = 0
        override fun start(onChunk: (FloatArray) -> Unit): Boolean {
            starts++
            onChunk(samples)
            return true
        }
        override fun stop() {
            stops++
        }
    }

    private fun TestScope.viewModel(client: FakeInferenceClient, audio: AudioSource = FakeAudio()) = ChatViewModel(
        client, history, model,
        voice = ChatVoice(audio, ready, aloud, { aloud.value = it }, notHeard = "not heard"),
        scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
    ).also { runCurrent() }

    @Test
    fun holdToTalkSendsWhatWasHeard() = runTest {
        val client = FakeInferenceClient().apply { transcriptResult = "set a timer for ten minutes"; enqueueReply("Okay.") }
        val audio = FakeAudio()
        val vm = viewModel(client, audio)
        assertTrue(vm.state.value.canTalk)
        vm.onIntent(ChatIntent.MicDown)
        runCurrent()
        assertTrue(vm.state.value.listening)
        vm.onIntent(ChatIntent.MicUp)
        advanceUntilIdle()
        assertFalse(vm.state.value.listening)
        // 20,000 samples arrive as 8,000 + 8,000 + 4,000.
        assertEquals(listOf(8_000, 8_000, 4_000), client.heard.map { it.size })
        assertEquals(1, audio.stops)
        assertEquals(USER to "set a timer for ten minutes", vm.state.value.messages.first().let { it.role to it.text })
    }

    @Test
    fun nothingHeardShowsANoticeAndSendsNothing() = runTest {
        val client = FakeInferenceClient().apply { transcriptResult = "" }
        val vm = viewModel(client)
        vm.onIntent(ChatIntent.MicDown)
        vm.onIntent(ChatIntent.MicUp)
        advanceTimeBy(100)
        assertEquals("not heard", vm.state.value.notice)
        assertTrue(client.sentTexts.isEmpty())
    }

    @Test
    fun theMicIsOffWhileBibiWritesOrWithoutAVoicePack() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 1_000).apply { enqueueReply("A", "B") }
        val audio = FakeAudio()
        val vm = viewModel(client, audio)
        vm.onIntent(ChatIntent.Edit("hi"))
        runCurrent()
        vm.onIntent(ChatIntent.Send)
        runCurrent()
        vm.onIntent(ChatIntent.MicDown)
        assertEquals(0, audio.starts)
        advanceUntilIdle()
        ready.value = false
        runCurrent()
        vm.onIntent(ChatIntent.MicDown)
        assertEquals(0, audio.starts)
    }

    @Test
    fun readAloudSpeaksEachSentenceInOrder() = runTest {
        aloud.value = true
        val client = FakeInferenceClient().apply { enqueueReply("Foxes are *clever*. ", "They hunt", " at night!", " **") }
        val vm = viewModel(client)
        vm.onIntent(ChatIntent.Edit("tell me about foxes"))
        runCurrent()
        vm.onIntent(ChatIntent.Send)
        advanceUntilIdle()
        assertEquals(listOf("Foxes are clever.", "They hunt at night!"), client.spokenTexts)
    }

    @Test
    fun anActionReplyIsSpokenWhole() = runTest {
        aloud.value = true
        val client = FakeInferenceClient()
        val vm = ChatViewModel(
            client, history, model,
            memory = ChatMemory(
                io.github.saadahmed1122.bibi.domain.usecase.Remember(FakeMemory()),
                object : MemoryTexts {
                    override fun saved() = "Got it."
                    override fun blocked(reason: io.github.saadahmed1122.bibi.domain.usecase.NeverStore.Reason) = "no"
                    override fun noticeSaved() = "saved"
                },
            ),
            voice = ChatVoice(FakeAudio(), ready, aloud, { aloud.value = it }, "not heard"),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
        )
        runCurrent()
        vm.onIntent(ChatIntent.Edit("remember that my sister is Sara"))
        runCurrent()
        vm.onIntent(ChatIntent.Send)
        advanceUntilIdle()
        assertEquals(listOf("Got it."), client.spokenTexts)
    }

    @Test
    fun stopAndTurningReadAloudOffSilenceBibi() = runTest {
        aloud.value = true
        val client = FakeInferenceClient(tokenDelayMillis = 10).apply { enqueueReply("One. ", "Two. ", "Three.") }
        val vm = viewModel(client)
        vm.onIntent(ChatIntent.Edit("count"))
        runCurrent()
        vm.onIntent(ChatIntent.Send)
        advanceTimeBy(15)
        vm.onIntent(ChatIntent.Stop)
        advanceUntilIdle()
        assertTrue(client.stopSpeakingCount >= 1)
        vm.onIntent(ChatIntent.ToggleSpeaker)
        runCurrent()
        assertFalse(aloud.value)
    }
}
