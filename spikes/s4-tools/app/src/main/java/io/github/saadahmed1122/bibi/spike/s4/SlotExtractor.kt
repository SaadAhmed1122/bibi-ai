package io.github.saadahmed1122.bibi.spike.s4

import kotlin.math.abs
import kotlin.math.roundToLong

// Pure Kotlin (no android.*): tested on a plain JVM by tools/core-test/run.sh.

/** Pass 2 of the real-LLM path: the arguments for the Decision Maker's tool, or why there are none. */
sealed interface Slots {
    /** [args] use the tool's wire schema (ToolRouting.schemaLine); they are NOT validated here. */
    data class Filled(val tool: ToolName, val args: Map<String, Any?>) : Slots

    /** A required slot is missing or ambiguous: ask the user, never guess. */
    data class Missing(val tool: ToolName, val question: String, val reason: String) : Slots
}

/**
 * Deterministic English slot extraction after the Decision Maker label (Decision 2026-10-30:
 * Gemma 3 1B gave 0/100 valid tool calls). It fills only the chosen tool's arguments from the
 * user's text. Two different values for one slot, or none, give [Slots.Missing].
 * Coverage is the benchmark set plus common forms; other phrasings fall through to asking [VERIFY].
 */
object SlotExtractor {
    fun extract(tool: ToolName, text: String, apps: AppResolver?): Slots = when (tool) {
        ToolName.SET_ALARM -> alarm(text)
        ToolName.START_TIMER -> timer(text)
        ToolName.FLASHLIGHT -> flashlight(text)
        ToolName.DIAL -> dial(text)
        ToolName.OPEN_APP -> openApp(text, apps)
    }

    // ---- Shared text helpers -----------------------------------------------------------------

