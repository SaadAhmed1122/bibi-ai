package io.github.saadahmed1122.bibi

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.github.saadahmed1122.bibi.ai.inference.api.Persona
import io.github.saadahmed1122.bibi.ai.inference.litert.MessengerInferenceClient
import io.github.saadahmed1122.bibi.companion.overlay.FloatingToggle
import io.github.saadahmed1122.bibi.companion.overlay.OverlayService
import io.github.saadahmed1122.bibi.core.common.crash.CrashLogWriter
import io.github.saadahmed1122.bibi.core.common.usage.Feature
import io.github.saadahmed1122.bibi.core.common.usage.FileUsageStore
import io.github.saadahmed1122.bibi.core.common.usage.UsageTracker
import io.github.saadahmed1122.bibi.core.database.RoomChatHistoryRepository
import io.github.saadahmed1122.bibi.core.database.RoomMemoryRepository
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.tools.ActionOutcome
import io.github.saadahmed1122.bibi.core.tools.ActionRunner
import io.github.saadahmed1122.bibi.core.tools.AppResolver
import io.github.saadahmed1122.bibi.core.tools.PermissionEngine
import io.github.saadahmed1122.bibi.core.tools.ToolAuditLog
import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.model.RouterModelFile
import io.github.saadahmed1122.bibi.domain.usecase.ApplyRetention
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import io.github.saadahmed1122.bibi.domain.usecase.ImportModel
import io.github.saadahmed1122.bibi.domain.usecase.MemoryPrompt
import io.github.saadahmed1122.bibi.domain.usecase.MemoryRepository
import io.github.saadahmed1122.bibi.domain.usecase.ModelRepository
import io.github.saadahmed1122.bibi.domain.usecase.Recall
import io.github.saadahmed1122.bibi.domain.usecase.Remember
import io.github.saadahmed1122.bibi.domain.usecase.Retention
import io.github.saadahmed1122.bibi.domain.usecase.VoicePackRepository
import io.github.saadahmed1122.bibi.feature.chat.AudioRecordSource
import io.github.saadahmed1122.bibi.feature.chat.ChatActions
import io.github.saadahmed1122.bibi.feature.chat.ChatMemory
import io.github.saadahmed1122.bibi.feature.chat.ChatViewModel
import io.github.saadahmed1122.bibi.feature.chat.ChatVoice
import io.github.saadahmed1122.bibi.feature.chat.PlanTurn
import io.github.saadahmed1122.bibi.feature.chat.ResourceActionTexts
import io.github.saadahmed1122.bibi.feature.chat.ResourceMemoryTexts
import io.github.saadahmed1122.bibi.feature.chat.SummaryScheduler
import io.github.saadahmed1122.bibi.feature.onboarding.OnboardingStore
import io.github.saadahmed1122.bibi.feature.tools.AppIndex
import io.github.saadahmed1122.bibi.feature.tools.ToolExecutor
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId

