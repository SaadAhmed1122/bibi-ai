package io.github.saadahmed1122.bibi.spike.s4

import java.util.Locale

// Pure Kotlin (no android.*, no model libraries): tested on a plain JVM by tools/core-test/run.sh.
// The adapters DecisionRouter.kt (MediaPipe) and LiteRtToolLlm.kt (LiteRT-LM) turn library types
// into the plain types below, so everything that decides what may run is testable here.

/** Which tool schema the LLM may see for one request (PRD §13.4 router). */
sealed interface ToolSelection {
    /** Expose exactly one tool to the LLM. */
    data class Expose(val tool: ToolName) : ToolSelection {
        val exposed: Set<ToolName> get() = setOf(tool)
    }

    /** No action: the request falls through to the clarify/chat path and the LLM is not asked for a tool. */
    data class NoAction(val reason: String) : ToolSelection
}

/**
 * The Decision Maker's question: five MVP tools + "none". It only selects which single tool
 * schema the LLM sees; it never dispatches anything (Decision 2026-10-30).
 */
object ToolRouting {
    const val NONE = "none"

    /**
     * Below this confidence the request is treated as "none". 0.25 from the vivo slot run (RESULTS.md §6):
     * right labels came in at 0.27–0.47; slot extraction still asks when a label is wrong. Tuned on
     * the same 110 prompts it was measured on [VERIFY on a fresh prompt set].
     */
    const val MIN_CONFIDENCE = 0.25f

    const val INSTRUCTIONS = "Select the single phone action the user's request asks for, or none if it asks for no phone action."

    /** Choice key → description, in this order. Keys are the tool wire names. */
    val CHOICES: Map<String, String> = linkedMapOf(
        ToolName.OPEN_APP.wire to "Open or launch an installed app, for example maps, camera, settings or YouTube.",
        ToolName.SET_ALARM.wire to "Set an alarm that rings at a clock time, for example to wake up at 7:30.",
        ToolName.START_TIMER.wire to "Start a countdown timer for a length of time, for example 10 minutes.",
        ToolName.FLASHLIGHT.wire to "Turn the phone's flashlight or torch on or off.",
        ToolName.DIAL.wire to "Open the phone dialer with a phone number filled in, to call that number.",
        NONE to "No phone action: chat, a question, or a request none of the other options covers.",
    )

    fun select(label: String?, confidence: Float, minConfidence: Float = MIN_CONFIDENCE): ToolSelection {
        if (label.isNullOrBlank()) return ToolSelection.NoAction("router returned no label")
        if (label == NONE) return ToolSelection.NoAction("router chose none")
        val tool = ToolName.fromWire(label) ?: return ToolSelection.NoAction("router returned unknown label \"$label\"")
        if (confidence.isNaN() || confidence < minConfidence) {
            return ToolSelection.NoAction(
                String.format(Locale.US, "low router confidence %.2f < %.2f for %s", confidence, minConfidence, label),
            )
        }
        return ToolSelection.Expose(tool)
    }

    /**
     * System instruction for the LLM when [tool] is the only tool exposed. LiteRT-LM also renders the
     * tool's schema through the model's prompt template; the line here is a fallback for templates
     * that ignore tools [VERIFY with gemma3-1b-it-int4].
     */
    fun systemInstruction(tool: ToolName): String =
        "You are the action step of Bibi, a phone assistant. If the user's request asks for it, call the " +
            "tool ${tool.wire} with arguments taken from the request; times use a 24-hour clock. Reply with " +
            "the tool call only. If the request does not ask for this action, reply with one short sentence " +
            "and no tool call. Tool: ${schemaLine(tool)}"

    /** One-line schema of [tool], for the system instruction and the repair message. */
    fun schemaLine(tool: ToolName): String = when (tool) {
        ToolName.OPEN_APP -> "open_app {\"app\": string}  app name (e.g. \"maps\") or package name"
        ToolName.SET_ALARM -> "set_alarm {\"hour\": integer 0..23, \"minute\": integer 0..59}  24-hour clock"
        ToolName.START_TIMER -> "start_timer {\"seconds\": integer 1..86400}"
        ToolName.FLASHLIGHT -> "flashlight {\"on\": true|false}"
        ToolName.DIAL -> "dial {\"number\": string}  digits only, optional leading +, 3..15 digits; the user presses Call"
    }
}

/** A tool call as the model returned it (LiteRT-LM `ToolCall.name` + `ToolCall.arguments`), unchecked. */
data class ModelToolCall(val name: String, val arguments: Map<String, Any?>)

/** One model answer: structured tool calls (if any) and the text. */
data class ModelTurn(val toolCalls: List<ModelToolCall>, val text: String)

