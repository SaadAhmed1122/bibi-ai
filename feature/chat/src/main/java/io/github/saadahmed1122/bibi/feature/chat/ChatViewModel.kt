package io.github.saadahmed1122.bibi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.saadahmed1122.bibi.ai.inference.api.EngineState
import io.github.saadahmed1122.bibi.ai.inference.api.InferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What blocks sending, shown above the input. */
enum class ChatBanner { ImportModel, Loading, Restarting, Error }

/** [streamingText] is non-null while Bibi is answering. */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val streamingText: String? = null,
    val banner: ChatBanner? = ChatBanner.Loading,
) {
    val generating: Boolean get() = streamingText != null
    val canSend: Boolean get() = banner == null && !generating && input.isNotBlank()
    val canClear: Boolean get() = !generating && messages.isNotEmpty()
}

sealed interface ChatIntent {
    data class Edit(val text: String) : ChatIntent
    data object Send : ChatIntent
    data object Stop : ChatIntent
    data object Clear : ChatIntent
}

/** MVI state holder for the chat screen (SPEC-chat.md). */
class ChatViewModel(
    private val client: InferenceClient,
    private val history: ChatHistoryRepository,
    modelState: Flow<ModelState>,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) : ViewModel(scope) {

    private val sendMessage = SendMessage(client, history)
    private val input = MutableStateFlow("")
    private val streaming = MutableStateFlow<String?>(null)

    /** Set once the engine has been ready, so a later load reads as a restart (e.g. after Stop). */
    private var wasReady = false

    private val banner = combine(modelState, client.state) { model, engine ->
        if (engine == EngineState.Ready || engine == EngineState.Generating) wasReady = true
        when {
            model !is ModelState.Ready -> ChatBanner.ImportModel
            engine == EngineState.Ready || engine == EngineState.Generating -> null
            engine is EngineState.Failed -> ChatBanner.Error
            wasReady -> ChatBanner.Restarting
            else -> ChatBanner.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatBanner.Loading)

    val state: StateFlow<ChatUiState> =
        combine(history.observeAll(), input, streaming, banner) { messages, text, streamingText, banner ->
            ChatUiState(messages, text, streamingText, banner)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Edit -> input.value = intent.text.take(MAX_INPUT_CHARS)
            ChatIntent.Send -> send()
            ChatIntent.Stop -> client.stop()
            ChatIntent.Clear -> if (streaming.value == null) {
                viewModelScope.launch {
                    history.deleteAll()
                    client.resetConversation()
                }
            }
        }
    }

    private fun send() {
        // Checked on the sources, not on [state], which may not have caught up with the last Edit.
        val text = input.value
        if (banner.value != null || streaming.value != null || text.isBlank()) return
        input.value = ""
        streaming.value = ""
        viewModelScope.launch {
            try {
                sendMessage(text).collect { event ->
                    if (event is ReplyEvent.Token) streaming.update { it.orEmpty() + event.text }
                }
            } finally {
                streaming.value = null
            }
        }
    }
}