/** Hand-wired app dependencies (SPEC-app-shell.md: wiring by hand). One per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val crashLog = CrashLogWriter(
        file = File(context.filesDir, "crash/crash.log"),
        appVersion = BuildConfig.VERSION_NAME,
    )

    /** Usage counters for the closed test (SPEC-test-report.md): numbers and dates only. Main process. */
    val usage: UsageTracker by lazy {
        UsageTracker(FileUsageStore(File(appContext.filesDir, "usage/usage.txt")), System::currentTimeMillis, ZoneId::systemDefault)
    }

    /** The chat history, counting each message the user sends. */
    private val countedHistory: ChatHistoryRepository by lazy {
        object : ChatHistoryRepository by chatHistory {
            override suspend fun insert(message: Message): Long =
                chatHistory.insert(message).also { if (message.role == Message.Role.USER) usage.count(Feature.MESSAGE) }
        }
    }

    /** Memory as "remember this" sees it, counting each saved fact. */
    private val countedMemory: MemoryRepository by lazy {
        object : MemoryRepository by memory {
            override suspend fun add(kind: MemoryItem.Kind, text: String, now: Long): Long =
                memory.add(kind, text, now).also { usage.count(Feature.MEMORY) }
        }
    }

    /** The imported model in app-private storage; the `:inference` process (same app) reads it there. */
    val models: ModelRepository by lazy { ModelRepository(File(appContext.filesDir, "models")) }

    /** The optional action router model, next to the chat model (SPEC-tools.md). */
    val routerModel: ModelRepository by lazy {
        ModelRepository(
            modelsDir = File(appContext.filesDir, "models"),
            importModel = ImportModel(
                fileName = RouterModelFile.NAME,
                expectedSize = RouterModelFile.SIZE_BYTES,
                expectedSha256 = RouterModelFile.SHA256,
            ),
            fileName = RouterModelFile.NAME,
            expectedSize = RouterModelFile.SIZE_BYTES,
        )
    }

    /** The optional voice pack (SPEC-voice.md) in app-private storage; `:inference` reads it there. */
    val voicePack: VoicePackRepository by lazy { VoicePackRepository(File(appContext.filesDir, "voice")) }

    /** Lazy, so the `:inference` process (which never asks for it) creates no client. */
    val inference: MessengerInferenceClient by lazy {
        MessengerInferenceClient(
            context = appContext,
            modelPath = { (models.state.value as? ModelState.Ready)?.path.orEmpty() },
            routerPath = { (routerModel.state.value as? ModelState.Ready)?.path },
            persona = { persona.current.value },
            memoryNotes = { pinnedNotes },
            voiceDir = { (voicePack.state.value as? ModelState.Ready)?.path.orEmpty() },
        )
    }

    /**
     * A chat ViewModel for the app screen or the floating chat card. Each one holds its own counted
     * connection to `:inference`: on while the model is Ready, off when it is removed or the
     * ViewModel is cleared. Main thread (the connection calls are).
     */
    fun chatViewModel(): ChatViewModel {
        var connected = false
        val setConnected = { on: Boolean ->
            if (on != connected) {
                connected = on
                if (on) inference.connect() else inference.disconnect()
            }
        }
        val chatActions = ChatActions(PlanTurn(inference) { appResolver }::invoke, actions, ResourceActionTexts(appContext)) { !safeMode.value }
        val chatMemory = ChatMemory(Remember(countedMemory), ResourceMemoryTexts(appContext), Recall(memory)) { !safeMode.value }
        val key = Any()
        val chatVoice = ChatVoice(
            audio = AudioRecordSource(),
            ready = voicePack.state.map { it is ModelState.Ready },
            speakReplies = speakReplies,
            setSpeakReplies = ::setSpeakReplies,
            notHeard = appContext.getString(io.github.saadahmed1122.bibi.feature.chat.R.string.voice_not_heard),
            log = { android.util.Log.i("BibiTalk", it) },
        )
        return ChatViewModel(
            inference, countedHistory, models.state,
            onModelReady = setConnected, actions = chatActions, memory = chatMemory, summaries = summaries,
            onMood = { reportMood(key, it) },
            voice = chatVoice,
            safeMode = safeMode,
            setSafeMode = ::setSafeMode,
        ).apply {
            addCloseable {
                setConnected(false)
                reportMood(key, null)
            }
        }
    }

    private val prefs by lazy { appContext.getSharedPreferences("app", Context.MODE_PRIVATE) }
    private val _speakReplies by lazy { MutableStateFlow(prefs.getBoolean(KEY_SPEAK_REPLIES, false)) }

    /** "Read answers aloud" (SPEC-voice.md), off by default, shared by the app chat and the floating card. */
    val speakReplies: StateFlow<Boolean> get() = _speakReplies

    private val _safeMode by lazy { MutableStateFlow(prefs.getBoolean(KEY_SAFE_MODE, false)) }

    /** Safe mode (FR-076, SPEC-privacy.md): no actions, no memory writes, no summaries; chat still works. */
    val safeMode: StateFlow<Boolean> get() = _safeMode

    fun setSafeMode(on: Boolean) {
        _safeMode.value = on
        prefs.edit().putBoolean(KEY_SAFE_MODE, on).apply()
    }

    /** Counts for the Privacy dashboard. */
    suspend fun storedData(): StoredData = StoredData(
        messages = chatHistory.observeAll().first().size,
        memories = memory.count(),
        actionLogBytes = actionLog.read().toByteArray().size.toLong(),
        crashLogBytes = crashLog.read().toByteArray().size.toLong(),
    )

    /** Delete everything (SPEC-privacy.md): chats, memories, logs, settings; a fresh conversation. Models stay. Main thread. */
    suspend fun deleteEverything() = DeleteEverything(
        listOf(
            { chatHistory.deleteAll() },
            { memory.deleteAll() },
            { actionLog.clear() },
            { crashLog.clear() },
            { usage.clear() },
            {
                // Settings back to their defaults; the first-run flag and Floating Bibi stay.
                persona.choose(Persona.FRIENDLY)
                retention.choose(Retention.DEFAULT)
                setSpeakReplies(false)
                setSafeMode(false)
                prefs.edit().remove(KEY_LAST_SUMMARIZED).apply()
            },
            { inference.resetConversation() },
        ),
    )()

    private fun setSpeakReplies(on: Boolean) {
        _speakReplies.value = on
        prefs.edit().putBoolean(KEY_SPEAK_REPLIES, on).apply()
    }

    private val moods = HashMap<Any, BibiMood>()
    private val _bibiMood = MutableStateFlow(BibiMood.IDLE)

    /** What floating Bibi shows: the strongest mood of the open chats (app screen and floating card). */
    val bibiMood: StateFlow<BibiMood> = _bibiMood.asStateFlow()

    /** Main thread. [mood] null removes a chat that closed. */
    private fun reportMood(key: Any, mood: BibiMood?) {
        if (mood == null) moods.remove(key) else moods[key] = mood
        _bibiMood.value = BibiMood.strongest(moods.values)
    }

    /** Bibi's personality preset (SPEC-personality.md), kept in the "app" preferences. */
    val persona: PersonaSetting by lazy {
        val prefs = appContext.getSharedPreferences("app", Context.MODE_PRIVATE)
        PersonaSetting(
            read = { prefs.getString(KEY_PERSONA, null) },
            write = { prefs.edit().putString(KEY_PERSONA, it).apply() },
            apply = { inference.setPersona(it) },
        )
    }

    /** Main thread only (the Permission Engine is). The launcher list loads once, in the background. */
    val actions: ActionRunner by lazy {
        Thread({ appResolver = AppResolver(AppIndex.load(appContext.packageManager)) }, "app-index").start()
        val executor = ToolExecutor(appContext) { appResolver }
        ActionRunner(
            engine = PermissionEngine(nowMs = SystemClock::elapsedRealtime),
            audit = actionLog,
            execute = { call -> executor.execute(call).also { if (it is ActionOutcome.Done) usage.countAction(call.tool) } },
            deviceLocked = { appContext.getSystemService(KeyguardManager::class.java).isDeviceLocked },
            // Safe mode is also the Permission Engine's kill switch, behind the chat's own check.
            actionsEnabled = { !safeMode.value },
        )
    }

    @Volatile private var appResolver: AppResolver? = null

    /** The local action log (FR-122): capped, never leaves the phone. */
    val actionLog = ToolAuditLog(File(context.filesDir, "audit/actions.log"))

    /** The Floating Bibi switch (SPEC-onboarding.md): asks for the overlay permission only when turned on. */
    val floating = FloatingToggle(
        settings = OverlayService.settings(appContext),
        canDrawOverlays = { Settings.canDrawOverlays(appContext) },
        overlay = OverlayService.control(appContext),
    )

    /** The first-run flag (SPEC-onboarding.md). */
    val onboarding: OnboardingStore = object : OnboardingStore {
        private val prefs = appContext.getSharedPreferences("app", Context.MODE_PRIVATE)
        override var completed: Boolean
            get() = prefs.getBoolean("onboarding_completed", false)
            set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()
    }

    val chatHistory: ChatHistoryRepository by lazy { RoomChatHistoryRepository.create(appContext) }

    /** Bibi's memory (SPEC-memory.md), in the same database as the chat. */
    val memory: MemoryRepository by lazy { RoomMemoryRepository.create(appContext) }

    /** How long unpinned memories are kept (SPEC-memory.md); a shorter choice applies at once. */
    val retention: RetentionSetting by lazy {
        val prefs = appContext.getSharedPreferences("app", Context.MODE_PRIVATE)
        RetentionSetting(
            read = { prefs.getString(KEY_RETENTION, null) },
            write = { prefs.edit().putString(KEY_RETENTION, it).apply() },
            apply = { r -> MainScope().launch { ApplyRetention(memory)(r) } },
        )
    }

    /** Automatic memory summaries while idle (SPEC-memory.md); one for the app chat and the floating card. */
    val summaries: SummaryScheduler by lazy {
        val prefs = appContext.getSharedPreferences("app", Context.MODE_PRIVATE)
        SummaryScheduler(
            scope = MainScope(),
            client = inference,
            history = chatHistory,
            memory = memory,
            lastSummarized = { prefs.getLong(KEY_LAST_SUMMARIZED, 0L) },
            setLastSummarized = { prefs.edit().putLong(KEY_LAST_SUMMARIZED, it).apply() },
            enabled = { !safeMode.value },
        )
    }

    /** Main process only. The pinned-notes block for the system prompt, kept current by [syncPinnedNotes]. */
    @Volatile private var pinnedNotes = ""

    /** Main process only: keeps `:inference` told about pinned memories (they apply at its next conversation open). */
    fun syncPinnedNotes() {
        MainScope().launch {
            memory.observeAll().map(MemoryPrompt::pinned).distinctUntilChanged().collect {
                pinnedNotes = it
                inference.setMemoryNotes(it)
            }
        }
    }

    private companion object {
        const val KEY_PERSONA = "persona"
        const val KEY_LAST_SUMMARIZED = "last_summarized_message_id"
        const val KEY_RETENTION = "memory_retention"
        const val KEY_SPEAK_REPLIES = "speak_replies"
        const val KEY_SAFE_MODE = "safe_mode"
    }
}
