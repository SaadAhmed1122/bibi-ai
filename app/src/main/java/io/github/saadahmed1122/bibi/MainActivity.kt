package io.github.saadahmed1122.bibi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme
import io.github.saadahmed1122.bibi.debug.CrashLogRoute
import io.github.saadahmed1122.bibi.debug.LlmDebugRoute

/** The app's screens; a plain state switch until there are enough screens to need a navigation library. */
private enum class Screen { Home, Terms, CrashLog, LlmDebug }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BibiApplication).container
        setContent {
            BibiTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                val goHome = { screen = Screen.Home }
                BackHandler(enabled = screen != Screen.Home, onBack = goHome)
                when (screen) {
                    Screen.Home -> HomeScreen(
                        onOpenTerms = { screen = Screen.Terms },
                        onOpenCrashLog = if (BuildConfig.DEBUG) ({ screen = Screen.CrashLog }) else null,
                        onOpenLlmDebug = if (BuildConfig.DEBUG) ({ screen = Screen.LlmDebug }) else null,
                    )
                    Screen.Terms -> TermsRoute(onBack = goHome)
                    Screen.CrashLog -> CrashLogRoute(container.crashLog, onBack = goHome)
                    Screen.LlmDebug -> LlmDebugRoute(container.inference, onBack = goHome)
                }
            }
        }
    }
}
