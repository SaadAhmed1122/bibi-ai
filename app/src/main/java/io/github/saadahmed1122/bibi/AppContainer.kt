package io.github.saadahmed1122.bibi

import android.content.Context
import io.github.saadahmed1122.bibi.ai.inference.litert.MessengerInferenceClient
import io.github.saadahmed1122.bibi.core.common.crash.CrashLogWriter
import java.io.File

/** Hand-wired app dependencies (SPEC-app-shell.md: wiring by hand). One per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val crashLog = CrashLogWriter(
        file = File(context.filesDir, "crash/crash.log"),
        appVersion = BuildConfig.VERSION_NAME,
    )

    /** Lazy, so the `:inference` process (which never asks for it) creates no client. */
    val inference: MessengerInferenceClient by lazy {
        MessengerInferenceClient(appContext) {
            // Debug-only model location until the import flow (Task 11) provides the path.
            File(appContext.getExternalFilesDir("models"), DEBUG_MODEL_FILE).path
        }
    }

    private companion object {
        const val DEBUG_MODEL_FILE = "gemma3-1b-it-int4.litertlm"
    }
}
