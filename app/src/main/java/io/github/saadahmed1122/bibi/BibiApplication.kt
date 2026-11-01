package io.github.saadahmed1122.bibi

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.saadahmed1122.bibi.companion.overlay.ChatCardHost
import io.github.saadahmed1122.bibi.companion.overlay.OverlayService
import io.github.saadahmed1122.bibi.core.common.ProcessNames
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import io.github.saadahmed1122.bibi.core.common.crash.CrashHandler
import io.github.saadahmed1122.bibi.core.common.crash.ExitInfoCollector
import io.github.saadahmed1122.bibi.core.common.crash.PlatformExitInfoSource
import io.github.saadahmed1122.bibi.core.common.crash.PrefsLastSeenStore
import io.github.saadahmed1122.bibi.feature.chat.ChatCardRoute
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

class BibiApplication : Application(), ChatCardHost {
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
        container.syncPinnedNotes()
        RetentionWorker.schedule(this)
    }

    override val bibiMood: StateFlow<BibiMood> get() = container.bibiMood

    /** The floating chat card (OverlayService). Its ViewModel lives in the overlay's own store. */
    @Composable
    override fun ChatCardContent(onClose: () -> Unit) {
        val viewModel = viewModel { container.chatViewModel() }
        ChatCardRoute(
            viewModel = viewModel,
            onOpenModel = {
                onClose()
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(OverlayService.EXTRA_OPEN_CHAT, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            },
            onClose = onClose,
        )
    }
}
