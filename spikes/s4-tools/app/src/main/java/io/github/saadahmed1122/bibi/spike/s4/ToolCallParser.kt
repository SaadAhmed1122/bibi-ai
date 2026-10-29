package io.github.saadahmed1122.bibi.spike.s4

// Pure Kotlin (no android.*).

/** Result of reading one model output. */
sealed interface ToolCallParse {
    data class Valid(val call: ToolCall) : ToolCallParse

    data class Invalid(val errors: List<String>) : ToolCallParse {
        /** The single repair message sent back to the model (PRD §13.5 layer 4). */
        fun repairPrompt(): String = buildString {
            appendLine("Your tool call was rejected:")
            errors.forEach { appendLine("- $it") }
            appendLine("Reply with exactly one tool call and nothing else, in this format:")
            appendLine("<tool_call>{\"tool\": NAME, \"args\": {...}}</tool_call>")
            append(ToolCallJson.SCHEMA)
        }
    }

    /** Plain text with no `{` and no `<tool_call>`: the model answered instead of calling a tool. */
    data object NoToolCall : ToolCallParse
}

/**
 * Parses the LLM's structured output: `{"tool": ..., "args": {...}}`, optionally wrapped in
 * `<tool_call>…</tool_call>`. Strict: unknown tools, unknown or missing keys, wrong types and
 * out-of-range values are errors. The model never supplies intents, URIs or components (§16.6).
 */
object ToolCallParser {
    const val MAX_OUTPUT = 2000
    private const val OPEN_TAG = "<tool_call>"
    private const val CLOSE_TAG = "</tool_call>"

    fun parse(output: String): ToolCallParse {
        if (output.length > MAX_OUTPUT) return invalid("output too long (max $MAX_OUTPUT characters)")
        val body = extract(output) ?: return ToolCallParse.NoToolCall
        val root = try {
            MiniJson.parse(body)
        } catch (e: JsonError) {
            return invalid("malformed JSON: ${e.message}")
        }
        if (root !is Map<*, *>) return invalid("the tool call must be a JSON object")
        val errors = mutableListOf<String>()
        for (k in root.keys) if (k != "tool" && k != "args") errors += "unexpected key \"$k\""
        val toolName = root["tool"]
        val tool = when {
            !root.containsKey("tool") -> null.also { errors += "missing \"tool\"" }
            toolName !is String -> null.also { errors += "\"tool\" must be a string" }
            else -> ToolName.fromWire(toolName).also {
                if (it == null) errors += "unknown tool \"$toolName\" (allowed: ${ToolName.entries.joinToString { t -> t.wire }})"
            }
        }
        val args = root["args"]
        when {
            !root.containsKey("args") -> errors += "missing \"args\""
            args !is Map<*, *> -> errors += "\"args\" must be a JSON object"
        }
        if (errors.isNotEmpty() || tool == null || args !is Map<*, *>) return ToolCallParse.Invalid(errors)
        val call = build(tool, args, errors) ?: return ToolCallParse.Invalid(errors)
        val rangeErrors = ToolValidator.validate(call)
        return if (rangeErrors.isEmpty()) ToolCallParse.Valid(call) else ToolCallParse.Invalid(rangeErrors)
    }

    /** The JSON text to parse, or null if the output contains no tool call attempt at all. */
    private fun extract(output: String): String? {
        val open = output.indexOf(OPEN_TAG)
        if (open >= 0) {
            val close = output.indexOf(CLOSE_TAG, open)
            // A missing close tag leaves the rest as the body, which then fails as malformed if truncated.
            return if (close < 0) output.substring(open + OPEN_TAG.length) else output.substring(open + OPEN_TAG.length, close)
        }
        val t = output.trim()
        if (t.startsWith("[")) return t
        val brace = t.indexOf('{')
        return if (brace < 0) null else t.substring(brace)
    }

