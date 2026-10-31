package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.FakeInferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.RouteResult
import io.github.saadahmed1122.bibi.core.tools.ActionOutcome
import io.github.saadahmed1122.bibi.core.tools.ActionRunner
import io.github.saadahmed1122.bibi.core.tools.PermissionEngine
import io.github.saadahmed1122.bibi.core.tools.ToolAuditLog
import io.github.saadahmed1122.bibi.core.tools.ToolCall
import io.github.saadahmed1122.bibi.domain.model.Message.Role.BIBI
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatActionsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val history = FakeChatHistory()
    private val model = MutableStateFlow<ModelState>(ModelState.Ready("/model"))
    private val executed = mutableListOf<ToolCall>()

    private object Texts : ActionTexts {
        override fun done(call: ToolCall, target: String) = "done ${call.tool.wire} $target"
        override fun failed(reason: String) = "failed $reason"
        override fun denied(reason: String) = "denied $reason"
        override fun cancelled() = "cancelled"
        override fun timedOut() = "timed out"
        override fun confirm() = "confirm?"
    }

    private fun TestScope.viewModel(client: FakeInferenceClient): ChatViewModel {
        val runner = ActionRunner(
            engine = PermissionEngine(nowMs = { testScheduler.currentTime }),
            audit = ToolAuditLog(tmp.newFile()),
            execute = { executed += it; ActionOutcome.Done("target") },
            deviceLocked = { false },
        )
        val actions = ChatActions(PlanTurn(client) { null }::invoke, runner, Texts)
        return ChatViewModel(
            client, history, model, actions = actions,
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
        ).also { runCurrent() }
    }

    private fun TestScope.send(vm: ChatViewModel, text: String) {
        vm.onIntent(ChatIntent.Edit(text))
        runCurrent()
        vm.onIntent(ChatIntent.Send)
        runCurrent()
    }

    private fun ChatViewModel.texts() = state.value.messages.map { it.role to it.text }

    @Test
    fun aShortcutPhraseRunsWithoutTheRouterOrTheModel() = runTest {
        val client = FakeInferenceClient()
        val vm = viewModel(client)
        send(vm, "set a timer for 5 minutes")
        advanceUntilIdle()
        assertEquals(listOf<ToolCall>(ToolCall.StartTimer(300)), executed)
        assertTrue(client.routedTexts.isEmpty())
        assertTrue(client.sentTexts.isEmpty())
        assertEquals(listOf(USER to "set a timer for 5 minutes", BIBI to "done start_timer target"), vm.texts())
    }

    @Test
    fun aRoutedRequestRunsTheToolTheRouterChose() = runTest {
        val client = FakeInferenceClient().apply { routeResult = RouteResult.Label("flashlight", 0.9f) }
        val vm = viewModel(client)
        send(vm, "it's too dark in here, give me some light")
        advanceUntilIdle()
        assertEquals(listOf<ToolCall>(ToolCall.Flashlight(true)), executed)
        assertTrue(client.sentTexts.isEmpty())
    }

    @Test
    fun aToolWithAMissingValueAsksInsteadOfGuessing() = runTest {
        val client = FakeInferenceClient().apply { routeResult = RouteResult.Label("set_alarm", 0.9f) }
        val vm = viewModel(client)
        send(vm, "I need an alarm")
        advanceUntilIdle()
        assertTrue(executed.isEmpty())
        assertTrue(client.sentTexts.isEmpty())
        assertEquals(2, vm.texts().size)
        assertEquals(BIBI, vm.texts().last().first)
    }

    @Test
    fun aQuestionThatDoesNotNameTheToolIsChatNotAQuestionBack() = runTest {
        // On the vivo, "how do I get better sleep" went to set_alarm and got "What time should the alarm ring?".
        for (text in listOf("How do I get better sleep", "What is a good way to start the morning")) {
            val client = FakeInferenceClient().apply { routeResult = RouteResult.Label("set_alarm", 0.83f); enqueueReply("Some tips.") }
            val vm = viewModel(client)
            send(vm, text)
            advanceUntilIdle()
            assertEquals(listOf(text), client.sentTexts)
            assertEquals(BIBI to "Some tips.", vm.texts().last())
            history.deleteAll()
        }
        assertTrue(executed.isEmpty())
    }

    @Test
    fun noneALowScoreOrNoRouterMeanChat() = runTest {
        for (route in listOf(RouteResult.Label("none", 0.9f), RouteResult.Label("flashlight", 0.1f), RouteResult.Unavailable("router not loaded"))) {
            val client = FakeInferenceClient().apply { routeResult = route; enqueueReply("Hi!") }
            val vm = viewModel(client)
            send(vm, "how are you today")
            advanceUntilIdle()
            assertEquals(listOf("how are you today"), client.sentTexts)
            assertEquals(BIBI to "Hi!", vm.texts().last())
            history.deleteAll()
        }
        assertTrue(executed.isEmpty())
    }

    @Test
    fun dialWaitsForConfirm() = runTest {
        val vm = viewModel(FakeInferenceClient())
        send(vm, "call 0300 1234567")
        advanceTimeBy(1_000)
        assertTrue(executed.isEmpty())
        assertTrue(vm.state.value.pendingAction!!.contains("03001234567"))
        vm.onIntent(ChatIntent.Edit("hello"))
        runCurrent()
        assertFalse(vm.state.value.canSend)

        vm.onIntent(ChatIntent.ConfirmAction)
        advanceTimeBy(1_000)
        assertEquals(listOf<ToolCall>(ToolCall.Dial("0300 1234567")), executed)
        assertNull(vm.state.value.pendingAction)
        assertEquals(BIBI to "done dial target", vm.texts().last())
    }

    @Test
    fun cancelRunsNothing() = runTest {
        val vm = viewModel(FakeInferenceClient())
        send(vm, "call 0300 1234567")
        advanceTimeBy(1_000)
        vm.onIntent(ChatIntent.CancelAction)
        advanceTimeBy(1_000)
        assertTrue(executed.isEmpty())
        assertNull(vm.state.value.pendingAction)
        assertEquals(BIBI to "cancelled", vm.texts().last())
    }

    @Test
    fun silenceCancelsAfterTheTimeout() = runTest {
        val vm = viewModel(FakeInferenceClient())
        send(vm, "call 0300 1234567")
        advanceTimeBy(PermissionEngine.DEFAULT_TIMEOUT_MS + 1_000)
        assertTrue(executed.isEmpty())
        assertNull(vm.state.value.pendingAction)
        assertEquals(BIBI to "timed out", vm.texts().last())
        // A late Confirm does nothing.
        vm.onIntent(ChatIntent.ConfirmAction)
        advanceUntilIdle()
        assertTrue(executed.isEmpty())
    }
}