/** Re-serializes a [ModelToolCall] into the `{"tool": …, "args": {…}}` JSON that [ToolCallParser] reads. */
object ModelCallEncoder {
    private val JSON_NUMBER = Regex("^-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?$")

    fun toParserJson(call: ModelToolCall): String =
        "{\"tool\":${MiniJson.quote(call.name)},\"args\":${value(call.arguments)}}"

    /**
     * JSON for one value. Faithful, not forgiving: a decimal stays a decimal (the parser then
     * rejects it as "must be an integer"), and anything that isn't a JSON number becomes a string.
     */
    fun value(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> v.toString()
        is String -> MiniJson.quote(v)
        is Double -> if (v.isFinite()) number(v.toString()) else MiniJson.quote(v.toString())
        is Float -> if (v.isFinite()) number(v.toString()) else MiniJson.quote(v.toString())
        is Number -> number(v.toString())
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) -> MiniJson.quote(k.toString()) + ":" + value(x) }
        is Iterable<*> -> v.joinToString(",", "[", "]") { value(it) }
        is Array<*> -> v.joinToString(",", "[", "]") { value(it) }
        else -> MiniJson.quote(v.toString())
    }

    private fun number(text: String) = if (JSON_NUMBER.matches(text)) text else MiniJson.quote(text)
}

/**
 * A model that doesn't emit structured tool calls may write one as text: `{"name": …, "arguments": {…}}`
 * (or `"parameters"`), possibly in a code fence. `{"tool": …}` and `<tool_call>` are left to [ToolCallParser].
 */
object TextToolCall {
    fun fromText(text: String): ModelToolCall? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start || text.length > ToolCallParser.MAX_OUTPUT) return null
        val root = try {
            MiniJson.parse(text.substring(start, end + 1))
        } catch (e: JsonError) {
            return null
        }
        if (root !is Map<*, *> || root.containsKey("tool")) return null
        val name = root["name"] as? String ?: return null
        val args = when {
            root.containsKey("arguments") -> root["arguments"]
            root.containsKey("parameters") -> root["parameters"]
            else -> emptyMap<String, Any?>()
        } as? Map<*, *> ?: return null
        return ModelToolCall(name, args.entries.associate { (k, v) -> k.toString() to v })
    }
}

enum class CallSource {
    /** LiteRT-LM returned `Message.toolCalls`. */
    STRUCTURED,

    /** No structured call; the text held a `{"name": …}` JSON call. */
    TEXT_JSON,

    /** No call found; the text goes to [ToolCallParser] as is. */
    TEXT,

    /** Not a model: [SlotExtractor] filled the Decision Maker's tool (pass 2, Decision 2026-10-30). */
    SLOTS,
}

/** One model answer after preparation and parsing. [rejection] set = a tool outside the exposed subset. */
data class LlmAttempt(
    val source: CallSource,
    val toolName: String,
    val parserInput: String,
    val parse: ToolCallParse?,
    val rejection: String?,
)

sealed interface LlmRoute {
    /** Validated and inside the exposed subset; it still has to pass the [PermissionEngine]. */
    data class Call(val call: ToolCall, val attempts: Int) : LlmRoute

    data class AskUser(val question: String, val errors: List<String>) : LlmRoute

    /** The model answered in text: chat, no action. */
    data class NoToolCall(val text: String) : LlmRoute

    /** The model named a tool it was not shown. Nothing runs, no repair. */
    data class Rejected(val reason: String) : LlmRoute
}

data class LlmRouteResult(val route: LlmRoute, val first: LlmAttempt?, val second: LlmAttempt?) {
    /** The tool the model named first ("" if none). */
    val toolName: String get() = first?.toolName.orEmpty()

    /** The first answer as given to [ToolCallParser]. May hold a phone number: log [argsJsonForLog]. */
    val argsJson: String get() = first?.parserInput.orEmpty()

    /** The first answer was a JSON tool call (structured, or text that parsed as a JSON object). */
    val jsonValid: Boolean get() {
        val a = first ?: return false
        if (a.source != CallSource.TEXT) return true
        return when (val p = a.parse) {
            null, ToolCallParse.NoToolCall -> false
            is ToolCallParse.Valid -> true
            is ToolCallParse.Invalid -> p.errors.none {
                it.startsWith("malformed JSON") || it.startsWith("output too long") || it == "the tool call must be a JSON object"
            }
        }
    }

    /** The first answer passed parsing and validation without a repair. */
    val argsValid: Boolean get() = first?.parse is ToolCallParse.Valid && first.rejection == null

    val repaired: Boolean get() = (route as? LlmRoute.Call)?.attempts == 2

