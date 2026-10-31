package io.github.saadahmed1122.bibi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.saadahmed1122.bibi.companion.overlay.OverlayService
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.debug.CrashLogRoute
import io.github.saadahmed1122.bibi.debug.LlmDebugRoute
import io.github.saadahmed1122.bibi.feature.chat.ChatRoute
import io.github.saadahmed1122.bibi.feature.chat.ModelImportRoute
import io.github.saadahmed1122.bibi.feature.chat.ModelImportViewModel

/** The app's screens; a plain state switch until there are enough screens to need a navigation library. */
private enum class Screen { Home, Chat, Model, Terms, CrashLog, LlmDebug }

class MainActivity : ComponentActivity() {
    /** Set by a tap on floating Bibi or the notification's Open chat ([OverlayService.EXTRA_OPEN_CHAT]). */
    private var openChatRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openChatRequested = intent.getBooleanExtra(OverlayService.EXTRA_OPEN_CHAT, false)
        val container = (application as BibiApplication).container
        setContent {
            BibiTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                val goHome = { screen = Screen.Home }
                LaunchedEffect(openChatRequested) {
                    if (openChatRequested) {
                        screen = Screen.Chat
                        openChatRequested = false
                    }
                }
                BackHandler(enabled = screen != Screen.Home, onBack = goHome)
                when (screen) {
                    Screen.Home -> HomeScreen(
                        onOpenChat = { screen = Screen.Chat },
                        onOpenModel = { screen = Screen.Model },
                        onOpenTerms = { screen = Screen.Terms },
                        onOpenCrashLog = if (BuildConfig.DEBUG) ({ screen = Screen.CrashLog }) else null,
                        onOpenLlmDebug = if (BuildConfig.DEBUG) ({ screen = Screen.LlmDebug }) else null,
                        // Debug-only until the Floating Bibi switch (Task 16, #47) asks for the permission.
                        onToggleOverlay = if (BuildConfig.DEBUG) ::toggleOverlay else null,
                    )
                    Screen.Chat -> ChatRoute(
                        // Activity-scoped: survives rotation, so the model stays loaded.
                        viewModel = viewModel { container.chatViewModel() },
                        onOpenModel = { screen = Screen.Model },
                        onBack = goHome,
                    )
                    Screen.Model -> {
                        val importer = viewModel { ModelImportViewModel(container.models) }
                        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            if (uri != null) importer.import(UriModelSource(contentResolver, uri))
                        }
                        ModelImportRoute(
                            viewModel = importer,
                            // The .litertlm type has no registered MIME type, so allow any file.
                            onPick = { picker.launch(arrayOf("*/*")) },
                            onOpenChat = { screen = Screen.Chat },
                            onBack = goHome,
                        )
                    }
                    Screen.Terms -> TermsRoute(onBack = goHome)
                    Screen.CrashLog -> CrashLogRoute(container.crashLog, onBack = goHome)
                    Screen.LlmDebug -> LlmDebugRoute(container.inference, onBack = goHome)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(OverlayService.EXTRA_OPEN_CHAT, false)) openChatRequested = true
    }

    /** Debug: starts floating Bibi, or opens the overlay permission page first. Stop is in its notification. */
    private fun toggleOverlay() {
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }
}
