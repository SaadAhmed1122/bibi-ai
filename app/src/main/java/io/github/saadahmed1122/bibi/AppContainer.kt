package io.github.saadahmed1122.bibi

import android.content.Context
import io.github.saadahmed1122.bibi.ai.inference.litert.MessengerInferenceClient
import io.github.saadahmed1122.bibi.core.common.crash.CrashLogWriter
import io.github.saadahmed1122.bibi.core.database.RoomChatHistoryRepository
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import io.github.saadahmed1122.bibi.domain.usecase.ModelRepository
import io.github.saadahmed1122.bibi.feature.chat.ChatViewModel
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

    /** Lazy, so the `:inference` process (which never asks for it) creates no client. */
    val inference: MessengerInferenceClient by lazy {
        MessengerInferenceClient(appContext) { (models.state.value as? ModelState.Ready)?.path.orEmpty() }
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
        return ChatViewModel(inference, chatHistory, models.state, onModelReady = setConnected)
            .apply { addCloseable { setConnected(false) } }
    }

    val chatHistory: ChatHistoryRepository by lazy { RoomChatHistoryRepository.create(appContext) }
}
