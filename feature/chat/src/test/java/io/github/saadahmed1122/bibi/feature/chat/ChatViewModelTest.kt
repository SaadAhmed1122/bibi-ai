package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.EngineState
import io.github.saadahmed1122.bibi.ai.inference.api.FakeInferenceClient
import io.github.saadahmed1122.bibi.domain.model.Message.Role.BIBI
import io.github.saadahmed1122.bibi.domain.model.Message.Role.USER
import io.github.saadahmed1122.bibi.domain.model.Message.Status.DONE
import io.github.saadahmed1122.bibi.domain.model.Message.Status.STOPPED
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelTest {
    private val history = FakeChatHistory()
    private val model = MutableStateFlow<ModelState>(ModelState.Ready("/model"))

    /**
     * Not `backgroundScope`: `advanceUntilIdle` skips background work. A separate job on the test
     * scheduler keeps runTest from waiting on the ViewModel's endless flows.
     */
    private fun TestScope.viewModel(client: FakeInferenceClient) =
        ChatViewModel(client, history, model, scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()))
            .also { runCurrent() }

    @Test
    fun sendStreamsTokensThenShowsTheSavedAnswer() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 10).apply { enqueueReply("Hi", " there") }
        val vm = viewModel(client)
        vm.onIntent(ChatIntent.Edit("Hello"))
        runCurrent()
        assertTrue(vm.state.value.canSend)

        vm.onIntent(ChatIntent.Send)
        runCurrent()
        assertEquals("", vm.state.value.input)
        assertEquals("", vm.state.value.streamingText)
        assertFalse(vm.state.value.canSend)

        advanceTimeBy(15)
        assertEquals("Hi", vm.state.value.streamingText)

        advanceUntilIdle()
        val state = vm.state.value
        assertNull(state.streamingText)
        assertEquals(listOf(USER to "Hello", BIBI to "Hi there"), state.messages.map { it.role to it.text })
    }

    @Test
    fun stopKeepsThePartialAnswer() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 10).apply { enqueueReply("Once", " upon", " a", " time") }
        val vm = viewModel(client)
        vm.onIntent(ChatIntent.Edit("Story"))
        vm.onIntent(ChatIntent.Send)
        advanceTimeBy(25)
        vm.onIntent(ChatIntent.Stop)
        advanceUntilIdle()

        assertNull(vm.state.value.streamingText)
        assertEquals(Triple(BIBI, "Once upon", STOPPED), history.saved().last())
    }

    @Test
    fun clearDeletesHistoryAndResetsTheConversation() = runTest {
        val client = FakeInferenceClient().apply { enqueueReply("Hi") }
        val vm = viewModel(client)
        vm.onIntent(ChatIntent.Edit("Hello"))
        vm.onIntent(ChatIntent.Send)
        advanceUntilIdle()
        assertTrue(vm.state.value.canClear)

        vm.onIntent(ChatIntent.Clear)
        advanceUntilIdle()

        assertTrue(vm.state.value.messages.isEmpty())
        assertEquals(1, client.resetCount)
    }

    @Test
    fun clearIsIgnoredWhileGenerating() = runTest {
        val client = FakeInferenceClient(tokenDelayMillis = 10).apply { enqueueReply("A", "B") }
        val vm = viewModel(client)
        vm.onIntent(ChatIntent.Edit("Hello"))
        vm.onIntent(ChatIntent.Send)
        runCurrent()
        vm.onIntent(ChatIntent.Clear)
        advanceUntilIdle()

        assertEquals(0, client.resetCount)
        assertEquals(listOf(Triple(USER, "Hello", DONE), Triple(BIBI, "AB", DONE)), history.saved())
    }

    @Test
    fun missingModelShowsTheImportPromptAndBlocksSend() = runTest {
        model.value = ModelState.Missing
        val client = FakeInferenceClient()
        val vm = viewModel(client)
        vm.onIntent(ChatIntent.Edit("Hello"))
        vm.onIntent(ChatIntent.Send)
        advanceUntilIdle()

        assertEquals(ChatBanner.ImportModel, vm.state.value.banner)
        assertTrue(client.sentTexts.isEmpty())
        assertEquals("Hello", vm.state.value.input)
    }

    @Test
    fun bannerFollowsTheEngine() = runTest {
        val client = FakeInferenceClient(initialState = EngineState.Loading)
        val vm = viewModel(client)
        runCurrent()
        assertEquals(ChatBanner.Loading, vm.state.value.banner)

        client.setState(EngineState.Ready)
        runCurrent()
        assertNull(vm.state.value.banner)

        client.setState(EngineState.Loading) // e.g. the conversation reopening after Stop
        runCurrent()
        assertEquals(ChatBanner.Restarting, vm.state.value.banner)

        client.setState(EngineState.Failed("boom"))
        runCurrent()
        assertEquals(ChatBanner.Error, vm.state.value.banner)
    }

    @Test
    fun theCallerIsToldWhenTheModelComesAndGoes() = runTest {
        val calls = mutableListOf<Boolean>()
        ChatViewModel(
            FakeInferenceClient(), history, model, { calls += it },
            CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
        )
        runCurrent()
        model.value = ModelState.Missing // Remove
        runCurrent()
        model.value = ModelState.Importing(0.5f)
        runCurrent()
        model.value = ModelState.Ready("/model")
        runCurrent()
        assertEquals(listOf(true, false, true), calls)
    }

    @Test
    fun editIsCappedAtTheInputLimit() = runTest {
        val vm = viewModel(FakeInferenceClient())
        vm.onIntent(ChatIntent.Edit("x".repeat(MAX_INPUT_CHARS + 1)))
        runCurrent()
        assertEquals(MAX_INPUT_CHARS, vm.state.value.input.length)
    }
}
