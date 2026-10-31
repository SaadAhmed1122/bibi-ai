package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.FakeInferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.RouteResult
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.tools.ActionOutcome
import io.github.saadahmed1122.bibi.core.tools.ActionRunner
import io.github.saadahmed1122.bibi.core.tools.PermissionEngine
import io.github.saadahmed1122.bibi.core.tools.ToolAuditLog
import io.github.saadahmed1122.bibi.core.tools.ToolCall
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatMoodTest {
    @get:Rule val tmp = TemporaryFolder()

    private val history = FakeChatHistory()
    private val model = MutableStateFlow<ModelState>(ModelState.Ready("/model"))
    private val reported = mutableListOf<BibiMood>()

    private object Texts : ActionTexts {
        override fun done(call: ToolCall, target: String) = "done"
        override fun failed(reason: String) = "failed"
        override fun denied(reason: String) = "denied"
        override fun cancelled() = "cancelled"
        override fun timedOut() = "timed out"
        override fun confirm() = "confirm?"
    }

    private fun TestScope.viewModel(client: FakeInferenceClient, outcome: ActionOutcome = ActionOutcome.Done("t")): ChatViewModel {
        val runner = ActionRunner(PermissionEngine(nowMs = { testScheduler.currentTime }), ToolAuditLog(tmp.newFile()), { outcome }, { false })
        return ChatViewModel(
            client, history, model,
            actions = ChatActions(PlanTurn(client) { null }::invoke, runner, Texts),
            onMood = { reported += it },
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
        ).also { runCurrent() }
    }

    private fun TestScope.send(vm: ChatViewModel, text: String) {
        vm.onIntent(ChatIntent.Edit(text))
        runCurrent()
        vm.onIntent(ChatIntent.Send)
    }

    @Test
    fun thinkingThenSpeakingThenIdle() = runTest {
        val vm = viewModel(FakeInferenceClient(tokenDelayMillis = 100).apply { enqueueReply("Hi", " there") })
        send(vm, "how are you")
        runCurrent()
        assertEquals(BibiMood.THINKING, vm.mood.value)
        advanceTimeBy(150)
        assertEquals(BibiMood.SPEAKING, vm.mood.value)
        advanceUntilIdle()
        assertEquals(BibiMood.IDLE, vm.mood.value)
        assertEquals(listOf(BibiMood.IDLE, BibiMood.THINKING, BibiMood.SPEAKING, BibiMood.IDLE), reported)
    }

    @Test
    fun anErrorIsConfusedForThreeSeconds() = runTest {
        val vm = viewModel(FakeInferenceClient().apply { enqueueError("engine crashed", "Hel") })
        send(vm, "how are you")
        advanceTimeBy(100)
        assertEquals(BibiMood.CONFUSED, vm.mood.value)
        advanceTimeBy(3_000)
        assertEquals(BibiMood.IDLE, vm.mood.value)
    }

    @Test
    fun aQuestionBackOrAFailedActionIsConfused() = runTest {
        val asking = viewModel(FakeInferenceClient().apply { routeResult = RouteResult.Label("set_alarm", 0.9f) })
        send(asking, "set an alarm")
        advanceTimeBy(100)
        assertEquals(BibiMood.CONFUSED, asking.mood.value)

        val failing = viewModel(FakeInferenceClient(), outcome = ActionOutcome.Failed("no app called \"light\""))
        send(failing, "open spotify")
        advanceTimeBy(100)
        assertEquals(BibiMood.CONFUSED, failing.mood.value)
    }

    @Test
    fun theStrongestMoodWinsAcrossChats() {
        assertEquals(BibiMood.SPEAKING, BibiMood.strongest(listOf(BibiMood.IDLE, BibiMood.SPEAKING)))
        assertEquals(BibiMood.IDLE, BibiMood.strongest(emptyList()))
    }
}
