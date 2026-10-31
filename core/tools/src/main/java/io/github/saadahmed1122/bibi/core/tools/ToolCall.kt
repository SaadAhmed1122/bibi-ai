package io.github.saadahmed1122.bibi.core.tools

// Pure Kotlin (no android.*): tested on a plain JVM by tools/core-test/run.sh.

/** PRD §16.4 risk classes. CRITICAL exists for completeness; no S4 tool uses it. */
enum class Risk { SAFE, SENSITIVE, CRITICAL }

/**
 * The five S4 tools (PRD 0.7) with their wire names and risk (PRD §16.3). `dial` is §16.3's
 * `call_contact` without contacts: a literal number only. It is SENSITIVE (open item 8 default) [OPEN].
 * [androidPermissions] are what the executor needs (developer.android.com "Common intents").
 */
enum class ToolName(val wire: String, val risk: Risk, val androidPermissions: List<String>, val description: String) {
    OPEN_APP("open_app", Risk.SAFE, emptyList(), "Open an installed app by name or package name."),
    SET_ALARM("set_alarm", Risk.SAFE, listOf(SET_ALARM_PERMISSION), "Set an alarm at a time of day (24-hour clock)."),
    START_TIMER("start_timer", Risk.SAFE, listOf(SET_ALARM_PERMISSION), "Start a countdown timer."),
    FLASHLIGHT("flashlight", Risk.SAFE, emptyList(), "Turn the flashlight on or off."),
    DIAL("dial", Risk.SENSITIVE, emptyList(), "Open the dialer with a number prefilled; the user presses Call."),
    ;

    companion object {
        fun fromWire(name: String): ToolName? = entries.firstOrNull { it.wire == name }
    }
}

const val SET_ALARM_PERMISSION = "com.android.alarm.permission.SET_ALARM"

/**
 * A proposed tool call with typed arguments. Construction does not validate; every call goes
 * through [ToolValidator] (router and Permission Engine) before anything executes (FR-121).
 */
sealed interface ToolCall {
    val tool: ToolName

    /** One log-safe line (FR-122): phone numbers keep only their last 2 digits (PRD §16.4). */
    fun redacted(): String

    /** Target is an app name ("maps") or a package name; the executor resolves it on the launcher list. */
    data class OpenApp(val app: String) : ToolCall {
        override val tool get() = ToolName.OPEN_APP
        override fun redacted() = "open_app app=$app"
    }

    data class SetAlarm(val hour: Int, val minute: Int) : ToolCall {
        override val tool get() = ToolName.SET_ALARM
        override fun redacted() = "set_alarm hour=$hour minute=$minute"
    }

    data class StartTimer(val seconds: Int) : ToolCall {
        override val tool get() = ToolName.START_TIMER
        override fun redacted() = "start_timer seconds=$seconds"
    }

    data class Flashlight(val on: Boolean) : ToolCall {
        override val tool get() = ToolName.FLASHLIGHT
        override fun redacted() = "flashlight on=$on"
    }

    /** [number] as written; the executor dials [ToolValidator.normalizeNumber] of it. */
    data class Dial(val number: String) : ToolCall {
        override val tool get() = ToolName.DIAL
        override fun redacted(): String {
            val n = ToolValidator.normalizeNumber(number) ?: number
            return "dial number=" + "*".repeat((n.length - 2).coerceAtLeast(0)) + n.takeLast(2)
        }
    }
}