    /** [argsJson] with dial numbers masked to their last 2 digits (FR-122). */
    fun argsJsonForLog(exposed: Set<ToolName>): String =
        if (ToolName.DIAL in exposed || toolName == ToolName.DIAL.wire) LogRedaction.maskDigits(argsJson) else argsJson
}

object LogRedaction {
    /** Every digit except the last [keep] becomes `*`. */
    fun maskDigits(s: String, keep: Int = 2): String {
        var digitsLeft = s.count { it.isDigit() }
        return buildString {
            for (c in s) {
                if (c.isDigit()) {
                    append(if (digitsLeft <= keep) c else '*')
                    digitsLeft--
                } else {
                    append(c)
                }
            }
        }
    }
}

/**
 * The real-LLM side of the Tool Router (PRD §16.2): every model call is re-serialized and goes
 * through [ToolCallParser] (which runs [ToolValidator]), with one repair, exactly like FakeLlm
 * output. On top of that, a call naming a tool outside the exposed subset is rejected outright.
 */
object LlmToolRouter {
    fun route(first: ModelTurn, exposed: Set<ToolName>, repair: (repairPrompt: String) -> ModelTurn): LlmRouteResult {
        val a1 = attempt(first, exposed)
        a1.rejection?.let { return LlmRouteResult(LlmRoute.Rejected(it), a1, null) }
        return when (val p1 = a1.parse!!) {
            is ToolCallParse.Valid -> LlmRouteResult(LlmRoute.Call(p1.call, attempts = 1), a1, null)
            ToolCallParse.NoToolCall -> LlmRouteResult(LlmRoute.NoToolCall(first.text), a1, null)
            is ToolCallParse.Invalid -> {
                val a2 = attempt(repair(repairPrompt(p1.errors, exposed)), exposed)
                a2.rejection?.let { return LlmRouteResult(LlmRoute.Rejected(it), a1, a2) }
                val route = when (val p2 = a2.parse!!) {
                    is ToolCallParse.Valid -> LlmRoute.Call(p2.call, attempts = 2)
                    is ToolCallParse.Invalid -> LlmRoute.AskUser(ToolRouter.CLARIFY, p2.errors)
                    ToolCallParse.NoToolCall -> LlmRoute.AskUser(ToolRouter.CLARIFY, p1.errors)
                }
                LlmRouteResult(route, a1, a2)
            }
        }
    }

    /** The single repair message: the errors and the exposed tools' schema only (no other tool names). */
    fun repairPrompt(errors: List<String>, exposed: Set<ToolName>): String = buildString {
        appendLine("Your tool call was rejected:")
        errors.forEach { appendLine("- $it") }
        appendLine("Call the tool again with corrected arguments. Tool:")
        exposed.forEach { appendLine("- " + ToolRouting.schemaLine(it)) }
    }.trimEnd()

    private fun attempt(turn: ModelTurn, exposed: Set<ToolName>): LlmAttempt {
        val (source, calls) = when {
            turn.toolCalls.isNotEmpty() -> CallSource.STRUCTURED to turn.toolCalls
            else -> TextToolCall.fromText(turn.text)?.let { CallSource.TEXT_JSON to listOf(it) } ?: (CallSource.TEXT to emptyList())
        }
        if (calls.isNotEmpty()) return check(source, calls, exposed)
        val parse = ToolCallParser.parse(turn.text)
        val call = (parse as? ToolCallParse.Valid)?.call
        val rejection = if (call != null && call.tool !in exposed) outsideReason(call.tool.wire, exposed) else null
        return LlmAttempt(CallSource.TEXT, call?.tool?.wire.orEmpty(), turn.text, parse, rejection)
    }

    /**
     * Re-serializes [calls] to the parser's JSON, rejects a tool outside [exposed], then parses
     * (and validates). Shared by model calls and [SlotToolRouter].
     */
    fun check(source: CallSource, calls: List<ModelToolCall>, exposed: Set<ToolName>): LlmAttempt {
        val names = calls.joinToString("+") { it.name }
        val input = if (calls.size == 1) {
            ModelCallEncoder.toParserJson(calls[0])
        } else {
            // More than one call: the parser rejects an array ("must be a JSON object"), which triggers the repair.
            calls.joinToString(",", "[", "]") { ModelCallEncoder.toParserJson(it) }
        }
        val outside = calls.firstOrNull { ToolName.fromWire(it.name) !in exposed }
        if (outside != null) return LlmAttempt(source, names, input, null, outsideReason(outside.name, exposed))
        return LlmAttempt(source, names, input, ToolCallParser.parse(input), null)
    }

    private fun outsideReason(name: String, exposed: Set<ToolName>) =
        "model called \"$name\", which was not exposed (exposed: ${exposed.joinToString { it.wire }})"
}
