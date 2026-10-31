package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.ai.inference.api.FakeInferenceClient
import io.github.saadahmed1122.bibi.domain.model.Message.Role.BIBI
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.NeverStore
import io.github.saadahmed1122.bibi.domain.usecase.Recall
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMemoryTest {
    private val history = FakeChatHistory()
    private val memory = FakeMemory()
    private val model = MutableStateFlow<ModelState>(ModelState.Ready("/model"))

    private object Texts : MemoryTexts {
        override fun saved() = "saved"
        override fun blocked(reason: NeverStore.Reason) = "blocked $reason"
        override fun noticeSaved() = "notice saved"
    }

    private fun TestScope.viewModel(client: FakeInferenceClient) = ChatViewModel(
        client, history, model,
        memory = ChatMemory(Remember(memory) { 7 }, Texts, Recall(memory)),
        scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
    ).also { runCurrent() }

    private fun TestScope.send(vm: ChatViewModel, text: String) {
        vm.onIntent(ChatIntent.Edit(text))
        runCurrent()
        vm.onIntent(ChatIntent.Send)
        advanceUntilIdle()
    }

    @Test
    fun aRememberPhraseIsSavedWithoutAskingTheModel() = runTest {
        val client = FakeInferenceClient()
        val vm = viewModel(client)
        send(vm, "remember that my sister is Sara")
        assertEquals(listOf("My sister is Sara"), memory.items.map { it.text })
        assertTrue(client.sentTexts.isEmpty())
        assertEquals(BIBI to "saved", vm.state.value.messages.last().let { it.role to it.text })
    }

    @Test
    fun aMatchingNoteReachesTheModelButNotTheSavedMessage() = runTest {
        val client = FakeInferenceClient().apply { enqueueReply("Her name is Sara.") }
        val vm = viewModel(client)
        send(vm, "remember that my sister is Sara")
        send(vm, "what's my sister's name?")
        assertEquals(listOf("(Remember: Your sister is Sara.)\nwhat's my sister's name?"), client.sentTexts)
        assertEquals("what's my sister's name?", vm.state.value.messages[2].text)
    }

    @Test
    fun aSecretIsRefusedAndNotStored() = runTest {
        val vm = viewModel(FakeInferenceClient())
        send(vm, "remember that my password is hunter2")
        assertTrue(memory.items.isEmpty())
        assertEquals("blocked PASSWORD", vm.state.value.messages.last().text)
    }

    @Test
    fun aQuestionAboutMemoryIsChat() = runTest {
        val client = FakeInferenceClient().apply { enqueueReply("I'm not sure.") }
        val vm = viewModel(client)
        send(vm, "do you remember my sister?")
        assertEquals(listOf("do you remember my sister?"), client.sentTexts)
        assertTrue(memory.items.isEmpty())
    }

    @Test
    fun rememberThisOnAMessageSavesItAndShowsANoticeForAFewSeconds() = runTest {
        val vm = viewModel(FakeInferenceClient())
        vm.onIntent(ChatIntent.RememberMessage("The gym opens at 6"))
        advanceTimeBy(100)
        assertEquals(listOf("The gym opens at 6"), memory.items.map { it.text })
        assertEquals("notice saved", vm.state.value.notice)
        advanceTimeBy(3_100)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun rememberThisRefusesACardNumber() = runTest {
        val vm = viewModel(FakeInferenceClient())
        vm.onIntent(ChatIntent.RememberMessage("card 4111 1111 1111 1111"))
        advanceTimeBy(100)
        assertTrue(memory.items.isEmpty())
        assertEquals("blocked CARD", vm.state.value.notice)
    }
}
