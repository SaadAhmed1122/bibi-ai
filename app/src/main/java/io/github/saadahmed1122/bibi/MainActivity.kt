package io.github.saadahmed1122.bibi

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.saadahmed1122.bibi.companion.overlay.OverlayService
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.debug.ActionsDebugRoute
import io.github.saadahmed1122.bibi.debug.CrashLogRoute
import io.github.saadahmed1122.bibi.debug.LlmDebugRoute
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.MemoryExport
import io.github.saadahmed1122.bibi.domain.usecase.Retention
import io.github.saadahmed1122.bibi.feature.chat.ChatRoute
import io.github.saadahmed1122.bibi.feature.chat.ModelImportRoute
import io.github.saadahmed1122.bibi.feature.chat.ModelImportViewModel
import io.github.saadahmed1122.bibi.feature.chat.VoicePackViewModel
import io.github.saadahmed1122.bibi.feature.memory.MemoryRoute
import io.github.saadahmed1122.bibi.feature.memory.MemoryViewModel
import io.github.saadahmed1122.bibi.feature.onboarding.OnboardingScreen
import io.github.saadahmed1122.bibi.feature.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app's screens; a plain state switch until there are enough screens to need a navigation library. */
private enum class Screen { Home, Chat, Model, Memory, Settings, Privacy, Terms, Licenses, Policy, CrashLog, LlmDebug, ActionsDebug }

/** The tab a top-level screen belongs to; null for screens opened from one (they show no bar). */
private val Screen.tab: Tab? get() = when (this) {
    Screen.Home -> Tab.HOME
    Screen.Chat -> Tab.CHAT
    Screen.Memory -> Tab.MEMORY
    Screen.Settings -> Tab.SETTINGS
    else -> null
}

private val Tab.screen: Screen get() = when (this) {
    Tab.HOME -> Screen.Home
    Tab.CHAT -> Screen.Chat
    Tab.MEMORY -> Screen.Memory
    Tab.SETTINGS -> Screen.Settings
}