    private val ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    ).withIndex().associate { it.value to it.index }
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)

    /**
     * Lowercase, "a.m."/"p.m." → am/pm, hyphens → spaces, commas kept as " , ", other punctuation
     * (except : . ' between characters) → space, number words → digits ("twenty five" → 25).
     */
    internal fun numberText(text: String): String {
        val s = text.lowercase()
            .replace(Regex("\\ba\\.m\\.?"), "am")
            .replace(Regex("\\bp\\.m\\.?"), "pm")
            .replace('-', ' ')
            .replace(",", " , ")
            .replace(Regex("[^a-z0-9:.', +]"), " ")
            .replace(Regex("(?<!\\d)[.:]|[.:](?!\\d)"), " ")
        val words = s.split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<String>(words.size)
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val tens = TENS[w]
            when {
                tens != null -> {
                    val next = ONES[words.getOrNull(i + 1)]
                    if (next != null && next in 1..9) {
                        out += (tens + next).toString()
                        i++
                    } else {
                        out += tens.toString()
                    }
                }
                ONES[w] != null -> out += ONES.getValue(w).toString()
                else -> out += w
            }
            i++
        }
        return out.joinToString(" ")
    }

    private class Hit<T>(val range: IntRange, val value: T)

    /** Matches of [patterns] in priority order; a match overlapping an earlier accepted one is dropped. Sorted by position. */
    private fun <T> hits(s: String, patterns: List<Pair<Regex, (MatchResult) -> T?>>): List<Hit<T>>? {
        val accepted = mutableListOf<Hit<T>>()
        for ((re, read) in patterns) {
            for (m in re.findAll(s)) {
                if (accepted.any { it.range.first <= m.range.last && m.range.first <= it.range.last }) continue
                val v = read(m) ?: return null // a match that is not a valid value: refuse
                accepted += Hit(m.range, v)
            }
        }
        return accepted.sortedBy { it.range.first }
    }

    // ---- set_alarm {hour, minute} --------------------------------------------------------------

    private class Clock(val hour: Int, val minute: Int, val meridiem: String?)

    private const val AMPM = "(?:\\s*(am|pm)\\b)?"
    private const val NOT_DURATION = "(?!\\s*(?:hours?|hrs?|minutes?|mins?|seconds?|secs?)\\b)"

    private fun relative(m: MatchResult): Clock? {
        val mins = when (val q = m.groupValues[1]) {
            "quarter" -> 15
            "half" -> 30
            else -> q.toInt()
        }
        val h = m.groupValues[3].toInt()
        if (mins !in 1..59 || h !in 0..23) return null
        return if (m.groupValues[2] in setOf("to", "till", "before")) {
            Clock(if (h == 1) 12 else if (h == 0) 23 else h - 1, 60 - mins, m.groupValues[4].ifEmpty { null })
        } else {
            Clock(h, mins, m.groupValues[4].ifEmpty { null })
        }
    }

    private val ALARM_PATTERNS: List<Pair<Regex, (MatchResult) -> Clock?>> = listOf(
        // 6:45, 22.00, 6:45 pm
        Regex("\\b(\\d{1,2})[:.](\\d{2})\\b$AMPM") to { m -> Clock(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { null }) },
        // quarter past 8, 10 past 7, 20 to 9, half past six
        Regex("\\b(quarter|half|\\d{1,2})(?:\\s+minutes?)?\\s+(past|after|to|till|before)\\s+(\\d{1,2})\\b$AMPM") to ::relative,
        // half six (British: 6:30)
        Regex("\\bhalf\\s+(\\d{1,2})\\b$NOT_DURATION") to { m -> Clock(m.groupValues[1].toInt(), 30, null) },
        // seven thirty, 7 30, seven oh five
        Regex("\\b(\\d{1,2})\\s+(?:(\\d{2})|oh\\s+(\\d))\\b$NOT_DURATION$AMPM") to { m ->
            Clock(m.groupValues[1].toInt(), (m.groupValues[2].ifEmpty { m.groupValues[3] }).toInt(), m.groupValues[4].ifEmpty { null })
        },
        // 6 am, 6pm, 7 o'clock
        Regex("\\b(\\d{1,2})\\s*(am|pm|o'?clock)\\b") to { m ->
            Clock(m.groupValues[1].toInt(), 0, m.groupValues[2].takeIf { it == "am" || it == "pm" })
        },
        // at 7, for 9, by 6
        Regex("\\b(?:at|for|by|until|till)\\s+(\\d{1,2})\\b(?![:.]\\d)(?!\\s+\\d)$NOT_DURATION") to { m -> Clock(m.groupValues[1].toInt(), 0, null) },
        Regex("\\b(noon|midday|midnight)\\b") to { m -> Clock(if (m.groupValues[1] == "midnight") 0 else 12, 0, "fixed") },
        // Any other bare hour-sized number ("6 or 7"): counted, so two numbers make the request ambiguous.
        Regex("(?<![\\d:.+])\\b(\\d{1,2})\\b(?![:.]\\d)(?!\\s*\\d)$NOT_DURATION") to { m -> Clock(m.groupValues[1].toInt(), 0, null) },
    )
    private val PM_CONTEXT = Regex("\\b(?:in the (?:evening|afternoon)|at night|tonight|this (?:evening|afternoon))\\b")
    private val AM_CONTEXT = Regex("\\b(?:in the morning|this morning|tomorrow morning)\\b")

    private fun alarm(text: String): Slots {
        fun missing(reason: String) = Slots.Missing(ToolName.SET_ALARM, "What time should the alarm ring?", reason)
        val s = numberText(text)
        // "one and a half hours", "in 20 minutes": a length of time is not a clock time (and may be a timer).
        if (hits(s, TIMER_PATTERNS).orEmpty().isNotEmpty()) return missing("the request has a length of time, not a clock time")
        if (hasPhoneNumber(text)) return missing("the request has a phone number")
        val found = hits(s, ALARM_PATTERNS) ?: return missing("a time in the request is not a valid clock time")
        val pm = PM_CONTEXT.containsMatchIn(s)
        val am = AM_CONTEXT.containsMatchIn(s)
        val times = found.map { h ->
            val c = h.value
            var hour = c.hour
            when (c.meridiem) {
                "fixed" -> Unit
                "am", "pm" -> {
                    if (hour !in 1..12) return missing("\"$hour ${c.meridiem}\" is not a time")
                    hour = to24(hour, c.meridiem == "pm")
                }
                else -> if (am && pm) {
                    return missing("both morning and evening mentioned")
                } else if ((am || pm) && hour in 1..12) {
                    hour = to24(hour, pm)
                }
            }
            if (hour !in 0..23 || c.minute !in 0..59) return missing("not a valid clock time")
            hour to c.minute
        }.distinct()
        return when (times.size) {
            0 -> missing("no time of day in the request")
            1 -> Slots.Filled(ToolName.SET_ALARM, mapOf("hour" to times[0].first.toLong(), "minute" to times[0].second.toLong()))
            else -> missing("more than one time in the request")
        }
    }

    private fun to24(hour: Int, pm: Boolean) = when {
        pm && hour != 12 -> hour + 12
        !pm && hour == 12 -> 0
        else -> hour
    }

    // ---- start_timer {seconds} -----------------------------------------------------------------

    private const val FRACTION = "(?:\\s+and\\s+an?\\s+(half|quarter))?"

    private fun unitSeconds(unit: String): Double = when {
        unit.startsWith("h") -> 3600.0
        unit.startsWith("m") -> 60.0
        else -> 1.0
    }

    private fun fraction(f: String) = when (f) {
        "half" -> 0.5
        "quarter" -> 0.25
        else -> 0.0
    }

    private val TIMER_PATTERNS: List<Pair<Regex, (MatchResult) -> Double?>> = listOf(
        // a quarter of an hour, three quarters of an hour
        Regex("\\b(?:(\\d+)\\s+|an?\\s+)?quarters?\\s+(?:of\\s+)?(?:an?\\s+)?hour\\b") to { m -> (m.groupValues[1].toIntOrNull() ?: 1) * 900.0 },
        // half an hour, half a minute
        Regex("\\bhalf\\s+(?:an?\\s+)?(hour|minute|second)\\b") to { m -> 0.5 * unitSeconds(m.groupValues[1]) },
        // 10 minutes, 1.5 hours, one and a half hours, 8 min, 45 second
        Regex("\\b(\\d+(?:\\.\\d+)?)$FRACTION\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b$FRACTION") to ::duration,
        // an hour, an hour and a half, a minute: "a"/"an" only before a whole unit word (never "a m", "as")
        Regex("\\b(an?)$FRACTION\\s+(hours?|minutes?|seconds?)\\b$FRACTION") to ::duration,
    )

    private fun duration(m: MatchResult): Double =
        ((m.groupValues[1].toDoubleOrNull() ?: 1.0) + fraction(m.groupValues[2]) + fraction(m.groupValues[4])) * unitSeconds(m.groupValues[3])
    private val JOIN = Regex("^[\\s,]*(?:and)?[\\s,]*$")

    private fun timer(text: String): Slots {
        fun missing(reason: String) = Slots.Missing(ToolName.START_TIMER, "How long should the timer run?", reason)
        val s = numberText(text)
        val found = hits(s, TIMER_PATTERNS) ?: return missing("a duration in the request has an unclear unit")
        if (found.isEmpty()) return missing("no duration in the request")
        // Adjacent parts ("1 hour 30 minutes", "2 minutes and 30 seconds") add up; separate ones are ambiguous.
        for ((a, b) in found.zipWithNext()) {
            if (!JOIN.matches(s.substring(a.range.last + 1, b.range.first))) return missing("more than one duration in the request")
        }
        val seconds = found.sumOf { it.value }
        val whole = seconds.roundToLong()
        if (abs(seconds - whole) > 1e-6) return missing("the duration is not a whole number of seconds")
        return Slots.Filled(ToolName.START_TIMER, mapOf("seconds" to whole))
    }

    // ---- flashlight {on} -----------------------------------------------------------------------

    private val NEGATED = Regex("\\b(?:don'?t|do not|never|no need to)\\s+(?:\\w+\\s+)?(?:turn|switch|put|shut|kill|stop|disable|enable|activate)\\b")
    private val OFF = Regex(
        "\\boff\\b|\\bkill\\b|\\bstop\\b|\\bdisable\\b|\\bdeactivate\\b|\\bshut\\b|\\blights?\\s+out\\b|\\benough\\b|" +
            "\\bdon'?t need\\b|\\bno longer need\\b|\\banymore\\b|\\bi'?m done\\b|\\btoo bright\\b",
    )
    private val LIGHT = Regex("\\b(?:flash\\s?light|torch|light|lamp|dark|can'?t see)\\b|\\b(?:turn|switch|power|flip)\\s+(?:it\\s+)?on\\b")

    private fun flashlight(text: String): Slots {
        fun missing(reason: String) = Slots.Missing(ToolName.FLASHLIGHT, "Should I turn the flashlight on or off?", reason)
        val s = text.lowercase().replace('’', '\'')
        if (NEGATED.containsMatchIn(s)) return missing("the request is negated")
        return when {
            OFF.containsMatchIn(s) -> Slots.Filled(ToolName.FLASHLIGHT, mapOf("on" to false))
            // No off word: on, but only if the text asks for light at all (a mislabelled request gets no default).
            LIGHT.containsMatchIn(s) -> Slots.Filled(ToolName.FLASHLIGHT, mapOf("on" to true))
            else -> missing("no on/off or light word in the request")
        }
    }

    // ---- dial {number} -------------------------------------------------------------------------

    /** A digit run with optional leading +, inner spaces, dashes, parentheses and dots. */
    private val NUMBER_RUN = Regex("(?<![\\w+])\\+?\\(?\\d(?:[\\d \\-().]*\\d)?\\)?")

    /** Digit runs of 3 or more digits; shorter runs ("at 5") are not phone numbers. */
    private fun phoneNumbers(text: String): List<String> =
        NUMBER_RUN.findAll(text).map { it.value.trim() }.filter { r -> r.count { it.isDigit() } >= 3 }.toList()

    private fun hasPhoneNumber(text: String) = phoneNumbers(text).isNotEmpty()

    private fun dial(text: String): Slots {
        fun missing(reason: String) = Slots.Missing(ToolName.DIAL, "Which number should I put in the dialer?", reason)
        val numbers = phoneNumbers(text)
        return when (numbers.size) {
            0 -> missing("no phone number (digits) in the request")
            1 -> Slots.Filled(ToolName.DIAL, mapOf("number" to numbers[0].replace(" ", "").replace("-", "")))
            else -> missing("more than one phone number in the request")
        }
    }

    // ---- open_app {app} ------------------------------------------------------------------------

    private class Mention(val start: Int, val end: Int, val name: String, val entry: AppEntry)

    private fun openApp(text: String, apps: AppResolver?): Slots {
        fun missing(reason: String) = Slots.Missing(ToolName.OPEN_APP, "Which app should I open?", reason)
        if (apps == null) return missing("the launcher app list is not loaded yet")
        // "phone +44 …", "type 0131 … into the phone": a number means dial, not open_app.
        if (hasPhoneNumber(text)) return missing("the request has a phone number")
        val s = AppResolver.normalizeName(text)
        // Whole-word occurrences of every known name (labels, vendor-less labels, aliases). No prefixes.
        val all = apps.names.flatMap { (name, entry) ->
            Regex("(?<![^ ])" + Regex.escape(name) + "(?![^ ])").findAll(s).map { Mention(it.range.first, it.range.last + 1, name, entry) }.toList()
        }
        val kept = all.filter { m ->
            // Drop a name inside a longer one ("maps" in "google maps"), and a vendor word right before another name ("google" drive).
            all.none { o -> o !== m && o.start <= m.start && m.end <= o.end && (o.end - o.start) > (m.end - m.start) } &&
                !(m.name in AppResolver.VENDORS && all.any { o -> o.start == m.end + 1 })
        }
        val packages = kept.map { it.entry.packageName }.distinct()
        return when (packages.size) {
            0 -> missing("no installed app named in the request")
            1 -> Slots.Filled(ToolName.OPEN_APP, mapOf("app" to kept.maxBy { it.end - it.start }.name))
            else -> missing("more than one app named in the request")
        }
    }
}