    private fun build(tool: ToolName, args: Map<*, *>, errors: MutableList<String>): ToolCall? {
        val expected = when (tool) {
            ToolName.OPEN_APP -> listOf("app")
            ToolName.SET_ALARM -> listOf("hour", "minute")
            ToolName.START_TIMER -> listOf("seconds")
            ToolName.FLASHLIGHT -> listOf("on")
            ToolName.DIAL -> listOf("number")
        }
        for (k in args.keys) if (k !in expected) errors += "unexpected argument \"$k\" for ${tool.wire}"
        for (k in expected) if (!args.containsKey(k)) errors += "missing argument \"$k\" for ${tool.wire}"
        if (errors.isNotEmpty()) return null

        fun int(k: String): Int? {
            val v = args[k]
            return if (v is Long && v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else null.also { errors += "\"$k\" must be an integer" }
        }
        fun str(k: String): String? = args[k] as? String ?: null.also { errors += "\"$k\" must be a string" }

        val call = when (tool) {
            ToolName.OPEN_APP -> str("app")?.let { ToolCall.OpenApp(it) }
            ToolName.SET_ALARM -> {
                val h = int("hour")
                val m = int("minute")
                if (h != null && m != null) ToolCall.SetAlarm(h, m) else null
            }
            ToolName.START_TIMER -> int("seconds")?.let { ToolCall.StartTimer(it) }
            ToolName.FLASHLIGHT -> (args["on"] as? Boolean)?.let { ToolCall.Flashlight(it) }
                ?: null.also { errors += "\"on\" must be true or false" }
            ToolName.DIAL -> str("number")?.let { ToolCall.Dial(it) }
        }
        return if (errors.isEmpty()) call else null
    }

    private fun invalid(error: String) = ToolCallParse.Invalid(listOf(error))
}

/** Encodes calls in the wire format (FakeLlm, logs) and holds the schema text given to the model. */
object ToolCallJson {
    fun encode(call: ToolCall): String {
        val args = when (call) {
            is ToolCall.OpenApp -> "\"app\":${MiniJson.quote(call.app)}"
            is ToolCall.SetAlarm -> "\"hour\":${call.hour},\"minute\":${call.minute}"
            is ToolCall.StartTimer -> "\"seconds\":${call.seconds}"
            is ToolCall.Flashlight -> "\"on\":${call.on}"
            is ToolCall.Dial -> "\"number\":${MiniJson.quote(call.number)}"
        }
        return "{\"tool\":\"${call.tool.wire}\",\"args\":{$args}}"
    }

    /** Union schema of the five tools, in the compact form a small model reads best [VERIFY with S2 model]. */
    val SCHEMA: String = """
        |Tools:
        |- open_app {"app": string}  app name (e.g. "maps") or package name
        |- set_alarm {"hour": integer 0..23, "minute": integer 0..59}
        |- start_timer {"seconds": integer 1..86400}
        |- flashlight {"on": true|false}
        |- dial {"number": string}  digits only, optional leading +, 3..15 digits; the user presses Call
    """.trimMargin()
}

sealed interface RouterOutcome {
    data class Call(val call: ToolCall, val attempts: Int) : RouterOutcome
    data class AskUser(val question: String, val errors: List<String>) : RouterOutcome
    data object NoToolCall : RouterOutcome
}

/**
 * LLM-side entry of the Tool Router (PRD §16.2): validate, re-prompt once with the errors, then
 * ask the user. The result still has to pass the [PermissionEngine] before anything executes.
 */
object ToolRouter {
    fun fromModel(firstOutput: String, repair: (repairPrompt: String) -> String): RouterOutcome {
        when (val first = ToolCallParser.parse(firstOutput)) {
            is ToolCallParse.Valid -> return RouterOutcome.Call(first.call, attempts = 1)
            ToolCallParse.NoToolCall -> return RouterOutcome.NoToolCall
            is ToolCallParse.Invalid -> {
                return when (val second = ToolCallParser.parse(repair(first.repairPrompt()))) {
                    is ToolCallParse.Valid -> RouterOutcome.Call(second.call, attempts = 2)
                    is ToolCallParse.Invalid -> RouterOutcome.AskUser(CLARIFY, second.errors)
                    ToolCallParse.NoToolCall -> RouterOutcome.AskUser(CLARIFY, first.errors)
                }
            }
        }
    }

    const val CLARIFY = "I couldn't work out that action. Could you say it another way, e.g. \"set a timer for 5 minutes\"?"
}