@OptIn(ExperimentalLayoutApi::class)
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
                val tab = screen.tab
                // The bar shows on the four top-level places, not during onboarding or while typing.
                val showBar = tab != null && onboardingStep == null && !WindowInsets.isImeVisible
                // Landscape: a rail on the side keeps the little height there is for the screen.
                val wide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                Scaffold(
                    bottomBar = { if (showBar && !wide) AppNavBar(tab, onSelect = { screen = it.screen }) },
                    contentWindowInsets = WindowInsets(0),
                ) { barPadding ->
                Row(Modifier.padding(barPadding).consumeWindowInsets(barPadding)) {
                if (showBar && wide) AppNavRail(tab, onSelect = { screen = it.screen })
                Box(Modifier.weight(1f)) {
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
                        val voice = viewModel(key = "voice") { VoicePackViewModel(container.voicePack) }
                        val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
                            if (tree != null) voice.import(UriTreeVoiceSource(contentResolver, tree))
                        }
                        val voiceState by voice.state.collectAsStateWithLifecycle()
                        LaunchedEffect(voiceState) {
                            container.inference.setVoiceDir((voiceState as? ModelState.Ready)?.path.orEmpty())
                        }
                        ModelImportRoute(
                            viewModel = importer,
                            // The .litertlm type has no registered MIME type, so allow any file.
                            onPick = { picker.launch(arrayOf("*/*")) },
                            onOpenChat = { screen = Screen.Chat },
                            onBack = goHome,
                            router = router,
                            onPickRouter = { routerPicker.launch(arrayOf("*/*")) },
                            voice = voice,
                            onPickVoice = { voicePicker.launch(null) },
                        )
                    }
                    Screen.Settings -> SettingsRoute(
                        persona = container.persona,
                        safeMode = container.safeMode,
                        onSafeMode = container::setSafeMode,
                        onOpenPrivacy = { screen = Screen.Privacy },
                        onOpenPolicy = { screen = Screen.Policy },
                        onOpenLicenses = { screen = Screen.Licenses },
                    )
                    Screen.Privacy -> {
                        var stored by remember { mutableStateOf<StoredData?>(null) }
                        val retention by container.retention.current.collectAsStateWithLifecycle()
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(Unit) { stored = container.storedData() }
                        PrivacyScreen(
                            stored = stored,
                            retentionLabel = retentionText(retention),
                            onOpenMemory = { screen = Screen.Memory },
                            onDeleteEverything = {
                                scope.launch {
                                    runCatching { container.deleteEverything() }
                                    stored = container.storedData()
                                    Toast.makeText(this@MainActivity, R.string.privacy_deleted, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBack = { screen = Screen.Settings },
                        )
                    }
                    Screen.Memory -> {
                        val retention by container.retention.current.collectAsStateWithLifecycle()
                        val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                            if (uri != null) exportMemory(uri)
                        }
                        MemoryRoute(
                            viewModel = viewModel { MemoryViewModel(container.memory) },
                            retention = retention,
                            onRetention = container.retention::choose,
                            onExport = { exporter.launch("bibimem.json") },
                            onBack = goHome,
                        )
                    }
                    Screen.Terms -> AssetTextRoute(AssetText.GEMMA_TERMS, onBack = goHome)
                    Screen.Licenses -> AssetTextRoute(AssetText.LICENSES, onBack = { screen = Screen.Settings })
                    Screen.Policy -> AssetTextRoute(AssetText.PRIVACY_POLICY, onBack = { screen = Screen.Settings })
                    Screen.CrashLog -> CrashLogRoute(container.crashLog, onBack = goHome)
                    Screen.LlmDebug -> LlmDebugRoute(container.inference, onBack = goHome, summaries = container.summaries)
                    Screen.ActionsDebug -> ActionsDebugRoute(container.actions, container.actionLog, onBack = goHome)
                }
                }
                }
                }
            }
        }
    }

    private fun retentionText(r: Retention): String = getString(
        when (r) {
            Retention.WEEK -> io.github.saadahmed1122.bibi.feature.memory.R.string.memory_keep_week
            Retention.MONTH -> io.github.saadahmed1122.bibi.feature.memory.R.string.memory_keep_month
            Retention.THREE_MONTHS -> io.github.saadahmed1122.bibi.feature.memory.R.string.memory_keep_three_months
            Retention.YEAR -> io.github.saadahmed1122.bibi.feature.memory.R.string.memory_keep_year
            Retention.FOREVER -> io.github.saadahmed1122.bibi.feature.memory.R.string.memory_keep_forever
        },
    )

    /** Writes `bibimem.json` to the place the user picked (no storage permission needed). */
    private fun exportMemory(uri: Uri) {
        val memory = (application as BibiApplication).container.memory
        lifecycleScope.launch {
            val message = try {
                val items = memory.observeAll().first()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(MemoryExport.toJson(items, System.currentTimeMillis()).toByteArray()) }
                        ?: error("no output stream")
                }
                getString(io.github.saadahmed1122.bibi.feature.memory.R.string.memory_exported, items.size, "bibimem.json")
            } catch (e: Exception) {
                getString(io.github.saadahmed1122.bibi.feature.memory.R.string.memory_export_failed)
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(OverlayService.EXTRA_OPEN_CHAT, false)) openChatRequested = true
    }

    // A usage session (SPEC-test-report.md) runs while the app is on screen.
    override fun onStart() {
        super.onStart()
        (application as BibiApplication).container.usage.begin()
    }

    override fun onStop() {
        super.onStop()
        (application as BibiApplication).container.usage.end()
    }

    override fun onResume() {
        super.onResume()
        // Picks up the notification's Turn off, and brings floating Bibi back after a process kill.
        (application as BibiApplication).container.floating.onAppVisible()
    }
}