/**
 * Pass 2 of the real-LLM route (PRD §16.2 path): the extracted slots become the same
 * `{"tool","args"}` JSON a model would give, then go through [ToolCallParser] (with [ToolValidator])
 * and the exposed-subset rule. No repair: extraction is deterministic, so an invalid result asks the user.
 */
object SlotToolRouter {
    fun route(selection: ToolSelection.Expose, text: String, apps: AppResolver?): LlmRouteResult =
        when (val slots = SlotExtractor.extract(selection.tool, text, apps)) {
            is Slots.Missing -> LlmRouteResult(LlmRoute.AskUser(slots.question, listOf(slots.reason)), null, null)
            is Slots.Filled -> fromSlots(slots, selection.exposed)
        }

    /** [exposed] is the Decision Maker's tool; anything else is rejected before parsing. */
    fun fromSlots(slots: Slots.Filled, exposed: Set<ToolName>): LlmRouteResult {
        val attempt = LlmToolRouter.check(CallSource.SLOTS, listOf(ModelToolCall(slots.tool.wire, slots.args)), exposed)
        attempt.rejection?.let { return LlmRouteResult(LlmRoute.Rejected(it), attempt, null) }
        val route = when (val p = attempt.parse!!) {
            is ToolCallParse.Valid -> LlmRoute.Call(p.call, attempts = 1)
            is ToolCallParse.Invalid -> LlmRoute.AskUser(ToolRouter.CLARIFY, p.errors)
            ToolCallParse.NoToolCall -> LlmRoute.AskUser(ToolRouter.CLARIFY, emptyList())
        }
        return LlmRouteResult(route, attempt, null)
    }
}
