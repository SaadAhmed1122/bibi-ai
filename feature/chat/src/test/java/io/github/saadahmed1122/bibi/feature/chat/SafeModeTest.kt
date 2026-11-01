package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.FakeInferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.RouteResult
import io.github.saadahmed1122.bibi.core.tools.ActionOutcome
import io.github.saadahmed1122.bibi.core.tools.ActionRunner
import io.github.saadahmed1122.bibi.core.tools.PermissionEngine
import io.github.saadahmed1122.bibi.core.tools.ToolAuditLog
import io.github.saadahmed1122.bibi.core.tools.ToolCall
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.NeverStore
import io.github.saadahmed1122.bibi.domain.usecase.Remember
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeModeTest {
    @get:Rule val tmp = TemporaryFolder()

    private val history = FakeChatHistory()
    private val memory = FakeMemory()
    private val model = MutableStateFlow<ModelState>(ModelState.Ready("/model"))
    private val safe = MutableStateFlow(true)
    private val executed = mutableListOf<ToolCall>()

    private object ActionWords : ActionTexts {
        override fun done(call: ToolCall, target: String) = "done"
        override fun failed(reason: String) = "failed"
        override fun denied(reason: String) = "denied"
        override fun cancelled() = "cancelled"
        override fun timedOut() = "timed out"
        override fun confirm() = "confirm?"
        override fun safeMode() = "safe mode: no actions"
    }

    private object MemoryWords : MemoryTexts {
        override fun saved() = "saved"
        override fun blocked(reason: NeverStore.Reason) = "blocked"
        override fun noticeSaved() = "notice"
        override fun safeMode() = "safe mode: not saved"
    }

    private fun TestScope.viewModel(client: FakeInferenceClient): ChatViewModel {
        val runner = ActionRunner(PermissionEngine(nowMs = { 0 }), ToolAuditLog(tmp.newFile()), { executed += it; ActionOutcome.Done("t") }, { false }, { !safe.value })
        return ChatViewModel(
            client, history, model,
            actions = ChatActions(PlanTurn(client) { null }::invoke, runner, ActionWords) { !safe.value },
            memory = ChatMemory(Remember(memory), MemoryWords) { !safe.value },
            safeMode = safe,
            setSafeMode = { safe.value = it },
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
        ).also { runCurrent() }
    }

    private fun TestScope.send(vm: ChatViewModel, text: String) {
        vm.onIntent(ChatIntent.Edit(text))
        runCurrent()
        vm.onIntent(ChatIntent.Send)
        advanceUntilIdle()
    }

    @Test
    fun aClearActionRequestGetsAnHonestNoAndNothingRuns() = runTest {
        val client = FakeInferenceClient().apply { routeResult = RouteResult.Label("flashlight", 0.9f) }
        val vm = viewModel(client)
        send(vm, "turn on the flashlight")
        assertTrue(executed.isEmpty())
        assertTrue(client.routedTexts.isEmpty())
        assertTrue(client.sentTexts.isEmpty())
        assertEquals("safe mode: no actions", vm.state.value.messages.last().text)
        assertTrue(vm.state.value.safeMode)
    }

    @Test
    fun anythingElseIsChatWithoutTheRouter() = runTest {
        val client = FakeInferenceClient().apply { routeResult = RouteResult.Label("flashlight", 0.9f); enqueueReply("Try a lamp.") }
        val vm = viewModel(client)
        send(vm, "it is dark in here, give me some light")
        assertTrue(executed.isEmpty())
        assertTrue(client.routedTexts.isEmpty())
        assertEquals(listOf("it is dark in here, give me some light"), client.sentTexts)
    }

    @Test
    fun rememberingIsRefusedBothWays() = runTest {
        val vm = viewModel(FakeInferenceClient())
        send(vm, "remember that my sister is Sara")
        assertEquals("safe mode: not saved", vm.state.value.messages.last().text)
        vm.onIntent(ChatIntent.RememberMessage("The gym opens at 6"))
        advanceTimeBy(100)
        assertEquals("safe mode: not saved", vm.state.value.notice)
        assertTrue(memory.items.isEmpty())
    }

    @Test
    fun turningItOffRestoresActions() = runTest {
        val vm = viewModel(FakeInferenceClient())
        vm.onIntent(ChatIntent.ToggleSafeMode)
        runCurrent()
        assertTrue(!safe.value)
        send(vm, "turn on the flashlight")
        assertEquals(listOf<ToolCall>(ToolCall.Flashlight(true)), executed)
    }

    @Test
    fun noSummariesInSafeMode() = runTest {
        repeat(4) {
            history.insert(Message(0, Message.Role.USER, "q$it", 0, Message.Status.DONE))
            history.insert(Message(0, Message.Role.BIBI, "a$it", 0, Message.Status.DONE))
        }
        val client = FakeInferenceClient().apply { summaryResult = "You asked things." }
        val s = SummaryScheduler(this, client, history, memory, { 0 }, { }, enabled = { !safe.value })
        assertEquals(SummaryScheduler.Result.Unavailable, s.runNow(force = true))
        assertTrue(client.summarized.isEmpty())
    }
}
