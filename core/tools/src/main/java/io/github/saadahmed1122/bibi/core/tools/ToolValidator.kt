package io.github.saadahmed1122.bibi.core.tools

// Pure Kotlin (no android.*).

/**
 * Argument rules shared by the shortcut path, the LLM path and the Permission Engine (FR-120/121,
 * PRD §16.6 rule 4). Ranges come from the AlarmClock reference on developer.android.com:
 * EXTRA_HOUR 0..23, EXTRA_MINUTES 0..59, EXTRA_LENGTH 1..86400 seconds.
 */
object ToolValidator {
    const val MAX_TIMER_SECONDS = 86_400
    const val MAX_APP_NAME = 40

    /** Java-style package name: dot-separated identifiers, at least two segments. Case-sensitive. */
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    /** App labels: letters, digits, spaces and a few separators. No URI or intent syntax. */
    private val APP_NAME = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} &'+\\-]*$")

    /** Literal number: optional leading +, digits, spaces, dashes, parentheses, dots. No * # , ; or letters. */
    private val NUMBER_CHARS = Regex("^\\+?[0-9 ()\\-.]+$")

    /** Returns all errors; empty means valid. */
    fun validate(call: ToolCall): List<String> = when (call) {
        is ToolCall.OpenApp -> validateApp(call.app)
        is ToolCall.SetAlarm -> buildList {
            if (call.hour !in 0..23) add("hour must be 0..23 (got ${call.hour})")
            if (call.minute !in 0..59) add("minute must be 0..59 (got ${call.minute})")
        }
        is ToolCall.StartTimer ->
            if (call.seconds !in 1..MAX_TIMER_SECONDS) listOf("seconds must be 1..$MAX_TIMER_SECONDS (got ${call.seconds})") else emptyList()
        is ToolCall.Flashlight -> emptyList()
        is ToolCall.Dial ->
            if (normalizeNumber(call.number) == null) {
                listOf("number must be a literal phone number: optional leading +, then 3..15 digits (spaces, dashes, dots, parentheses allowed)")
            } else {
                emptyList()
            }
    }

    private fun validateApp(app: String): List<String> {
        val s = app.trim()
        return when {
            s.isEmpty() -> listOf("app must not be empty")
            s.length > 255 -> listOf("app is too long")
            s.contains('.') -> if (PACKAGE.matches(s)) emptyList() else listOf("app is not a valid package name")
            s.length > MAX_APP_NAME -> listOf("app name must be at most $MAX_APP_NAME characters")
            !APP_NAME.matches(s) -> listOf("app name may contain only letters, digits, spaces and & ' + -")
            else -> emptyList()
        }
    }

    /**
     * The dialable form of a literal number ("+" then digits), or null if it isn't one. Rejects
     * letters, `*`, `#` (MMI/USSD codes), pause/wait characters and URI schemes. 3..15 digits (E.164 max 15).
     */
    fun normalizeNumber(raw: String): String? {
        val s = raw.trim()
        if (!NUMBER_CHARS.matches(s)) return null
        val digits = s.filter { it.isDigit() }
        if (digits.length !in 3..15) return null
        return if (s.startsWith("+")) "+$digits" else digits
    }
}
