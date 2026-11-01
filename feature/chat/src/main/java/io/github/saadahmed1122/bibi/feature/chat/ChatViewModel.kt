package io.github.saadahmed1122.bibi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.saadahmed1122.bibi.ai.inference.api.EngineState
import io.github.saadahmed1122.bibi.ai.inference.api.InferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.tools.ConfirmationRequest
import io.github.saadahmed1122.bibi.core.tools.PermissionEngine
import io.github.saadahmed1122.bibi.core.tools.RunResult
import io.github.saadahmed1122.bibi.core.tools.ShortcutParser
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What blocks sending, shown above the input. */
enum class ChatBanner { ImportModel, Loading, Restarting, Error }

/** Where a tap-to-talk conversation is (#118): waiting for speech, hearing it, working out the answer, saying it. */
enum class TalkPhase { LISTENING, HEARING, THINKING, SPEAKING }

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
    /** Voice (SPEC-voice.md): the pack is imported; the mic is held; answers are read aloud. */
    val voiceReady: Boolean = false,
    val listening: Boolean = false,
    val speakReplies: Boolean = false,
    /** Safe mode (FR-076): no actions, no memory writes; shown as a label. */
    val safeMode: Boolean = false,
    /** A tap-to-talk conversation in progress (#118), or null. */
    val talk: TalkPhase? = null,
) {
    /** Push-to-talk is offered: voice ready, and not while Bibi writes (memory check, #72) or talks. */
    val canTalk: Boolean get() = voiceReady && banner == null && !generating && pendingAction == null && talk == null
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

    /** Push-to-talk: the mic button is pressed / released. */
    data object MicDown : ChatIntent
    data object MicUp : ChatIntent

    /** "Read answers aloud" on or off. */
    data object ToggleSpeaker : ChatIntent

    /** Tap-to-talk (#118): one tap starts a spoken conversation; [StopTalk] ends it. */
    data object StartTalk : ChatIntent
    data object StopTalk : ChatIntent

    /** Safe mode on or off (the floating card's header switch). */
    data object ToggleSafeMode : ChatIntent

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
    /** Called with this chat's mood whenever it changes (floating Bibi shows it). */
    private val onMood: (BibiMood) -> Unit = {},
    private val voice: ChatVoice? = null,
    /** Safe mode (FR-076); [setSafeMode] saves a change. Null means no switch. */
    private val safeMode: StateFlow<Boolean>? = null,
    private val setSafeMode: (Boolean) -> Unit = {},
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

    /** Push-to-talk in progress (its listening id), and the transcription wait after release. */
    private var listenId: Long? = null
    private var chunker: AudioChunker? = null
    private val listening = MutableStateFlow(false)
    private val transcribing = MutableStateFlow(false)

    /** Sentences waiting to be spoken, in order; one coroutine speaks them. */
    private var speech = Channel<String>(Channel.UNLIMITED)
    private var speaker: Job? = null
    private val talking = MutableStateFlow(false)

    /** The tap-to-talk conversation (#118) and its loop. */
    private val talk = MutableStateFlow<TalkPhase?>(null)
    private var talkJob: Job? = null
    private val openChatRequests = Channel<Unit>(Channel.CONFLATED)

    /** Emits when the chat should open instead: "open chat" was said, or talking isn't possible. */
    val openChat: Flow<Unit> = openChatRequests.receiveAsFlow()

    /** True for a few seconds after an error, a refusal or a question back (SPEC-avatar-states.md). */
    private val confused = MutableStateFlow(false)
    private var confusedJob: Job? = null

    /**
     * Bibi's state for this chat: thinking until the first word, speaking while words arrive,
     * confused for [CONFUSED_MS] after something went wrong or Bibi had to ask, else idle.
     */
    val mood: StateFlow<BibiMood> = combine(streaming, confused, combine(transcribing, talk) { t, phase -> t || phase == TalkPhase.THINKING }, talking) { text, puzzled, hearing, voiceOut ->
        when {
            hearing || (text != null && text.isEmpty()) -> BibiMood.THINKING
            text != null || voiceOut -> BibiMood.SPEAKING
            puzzled -> BibiMood.CONFUSED
            else -> BibiMood.IDLE
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BibiMood.IDLE)

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
        }.combine(combine(voice?.ready ?: flowOf(false), listening, voice?.speakReplies ?: flowOf(false), ::Triple)) { ui, (ready, mic, aloud) ->
            ui.copy(voiceReady = ready, listening = mic, speakReplies = aloud)
        }.combine(safeMode ?: flowOf(false)) { ui, safe -> ui.copy(safeMode = safe) }
            .combine(combine(talk, talking) { phase, voiceOut -> if (phase == TalkPhase.THINKING && voiceOut) TalkPhase.SPEAKING else phase }) { ui, phase -> ui.copy(talk = phase) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    init {
        viewModelScope.launch {
            modelState.map { it is ModelState.Ready }.distinctUntilChanged().collect(onModelReady)
        }
        viewModelScope.launch { mood.collect(onMood) }
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Edit -> input.value = intent.text.take(MAX_INPUT_CHARS)
            ChatIntent.Send -> send()
            ChatIntent.Stop -> {
                client.stop()
                silence()
            }
            ChatIntent.ToggleSafeMode -> safeMode?.let { setSafeMode(!it.value) }
            ChatIntent.StartTalk -> startTalk()
            ChatIntent.StopTalk -> stopTalk()
            ChatIntent.MicDown -> micDown()
            ChatIntent.MicUp -> micUp()
            ChatIntent.ToggleSpeaker -> voice?.let { v ->
                val on = !v.speakReplies.value
                v.setSpeakReplies(on)
                if (!on) silence()
            }
            ChatIntent.Clear -> if (streaming.value == null) {
                pending.value?.let { answer(it, UserResponse.CANCEL, say = false) }
                viewModelScope.launch {
                    history.deleteAll()
                    client.resetConversation()
                }
            }
            is ChatIntent.RememberMessage -> memory?.let { m ->
                if (!m.writesAllowed()) {
                    showNotice(m.texts.safeMode())
                    return
                }
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
        if (!m.writesAllowed()) return m.texts.safeMode()
        return when (val r = m.remember(fact)) {
            is Remember.Result.Saved -> m.texts.saved()
            is Remember.Result.Blocked -> m.texts.blocked(r.reason).also { puzzle() }
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
    private suspend fun planAndRun(a: ChatActions, text: String): String? = if (!a.enabled()) {
        // Safe mode: no router and no action. A clear shortcut request gets an honest answer instead
        // of the model improvising ("Let's see if we can get some light" on the vivo).
        if (ShortcutParser.parse(text) != null) a.texts.safeMode() else null
    } else when (val plan = a.plan(text)) {
        TurnPlan.Chat -> null
        is TurnPlan.Ask -> plan.question.also { puzzle() }
        is TurnPlan.Act -> describe(a, a.runner.submit(plan.call))
    }

    private fun micDown() {
        val v = voice ?: return
        if (!state.value.canTalk || listenId != null) return
        silence()
        val id = client.startListening()
        val c = AudioChunker { client.sendAudio(id, it) }
        // The recorder delivers on its own thread; the chunker is used only there until stop().
        if (!v.audio.start { c.add(it) }) return
        listenId = id
        chunker = c
        listening.value = true
    }

    private fun micUp() {
        val v = voice ?: return
        val id = listenId ?: return
        v.audio.stop() // returns after the recorder thread has finished
        chunker?.flush()
        listenId = null
        chunker = null
        listening.value = false
        transcribing.value = true
        viewModelScope.launch {
            val heard = try {
                client.finishListening(id)
            } finally {
                transcribing.value = false
            }
            if (heard.isNullOrBlank()) {
                showNotice(v.notHeard)
                puzzle()
            } else {
                input.value = heard.take(MAX_INPUT_CHARS)
                send()
            }
        }
    }

    /**
     * Tap-to-talk (#118): listen until a pause, answer aloud, listen again; ends on [TalkCommand.END],
     * [Endpointer.Event.NoSpeech] or [stopTalk]. The mic is never on while Bibi speaks.
     */
    private fun startTalk() {
        val v = voice
        if (v == null || !state.value.voiceReady || banner.value != null) {
            openChatRequests.trySend(Unit)
            return
        }
        if (talkJob?.isActive == true || listenId != null) return
        silence()
        talkJob = viewModelScope.launch {
            try {
                while (true) {
                    talk.value = TalkPhase.LISTENING
                    val heard = hear(v) ?: break
                    if (heard.isBlank()) continue
                    when (TalkCommand.parse(heard)) {
                        TalkCommand.OPEN_CHAT -> {
                            openChatRequests.trySend(Unit)
                            break
                        }
                        TalkCommand.END -> break
                        null -> Unit
                    }
                    input.value = heard.take(MAX_INPUT_CHARS)
                    val reply = send(aloud = true) ?: break
                    reply.join()
                    talking.first { !it }
                }
            } finally {
                talk.value = null
            }
        }
    }

    private fun stopTalk() {
        talkJob?.cancel()
        talkJob = null
        if (streaming.value != null) client.stop()
        silence()
    }

    /**
     * One spoken turn: what was said, or null when nothing was (or the mic wouldn't start). The
     * recognizer gets the speech plus [PRE_ROLL] pieces from just before it started.
     */
    private suspend fun hear(v: ChatVoice): String? {
        val events = Channel<Endpointer.Event>(Channel.UNLIMITED)
        val id = client.startListening()
        val chunker = AudioChunker { client.sendAudio(id, it) }
        val endpointer = Endpointer()
        val preRoll = ArrayDeque<FloatArray>()
        var started = false
        // Recorder thread only, until stop() returns.
        val onChunk: (FloatArray) -> Unit = { samples ->
            val event = endpointer.add(samples)
            if (event == Endpointer.Event.SpeechStarted) {
                started = true
                preRoll.forEach { chunker.add(it) }
                preRoll.clear()
            }
            if (started) chunker.add(samples) else {
                preRoll.addLast(samples)
                if (preRoll.size > PRE_ROLL) preRoll.removeFirst()
            }
            if (event != null) events.trySend(event)
        }
        if (!v.audio.start(onChunk)) return null
        try {
            var event = events.receive()
            if (event == Endpointer.Event.SpeechStarted) {
                talk.value = TalkPhase.HEARING
                event = events.receive()
            }
            v.audio.stop()
            chunker.flush()
            talk.value = TalkPhase.THINKING
            val text = client.finishListening(id)
            return if (event == Endpointer.Event.NoSpeech) null else text.orEmpty()
        } finally {
            v.audio.stop()
        }
    }

    /** Queues one sentence to speak; one coroutine speaks them in order. Markdown marks aren't read out. */
    private fun say(raw: String) {
        val sentence = raw.replace(MARKDOWN, "").trim()
        if (sentence.none(Char::isLetterOrDigit)) return
        if (speaker?.isActive != true) {
            val queue = speech
            speaker = viewModelScope.launch {
                for (line in queue) {
                    talking.value = true
                    try {
                        client.speak(line)
                    } finally {
                        talking.value = !queue.isEmpty
                    }
                }
            }
        }
        speech.trySend(sentence)
    }

    /** Stops speaking and drops what was queued. */
    private fun silence() {
        if (speaker == null && !talking.value) return
        speech.close()
        speech = Channel(Channel.UNLIMITED)
        speaker?.cancel()
        speaker = null
        talking.value = false
        client.stopSpeaking()
    }

    /** Bibi looks puzzled for a moment. */
    private fun puzzle() {
        confused.value = true
        confusedJob?.cancel()
        confusedJob = viewModelScope.launch {
            delay(CONFUSED_MS)
            confused.value = false
        }
    }

    private fun describe(a: ChatActions, result: RunResult): String = when (result) {
        is RunResult.Done -> a.texts.done(result.call, result.target)
        is RunResult.Failed -> a.texts.failed(result.reason).also { puzzle() }
        is RunResult.Denied -> a.texts.denied(result.reason).also { puzzle() }
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

    /** Sends the input; returns the reply's job, or null when nothing was sent. [aloud] forces speaking. */
    private fun send(aloud: Boolean = voice?.speakReplies?.value == true): Job? {
        // Checked on the sources, not on [state], which may not have caught up with the last Edit.
        val text = input.value
        if (banner.value != null || streaming.value != null || text.isBlank()) return null
        input.value = ""
        streaming.value = ""
        summaries?.onUserActivity()
        val sentences = SentenceSplitter()
        var streamed = false
        return viewModelScope.launch {
            try {
                sendMessage(text).collect { event ->
                    if (event is ReplyEvent.Token) {
                        streaming.update { it.orEmpty() + event.text }
                        streamed = true
                        if (aloud) sentences.add(event.text).forEach(::say)
                    }
                    if (event is ReplyEvent.Error) puzzle()
                    // An action or memory reply arrives whole, without tokens.
                    if (aloud && event is ReplyEvent.Done && !streamed) say(event.fullText)
                }
                if (aloud) sentences.finish()?.let(::say)
            } finally {
                streaming.value = null
                summaries?.onTurnDone()
            }
        }
    }

    private companion object {
        const val NOTICE_MS = 3_000L
        const val CONFUSED_MS = 3_000L
        val MARKDOWN = Regex("[*_#`]")

        /** 0.3 s of audio from before speech was noticed, so the first word isn't clipped. */
        const val PRE_ROLL = 3
    }
}
