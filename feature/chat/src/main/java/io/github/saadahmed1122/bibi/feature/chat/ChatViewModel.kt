package io.github.saadahmed1122.bibi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.saadahmed1122.bibi.ai.inference.api.EngineState
import io.github.saadahmed1122.bibi.ai.inference.api.InferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.core.tools.ConfirmationRequest
import io.github.saadahmed1122.bibi.core.tools.PermissionEngine
import io.github.saadahmed1122.bibi.core.tools.RunResult
import io.github.saadahmed1122.bibi.core.tools.UserResponse
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import io.github.saadahmed1122.bibi.domain.usecase.Remember
import io.github.saadahmed1122.bibi.domain.usecase.RememberPhrase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What blocks sending, shown above the input. */
enum class ChatBanner { ImportModel, Loading, Restarting, Error }

/**
 * [streamingText] is non-null while Bibi is answering; [pendingAction] is the open confirmation
 * card's text; [notice] is a short message that goes away by itself (e.g. "Saved to memory").
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val streamingText: String? = null,
    val banner: ChatBanner? = ChatBanner.Loading,
    val pendingAction: String? = null,
    val notice: String? = null,
) {
    val generating: Boolean get() = streamingText != null
    val canSend: Boolean get() = banner == null && !generating && pendingAction == null && input.isNotBlank()
    val canClear: Boolean get() = !generating && messages.isNotEmpty()
}

sealed interface ChatIntent {
    data class Edit(val text: String) : ChatIntent
    data object Send : ChatIntent
    data object Stop : ChatIntent
    data object Clear : ChatIntent

    /** "Remember this" on a message: saves its text unless it is something Bibi never stores. */
    data class RememberMessage(val text: String) : ChatIntent

    /** Answers to the confirmation card of a Sensitive action. */
    data object ConfirmAction : ChatIntent
    data object CancelAction : ChatIntent
}

/**
 * MVI state holder for the chat screen (SPEC-chat.md). [onModelReady] is called with true when the
 * model becomes Ready and false when it goes away (e.g. Remove), so the caller can bind or unbind
 * the inference service. [actions], when given, lets messages run phone actions (SPEC-tools.md);
 * a Sensitive one waits on a confirmation card that cancels itself after the engine's timeout.
 */
class ChatViewModel(
    private val client: InferenceClient,
    private val history: ChatHistoryRepository,
    modelState: Flow<ModelState>,
    onModelReady: (Boolean) -> Unit = {},
    private val actions: ChatActions? = null,
    private val memory: ChatMemory? = null,
    private val summaries: SummaryScheduler? = null,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) : ViewModel(scope) {

    private val sendMessage = SendMessage(
        client,
        history,
        handleAction = if (actions == null && memory == null) null else { text -> rememberPhrase(text) ?: actions?.let { planAndRun(it, text) } },
        modelText = { text -> memory?.recall?.invoke(text) ?: text },
    )
    private val notice = MutableStateFlow<String?>(null)
    private var noticeJob: Job? = null
    private val input = MutableStateFlow("")
    private val streaming = MutableStateFlow<String?>(null)
    private val pending = MutableStateFlow<ConfirmationRequest?>(null)
    private var pendingTimeout: Job? = null

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
        combine(
            combine(history.observeAll(), input, streaming, ::Triple),
            banner,
            pending,
            notice,
        ) { (messages, text, streamingText), banner, request, note ->
            ChatUiState(messages, text, streamingText, banner, request?.summary, note)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    init {
        viewModelScope.launch {
            modelState.map { it is ModelState.Ready }.distinctUntilChanged().collect(onModelReady)
        }
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Edit -> input.value = intent.text.take(MAX_INPUT_CHARS)
            ChatIntent.Send -> send()
            ChatIntent.Stop -> client.stop()
            ChatIntent.Clear -> if (streaming.value == null) {
                pending.value?.let { answer(it, UserResponse.CANCEL, say = false) }
                viewModelScope.launch {
                    history.deleteAll()
                    client.resetConversation()
                }
            }
            is ChatIntent.RememberMessage -> memory?.let { m ->
                viewModelScope.launch {
                    when (val r = m.remember(intent.text)) {
                        is Remember.Result.Saved -> showNotice(m.texts.noticeSaved())
                        is Remember.Result.Blocked -> showNotice(m.texts.blocked(r.reason))
                        Remember.Result.Empty -> Unit
                    }
                }
            }
            ChatIntent.ConfirmAction -> pending.value?.let { answer(it, UserResponse.CONFIRM) }
            ChatIntent.CancelAction -> pending.value?.let { answer(it, UserResponse.CANCEL) }
        }
    }

    /** A typed "remember that …": Bibi's reply, or null when the message isn't one. */
    private suspend fun rememberPhrase(text: String): String? {
        val m = memory ?: return null
        val fact = RememberPhrase.fact(text) ?: return null
        return when (val r = m.remember(fact)) {
            is Remember.Result.Saved -> m.texts.saved()
            is Remember.Result.Blocked -> m.texts.blocked(r.reason)
            Remember.Result.Empty -> null
        }
    }

    private fun showNotice(text: String) {
        notice.value = text
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MS)
            notice.value = null
        }
    }

    /** Main thread, inside the send. Returns Bibi's reply when the message was an action, null for chat. */
    private suspend fun planAndRun(a: ChatActions, text: String): String? = when (val plan = a.plan(text)) {
        TurnPlan.Chat -> null
        is TurnPlan.Ask -> plan.question
        is TurnPlan.Act -> describe(a, a.runner.submit(plan.call))
    }

    private fun describe(a: ChatActions, result: RunResult): String = when (result) {
        is RunResult.Done -> a.texts.done(result.call, result.target)
        is RunResult.Failed -> a.texts.failed(result.reason)
        is RunResult.Denied -> a.texts.denied(result.reason)
        is RunResult.Cancelled -> a.texts.cancelled()
        is RunResult.NeedsConfirmation -> {
            val request = result.request
            pending.value = request
            pendingTimeout?.cancel()
            // Silence is never consent: the card closes and nothing runs.
            pendingTimeout = viewModelScope.launch {
                delay(PermissionEngine.DEFAULT_TIMEOUT_MS)
                if (pending.value == request) {
                    a.runner.expire()
                    pending.value = null
                    saveBibi(a.texts.timedOut())
                }
            }
            a.texts.confirm()
        }
    }

    private fun answer(request: ConfirmationRequest, response: UserResponse, say: Boolean = true) {
        val a = actions ?: return
        pending.value = null
        pendingTimeout?.cancel()
        val reply = describe(a, a.runner.respond(request, response))
        if (say) saveBibi(reply)
    }

    private fun saveBibi(text: String) {
        viewModelScope.launch { history.insert(Message(0, Message.Role.BIBI, text, System.currentTimeMillis(), Message.Status.DONE)) }
    }

    private fun send() {
        // Checked on the sources, not on [state], which may not have caught up with the last Edit.
        val text = input.value
        if (banner.value != null || streaming.value != null || text.isBlank()) return
        input.value = ""
        streaming.value = ""
        summaries?.onUserActivity()
        viewModelScope.launch {
            try {
                sendMessage(text).collect { event ->
                    if (event is ReplyEvent.Token) streaming.update { it.orEmpty() + event.text }
                }
            } finally {
                streaming.value = null
                summaries?.onTurnDone()
            }
        }
    }

    private companion object {
        const val NOTICE_MS = 3_000L
    }
}
