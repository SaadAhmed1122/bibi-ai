package io.github.saadahmed1122.bibi

import android.content.Context
import io.github.saadahmed1122.bibi.core.common.crash.CrashLogWriter
import java.io.File

/** Hand-wired app dependencies (SPEC-app-shell.md: wiring by hand). One per process. */
class AppContainer(context: Context) {
    val crashLog = CrashLogWriter(
        file = File(context.filesDir, "crash/crash.log"),
        appVersion = BuildConfig.VERSION_NAME,
    )
}
