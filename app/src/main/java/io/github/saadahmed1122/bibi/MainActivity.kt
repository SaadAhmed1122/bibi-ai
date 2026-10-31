package io.github.saadahmed1122.bibi

import android.Manifest
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.saadahmed1122.bibi.companion.overlay.OverlayService
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.debug.ActionsDebugRoute
import io.github.saadahmed1122.bibi.debug.CrashLogRoute
import io.github.saadahmed1122.bibi.debug.LlmDebugRoute
import io.github.saadahmed1122.bibi.feature.chat.ChatRoute
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.feature.chat.ModelImportRoute
import io.github.saadahmed1122.bibi.feature.chat.ModelImportViewModel
import io.github.saadahmed1122.bibi.feature.memory.MemoryRoute
import io.github.saadahmed1122.bibi.feature.memory.MemoryViewModel
import io.github.saadahmed1122.bibi.feature.onboarding.OnboardingScreen
import io.github.saadahmed1122.bibi.feature.onboarding.OnboardingViewModel

/** The app's screens; a plain state switch until there are enough screens to need a navigation library. */
private enum class Screen { Home, Chat, Model, Memory, Settings, Terms, CrashLog, LlmDebug, ActionsDebug }

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
                val floating by container.floating.state.collectAsStateWithLifecycle()
                val overlaySettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    container.floating.onReturnFromSettings()
                }
                val onboarding = viewModel { OnboardingViewModel(container.onboarding) }
                val onboardingStep by onboarding.step.collectAsStateWithLifecycle()
                when (screen) {
                    Screen.Home -> onboardingStep?.let { step ->
                        val importer = viewModel { ModelImportViewModel(container.models) }
                        val model by importer.state.collectAsStateWithLifecycle()
                        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            if (uri != null) importer.import(UriModelSource(contentResolver, uri))
                        }
                        // The first permission request in the app (SPEC-onboarding.md); any answer finishes.
                        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                            onboarding.finish()
                        }
                        OnboardingScreen(
                            step = step,
                            modelState = model,
                            onNext = onboarding::next,
                            onBack = onboarding::back,
                            onPickModel = { picker.launch(arrayOf("*/*")) },
                            onCancelImport = importer::cancel,
                            onOpenTerms = { screen = Screen.Terms },
                            onAllowNotifications = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            onNotNow = onboarding::finish,
                        )
                    } ?: HomeScreen(
                        onOpenChat = { screen = Screen.Chat },
                        onOpenModel = { screen = Screen.Model },
                        onOpenTerms = { screen = Screen.Terms },
                        onOpenCrashLog = if (BuildConfig.DEBUG) ({ screen = Screen.CrashLog }) else null,
                        onOpenLlmDebug = if (BuildConfig.DEBUG) ({ screen = Screen.LlmDebug }) else null,
                        onOpenActionsDebug = if (BuildConfig.DEBUG) ({ screen = Screen.ActionsDebug }) else null,
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenMemory = { screen = Screen.Memory },
                        floating = floating,
                        onFloatingSwitch = container.floating::onSwitch,
                        onCalmSwitch = container.floating::onCalm,
                        onExplainerContinue = {
                            container.floating.onExplainerContinue()
                            overlaySettings.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        },
                        onExplainerDismiss = container.floating::onExplainerDismiss,
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
                        val router = viewModel(key = "router") { ModelImportViewModel(container.routerModel) }
                        val routerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            if (uri != null) router.import(UriModelSource(contentResolver, uri))
                        }
                        // A router imported while the chat is connected loads right away; otherwise at the next bind.
                        val routerState by router.state.collectAsStateWithLifecycle()
                        LaunchedEffect(routerState) {
                            (routerState as? ModelState.Ready)?.let { container.inference.loadRouter(it.path) }
                        }
                        ModelImportRoute(
                            viewModel = importer,
                            // The .litertlm type has no registered MIME type, so allow any file.
                            onPick = { picker.launch(arrayOf("*/*")) },
                            onOpenChat = { screen = Screen.Chat },
                            onBack = goHome,
                            router = router,
                            onPickRouter = { routerPicker.launch(arrayOf("*/*")) },
                        )
                    }
                    Screen.Settings -> SettingsRoute(container.persona, onBack = goHome)
                    Screen.Memory -> MemoryRoute(viewModel { MemoryViewModel(container.memory) }, onBack = goHome)
                    Screen.Terms -> TermsRoute(onBack = goHome)
                    Screen.CrashLog -> CrashLogRoute(container.crashLog, onBack = goHome)
                    Screen.LlmDebug -> LlmDebugRoute(container.inference, onBack = goHome)
                    Screen.ActionsDebug -> ActionsDebugRoute(container.actions, container.actionLog, onBack = goHome)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(OverlayService.EXTRA_OPEN_CHAT, false)) openChatRequested = true
    }

    override fun onResume() {
        super.onResume()
        // Picks up the notification's Turn off, and brings floating Bibi back after a process kill.
        (application as BibiApplication).container.floating.onAppVisible()
    }
}
