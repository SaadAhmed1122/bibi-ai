package io.github.saadahmed1122.bibi.core.tools

// Pure Kotlin (no android.*).

/**
 * Deterministic English matcher for the top commands (PRD §13.5 layer 3): it skips the LLM. Every
 * pattern is anchored to the whole utterance, so negations ("don't turn on…"), compound requests
 * and anything unusual return null and fall through to the LLM path. The result is NOT validated
 * here; the router runs [ToolValidator] on it like on any other proposed call.
 */
object ShortcutParser {
    private val OPT = setOf(RegexOption.IGNORE_CASE)

    private val WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40,
        "forty-five" to 45, "fifty" to 50, "sixty" to 60, "ninety" to 90,
    )
    private val NUM = "(?:\\d{1,5}|" + WORDS.keys.sortedByDescending { it.length }.joinToString("|") + ")"
    private const val UNIT = "(?:hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)"
    private val PART = "$NUM\\s*-?\\s*$UNIT\\b"
    private val DUR = "$PART(?:\\s+(?:and\\s+)?$PART)*"
    private val PART_RE = Regex("($NUM)\\s*-?\\s*($UNIT)\\b", OPT)

    private val TIMER_FOR = Regex("^(?:(?:set|start)\\s+)?(?:(?:a|an|the)\\s+)?timer\\s+(?:for\\s+)?($DUR)$", OPT)
    private val TIMER_ADJ = Regex("^(?:(?:set|start)\\s+)?(?:(?:a|an)\\s+)?($DUR)\\s+timer$", OPT)

    private const val TIME = "(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?"
    private val ALARM = Regex(
        "^(?:(?:set\\s+)?(?:an?\\s+)?alarm\\s+(?:(?:for|at)\\s+)?|wake\\s+me(?:\\s+up)?\\s+at\\s+)$TIME$",
        OPT,
    )

    private const val FL = "(?:flash\\s?light|torch)"
    private val FLASH_VERB_FIRST = Regex("^(?:turn|switch)\\s+(on|off)\\s+(?:the\\s+)?$FL$", OPT)
    private val FLASH_VERB_LAST = Regex("^(?:turn|switch)\\s+(?:the\\s+)?$FL\\s+(on|off)$", OPT)
    private val FLASH_BARE = Regex("^(?:the\\s+)?$FL\\s+(on|off)$", OPT)

    private val DIAL = Regex("^(?:call|dial|phone|ring)\\s+([+(]?[0-9][0-9 ()\\-.]*)$", OPT)

    private val OPEN = Regex("^(?:open|launch|start)\\s+(?:the\\s+)?([\\p{L}\\p{N}][\\p{L}\\p{N} &'+\\-._]*?)(?:\\s+app)?$", OPT)

    private val PREFIX = Regex("^(?:(?:hey\\s+)?bibi\\s*,?\\s+|please\\s+|can\\s+you\\s+|could\\s+you\\s+)", OPT)
    private val SUFFIX = Regex("\\s+please$", OPT)

    fun parse(text: String): ToolCall? {
        val s = normalize(text)
        if (s.isEmpty()) return null
        flashlight(s)?.let { return it }
        timer(s)?.let { return it }
        alarm(s)?.let { return it }
        DIAL.matchEntire(s)?.let { return ToolCall.Dial(it.groupValues[1].trim()) }
        OPEN.matchEntire(s)?.let { return ToolCall.OpenApp(it.groupValues[1].trim()) }
        return null
    }

    /** Trims, drops trailing . ! ?, collapses spaces, strips "hey bibi,", "please", "can you" and a trailing "please". Keeps case. */
    internal fun normalize(text: String): String {
        var s = text.trim().trimEnd('.', '!', '?').trim().replace(Regex("\\s+"), " ")
        while (true) {
            val next = PREFIX.replaceFirst(s, "")
            if (next == s) break
            s = next
        }
        return SUFFIX.replace(s, "").trim()
    }

    private fun flashlight(s: String): ToolCall? {
        val m = FLASH_VERB_FIRST.matchEntire(s) ?: FLASH_VERB_LAST.matchEntire(s) ?: FLASH_BARE.matchEntire(s) ?: return null
        return ToolCall.Flashlight(m.groupValues[1].equals("on", ignoreCase = true))
    }

    private fun timer(s: String): ToolCall? {
        val m = TIMER_FOR.matchEntire(s) ?: TIMER_ADJ.matchEntire(s) ?: return null
        var seconds = 0L
        for (part in PART_RE.findAll(m.groupValues[1])) {
            val n = number(part.groupValues[1]) ?: return null
            val unit = part.groupValues[2].lowercase()
            seconds += n * when {
                unit.startsWith("h") -> 3600L
                unit.startsWith("m") -> 60L
                else -> 1L
            }
        }
        return ToolCall.StartTimer(seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    private fun alarm(s: String): ToolCall? {
        val m = ALARM.matchEntire(s) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        val meridiem = m.groupValues[3].lowercase().filter { it == 'a' || it == 'p' }
        if (meridiem.isNotEmpty()) {
            if (hour !in 1..12) return null // "13 pm" is not a time
            hour = when {
                meridiem == "a" && hour == 12 -> 0
                meridiem == "p" && hour != 12 -> hour + 12
                else -> hour
            }
        }
        // Without am/pm the hour is taken as written (24-hour): "alarm at 6" is 06:00 [OPEN: ask instead?].
        return ToolCall.SetAlarm(hour, minute)
    }

    private fun number(token: String): Long? = token.toLongOrNull() ?: WORDS[token.lowercase()]?.toLong()
}
