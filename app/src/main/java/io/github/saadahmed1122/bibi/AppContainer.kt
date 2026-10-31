package io.github.saadahmed1122.bibi

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.github.saadahmed1122.bibi.companion.overlay.FloatingToggle
import io.github.saadahmed1122.bibi.companion.overlay.OverlayService
import io.github.saadahmed1122.bibi.ai.inference.litert.MessengerInferenceClient
import io.github.saadahmed1122.bibi.core.common.crash.CrashLogWriter
import io.github.saadahmed1122.bibi.core.database.RoomChatHistoryRepository
import io.github.saadahmed1122.bibi.core.database.RoomMemoryRepository
import io.github.saadahmed1122.bibi.core.tools.ActionRunner
import io.github.saadahmed1122.bibi.core.tools.AppResolver
import io.github.saadahmed1122.bibi.core.tools.PermissionEngine
import io.github.saadahmed1122.bibi.core.tools.ToolAuditLog
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.model.RouterModelFile
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import io.github.saadahmed1122.bibi.domain.usecase.ImportModel
import io.github.saadahmed1122.bibi.domain.usecase.MemoryPrompt
import io.github.saadahmed1122.bibi.domain.usecase.MemoryRepository
import io.github.saadahmed1122.bibi.domain.usecase.ModelRepository
import io.github.saadahmed1122.bibi.domain.usecase.Recall
import io.github.saadahmed1122.bibi.domain.usecase.Remember
import io.github.saadahmed1122.bibi.feature.chat.ChatActions
import io.github.saadahmed1122.bibi.feature.chat.ChatMemory
import io.github.saadahmed1122.bibi.feature.chat.ChatViewModel
import io.github.saadahmed1122.bibi.feature.chat.PlanTurn
import io.github.saadahmed1122.bibi.feature.chat.ResourceActionTexts
import io.github.saadahmed1122.bibi.feature.chat.ResourceMemoryTexts
import io.github.saadahmed1122.bibi.feature.chat.SummaryScheduler
import io.github.saadahmed1122.bibi.feature.onboarding.OnboardingStore
import io.github.saadahmed1122.bibi.feature.tools.AppIndex
import io.github.saadahmed1122.bibi.feature.tools.ToolExecutor
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/** Hand-wired app dependencies (SPEC-app-shell.md: wiring by hand). One per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val crashLog = CrashLogWriter(
        file = File(context.filesDir, "crash/crash.log"),
        appVersion = BuildConfig.VERSION_NAME,
    )

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

    /** Lazy, so the `:inference` process (which never asks for it) creates no client. */
    val inference: MessengerInferenceClient by lazy {
        MessengerInferenceClient(
            context = appContext,
            modelPath = { (models.state.value as? ModelState.Ready)?.path.orEmpty() },
            routerPath = { (routerModel.state.value as? ModelState.Ready)?.path },
            persona = { persona.current.value },
            memoryNotes = { pinnedNotes },
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
        val chatActions = ChatActions(PlanTurn(inference) { appResolver }::invoke, actions, ResourceActionTexts(appContext))
        val chatMemory = ChatMemory(Remember(memory), ResourceMemoryTexts(appContext), Recall(memory))
        return ChatViewModel(
            inference, chatHistory, models.state,
            onModelReady = setConnected, actions = chatActions, memory = chatMemory, summaries = summaries,
        )
            .apply { addCloseable { setConnected(false) } }
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
            execute = executor::execute,
            deviceLocked = { appContext.getSystemService(KeyguardManager::class.java).isDeviceLocked },
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
    }
}
