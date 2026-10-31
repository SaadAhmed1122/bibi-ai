package io.github.saadahmed1122.bibi

import android.content.Context
import io.github.saadahmed1122.bibi.ai.inference.litert.MessengerInferenceClient
import io.github.saadahmed1122.bibi.core.common.crash.CrashLogWriter
import io.github.saadahmed1122.bibi.core.database.RoomChatHistoryRepository
import io.github.saadahmed1122.bibi.domain.model.ModelFile
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Hand-wired app dependencies (SPEC-app-shell.md: wiring by hand). One per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val crashLog = CrashLogWriter(
        file = File(context.filesDir, "crash/crash.log"),
        appVersion = BuildConfig.VERSION_NAME,
    )

    // Debug-only model location until the import flow (Task 11) provides the path.
    private val debugModel by lazy { File(appContext.getExternalFilesDir("models"), ModelFile.NAME) }

    /** Lazy, so the `:inference` process (which never asks for it) creates no client. */
    val inference: MessengerInferenceClient by lazy { MessengerInferenceClient(appContext) { debugModel.path } }

    val chatHistory: ChatHistoryRepository by lazy { RoomChatHistoryRepository.create(appContext) }

    /** Checked once at startup. TODO(#42): replace with ModelRepository and the import flow. */
    val modelState: StateFlow<ModelState> by lazy {
        MutableStateFlow(if (debugModel.isFile) ModelState.Ready(debugModel.path) else ModelState.Missing)
    }
}
