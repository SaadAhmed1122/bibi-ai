package io.github.saadahmed1122.bibi

import android.app.ActivityManager
import android.app.Application
import io.github.saadahmed1122.bibi.core.common.ProcessNames
import io.github.saadahmed1122.bibi.core.common.crash.CrashHandler
import io.github.saadahmed1122.bibi.core.common.crash.ExitInfoCollector
import io.github.saadahmed1122.bibi.core.common.crash.PlatformExitInfoSource
import io.github.saadahmed1122.bibi.core.common.crash.PrefsLastSeenStore
import kotlin.concurrent.thread

class BibiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val processName = getProcessName()
        // Both processes log their own uncaught exceptions to the shared local file.
        CrashHandler.install(container.crashLog, processName)
        // The :inference process runs only the model service; it gets no UI wiring.
        if (ProcessNames.isInference(processName)) return

        val collector = ExitInfoCollector(
            source = PlatformExitInfoSource(getSystemService(ActivityManager::class.java), packageName),
            store = PrefsLastSeenStore(getSharedPreferences("crash_log", MODE_PRIVATE)),
            writer = container.crashLog,
        )
        // One-shot read of past exits; the binder call and file I/O stay off the main thread.
        thread(name = "exit-info") { collector.collect() }
    }
}
