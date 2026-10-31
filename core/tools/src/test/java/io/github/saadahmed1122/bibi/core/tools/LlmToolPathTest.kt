package io.github.saadahmed1122.bibi.core.tools


// The real-LLM path's pure pieces: Decision Maker label → exposed tool, LiteRT-LM ToolCall →
// ToolCallParser input, the exposed-subset guard, and the one repair.

private fun turn(name: String, vararg args: Pair<String, Any?>) = ModelTurn(listOf(ModelToolCall(name, mapOf(*args))), "")

private fun noRepair(): (String) -> ModelTurn = { throw AssertionError("repair must not be called") }

/** A Number that, like Gson's LazilyParsedNumber, prints the model's literal text. */
private class LiteralNumber(private val text: String) : Number() {
    override fun toByte() = text.toDouble().toInt().toByte()
    override fun toDouble() = text.toDouble()
    override fun toFloat() = text.toFloat()
    override fun toInt() = text.toDouble().toInt()
    override fun toLong() = text.toDouble().toLong()
    override fun toShort() = text.toDouble().toInt().toShort()
    override fun toString() = text
}

fun llmToolPathTests() {
    // ---- Decision Maker label → the one tool the LLM may see -----------------------------------
    checkEq("router choices: five tools + none", ToolName.entries.map { it.wire } + "none", ToolRouting.CHOICES.keys.toList())
    for (t in ToolName.entries) {
        checkEq("label ${t.wire} → expose only ${t.wire}", ToolSelection.Expose(t), ToolRouting.select(t.wire, 0.9f))
        checkEq("exposed subset of ${t.wire} is one tool", setOf(t), (ToolRouting.select(t.wire, 0.9f) as ToolSelection.Expose).exposed)
    }
    check("label none → no action", ToolRouting.select("none", 0.99f) is ToolSelection.NoAction)
    check("low confidence → no action", ToolRouting.select("set_alarm", ToolRouting.MIN_CONFIDENCE - 0.01f) is ToolSelection.NoAction)
    check("confidence at the threshold → expose", ToolRouting.select("set_alarm", ToolRouting.MIN_CONFIDENCE) is ToolSelection.Expose)
    // vivo slot run (RESULTS.md §6): right labels at 0.27–0.47 were dropped; "what time is it in Tokyo" was set_alarm at 0.15.
    for (c in listOf(0.27f, 0.35f, 0.47f)) check("vivo right label at $c → expose", ToolRouting.select("open_app", c) is ToolSelection.Expose)
    check("vivo wrong label at 0.15 → no action", ToolRouting.select("set_alarm", 0.15f) is ToolSelection.NoAction)
    check("NaN confidence → no action", ToolRouting.select("dial", Float.NaN) is ToolSelection.NoAction)
    check("unknown label → no action", ToolRouting.select("send_sms", 0.99f) is ToolSelection.NoAction)
    check("blank label → no action", ToolRouting.select("", 0.99f) is ToolSelection.NoAction)
    check("null label → no action", ToolRouting.select(null, 0.99f) is ToolSelection.NoAction)
    check("label is case-sensitive (no fuzzy match)", ToolRouting.select("SET_ALARM", 0.99f) is ToolSelection.NoAction)
    val lowReason = (ToolRouting.select("flashlight", 0.2f) as ToolSelection.NoAction).reason
    check("low-confidence reason names the value", lowReason.contains("0.20")) { lowReason }

    for (t in ToolName.entries) {
        val sys = ToolRouting.systemInstruction(t)
        val others = ToolName.entries.filter { it != t }.map { it.wire }
        check("system instruction for ${t.wire} names only that tool", sys.contains(t.wire) && others.none { sys.contains(it) }) { sys }
    }

    // ---- LiteRT-LM ToolCall (name + arguments map) → ToolCallParser input ----------------------
    checkEq(
        "encode set_alarm",
        """{"tool":"set_alarm","args":{"hour":7,"minute":30}}""",
        ModelCallEncoder.toParserJson(ModelToolCall("set_alarm", linkedMapOf("hour" to LiteralNumber("7"), "minute" to LiteralNumber("30")))),
    )
    checkEq(
        "encode Kotlin Int/Long",
        """{"tool":"start_timer","args":{"seconds":300}}""",
        ModelCallEncoder.toParserJson(ModelToolCall("start_timer", mapOf("seconds" to 300))),
    )
    checkEq("encode boolean", """{"tool":"flashlight","args":{"on":true}}""", ModelCallEncoder.toParserJson(ModelToolCall("flashlight", mapOf("on" to true))))
    checkEq(
        "encode string with quotes and escapes",
        """{"tool":"open_app","args":{"app":"a\"b\\c\n"}}""",
        ModelCallEncoder.toParserJson(ModelToolCall("open_app", mapOf("app" to "a\"b\\c\n"))),
    )
    checkEq("encode null", """{"tool":"dial","args":{"number":null}}""", ModelCallEncoder.toParserJson(ModelToolCall("dial", mapOf("number" to null))))
    checkEq(
        "encode nested list and map",
        """{"tool":"x","args":{"a":[1,"b",false],"m":{"k":null}}}""",
        ModelCallEncoder.toParserJson(ModelToolCall("x", linkedMapOf("a" to listOf(1, "b", false), "m" to mapOf("k" to null)))),
    )
    checkEq("encode empty args", """{"tool":"flashlight","args":{}}""", ModelCallEncoder.toParserJson(ModelToolCall("flashlight", emptyMap())))
    // Faithful: a decimal stays a decimal, so the parser rejects it (no silent fixing of model output).
    checkEq("encode 7.0 stays 7.0", """{"tool":"set_alarm","args":{"hour":7.0}}""", ModelCallEncoder.toParserJson(ModelToolCall("set_alarm", mapOf("hour" to 7.0))))
    checkEq("NaN becomes a string", """{"tool":"set_alarm","args":{"hour":"NaN"}}""", ModelCallEncoder.toParserJson(ModelToolCall("set_alarm", mapOf("hour" to Double.NaN))))
    checkEq("non-JSON number text becomes a string", """{"tool":"t","args":{"n":"0x10"}}""", ModelCallEncoder.toParserJson(ModelToolCall("t", mapOf("n" to LiteralNumber("0x10")))))

    // Round trip: every encoded valid call parses back to the same typed call.
    val samples = listOf(
        ModelToolCall("open_app", mapOf("app" to "maps")) to ToolCall.OpenApp("maps"),
        ModelToolCall("set_alarm", mapOf("hour" to LiteralNumber("6"), "minute" to LiteralNumber("45"))) to ToolCall.SetAlarm(6, 45),
        ModelToolCall("start_timer", mapOf("seconds" to LiteralNumber("600"))) to ToolCall.StartTimer(600),
        ModelToolCall("flashlight", mapOf("on" to false)) to ToolCall.Flashlight(false),
        ModelToolCall("dial", mapOf("number" to "020 7946 0018")) to ToolCall.Dial("020 7946 0018"),
    )
    for ((model, expected) in samples) {
        val parsed = ToolCallParser.parse(ModelCallEncoder.toParserJson(model))
        checkEq("round trip ${model.name}", ToolCallParse.Valid(expected), parsed)
    }

    // ---- Text fallback: a JSON tool call written as text --------------------------------------
    checkEq(
        "text {name, arguments}",
        ModelToolCall("set_alarm", mapOf("hour" to 7L, "minute" to 0L)),
        TextToolCall.fromText("""Sure! {"name": "set_alarm", "arguments": {"hour": 7, "minute": 0}}"""),
    )
    checkEq(
        "text {name, parameters} in a code fence",
        ModelToolCall("flashlight", mapOf("on" to true)),
        TextToolCall.fromText("```json\n{\"name\":\"flashlight\",\"parameters\":{\"on\":true}}\n```"),
    )
    checkEq("plain text → no call", null, TextToolCall.fromText("I can't do that."))
    checkEq("parser format is left to ToolCallParser", null, TextToolCall.fromText("""{"tool":"flashlight","args":{"on":true}}"""))

    // ---- Routing through ToolCallParser → ToolValidator with the exposed subset ------------------
    val alarmOnly = setOf(ToolName.SET_ALARM)
    val ok = LlmToolRouter.route(turn("set_alarm", "hour" to LiteralNumber("7"), "minute" to LiteralNumber("30")), alarmOnly, noRepair())
    checkEq("structured call → Call", LlmRoute.Call(ToolCall.SetAlarm(7, 30), attempts = 1), ok.route)
    checkEq("structured source", CallSource.STRUCTURED, ok.first?.source)
    checkEq("tool name logged", "set_alarm", ok.toolName)
    checkEq("args json logged", """{"tool":"set_alarm","args":{"hour":7,"minute":30}}""", ok.argsJson)
    check("json ok", ok.jsonValid)
    check("args ok", ok.argsValid)
    check("not repaired", !ok.repaired)

    // A tool outside the exposed subset is rejected outright: no repair, nothing reaches the engine.
    val other = LlmToolRouter.route(turn("dial", "number" to "0123456789"), alarmOnly, noRepair())
    check("tool outside the subset → Rejected", other.route is LlmRoute.Rejected) { "${other.route}" }
    check("rejection names the tool", (other.route as LlmRoute.Rejected).reason.contains("dial"))
    checkEq("rejected call's name still logged", "dial", other.toolName)
    val unknown = LlmToolRouter.route(turn("send_sms", "to" to "mum"), alarmOnly, noRepair())
    check("unknown tool → Rejected", unknown.route is LlmRoute.Rejected)
    val second = LlmToolRouter.route(
        ModelTurn(listOf(ModelToolCall("set_alarm", mapOf("hour" to 7, "minute" to 0)), ModelToolCall("dial", mapOf("number" to "123"))), ""),
        alarmOnly,
        noRepair(),
    )
    check("one of two calls outside the subset → Rejected", second.route is LlmRoute.Rejected)
    val textOther = LlmToolRouter.route(ModelTurn(emptyList(), """<tool_call>{"tool":"dial","args":{"number":"0123456789"}}</tool_call>"""), alarmOnly, noRepair())
    check("text tool_call outside the subset → Rejected", textOther.route is LlmRoute.Rejected) { "${textOther.route}" }
    val textNameOther = LlmToolRouter.route(ModelTurn(emptyList(), """{"name":"open_app","arguments":{"app":"maps"}}"""), alarmOnly, noRepair())
    check("text {name} outside the subset → Rejected", textNameOther.route is LlmRoute.Rejected)

    // Text tool call inside the subset is accepted.
    val textOk = LlmToolRouter.route(ModelTurn(emptyList(), """{"name":"set_alarm","arguments":{"hour":6,"minute":30}}"""), alarmOnly, noRepair())
    checkEq("text JSON call → Call", LlmRoute.Call(ToolCall.SetAlarm(6, 30), 1), textOk.route)
    checkEq("text JSON source", CallSource.TEXT_JSON, textOk.first?.source)

    // One repair, then ask the user (same rule as ToolRouter).
    var repairPrompt = ""
    val fixed = LlmToolRouter.route(turn("set_alarm", "hour" to 25, "minute" to 0), alarmOnly) {
        repairPrompt = it
        turn("set_alarm", "hour" to 7, "minute" to 0)
    }
    checkEq("bad args → repaired once", LlmRoute.Call(ToolCall.SetAlarm(7, 0), attempts = 2), fixed.route)
    check("repaired flag", fixed.repaired)
    check("json ok on first attempt", fixed.jsonValid)
    check("args not valid on first attempt", !fixed.argsValid)
    check("repair prompt lists the error", repairPrompt.contains("hour must be 0..23")) { repairPrompt }
    check("repair prompt names only the exposed tool", repairPrompt.contains("set_alarm") && !repairPrompt.contains("dial") && !repairPrompt.contains("open_app")) { repairPrompt }

    var repairs = 0
    val twice = LlmToolRouter.route(turn("set_alarm", "hour" to 25, "minute" to 0), alarmOnly) {
        repairs++
        turn("set_alarm", "hour" to 99, "minute" to 0)
    }
    check("bad twice → AskUser", twice.route is LlmRoute.AskUser)
    checkEq("AskUser uses the router's question", ToolRouter.CLARIFY, (twice.route as LlmRoute.AskUser).question)
    checkEq("exactly one repair", 1, repairs)

    val repairedOutside = LlmToolRouter.route(turn("set_alarm", "hour" to 25, "minute" to 0), alarmOnly) { turn("dial", "number" to "0123456789") }
    check("repair answer outside the subset → Rejected", repairedOutside.route is LlmRoute.Rejected)

    val multiple = LlmToolRouter.route(
        ModelTurn(listOf(ModelToolCall("set_alarm", mapOf("hour" to 7, "minute" to 0)), ModelToolCall("set_alarm", mapOf("hour" to 8, "minute" to 0))), ""),
        alarmOnly,
    ) { turn("set_alarm", "hour" to 7, "minute" to 0) }
    checkEq("two calls → repaired to one", LlmRoute.Call(ToolCall.SetAlarm(7, 0), 2), multiple.route)

    val chat = LlmToolRouter.route(ModelTurn(emptyList(), "It's a lovely day."), alarmOnly, noRepair())
    checkEq("plain text → NoToolCall", LlmRoute.NoToolCall("It's a lovely day."), chat.route)
    check("plain text: json not ok", !chat.jsonValid)
    checkEq("plain text: no tool name", "", chat.toolName)

    val malformed = LlmToolRouter.route(ModelTurn(emptyList(), """<tool_call>{"tool":"set_alarm","args":{"hour":7,</tool_call>"""), alarmOnly) {
        turn("set_alarm", "hour" to 7, "minute" to 0)
    }
    check("malformed text: json not ok", !malformed.jsonValid)
    checkEq("malformed text → repaired", LlmRoute.Call(ToolCall.SetAlarm(7, 0), 2), malformed.route)

    // Logged args: numbers of a dial keep their last 2 digits only (FR-122).
    checkEq("mask digits", "*** **** **18", LogRedaction.maskDigits("020 7946 0018"))
    val dialLog = LlmToolRouter.route(turn("dial", "number" to "020 7946 0018"), setOf(ToolName.DIAL), noRepair())
    checkEq(
        "dial args json is redacted for the log",
        """{"tool":"dial","args":{"number":"*** **** **18"}}""",
        dialLog.argsJsonForLog(setOf(ToolName.DIAL)),
    )
    checkEq("alarm args json is not redacted", ok.argsJson, ok.argsJsonForLog(alarmOnly))

    // ---- Benchmark prompt set ---------------------------------------------------------------
    val prompts = LlmBenchPrompts.ALL
    for (t in ToolName.entries) checkEq("bench: 20 prompts for ${t.wire}", 20, prompts.count { it.expected == t })
    checkEq("bench: 10 none prompts", 10, prompts.count { it.expected == null })
    checkEq("bench: 110 prompts", 110, prompts.size)
    checkEq("bench: prompts are unique", prompts.size, prompts.map { it.text.lowercase() }.toSet().size)
    val shortcutHits = prompts.filter { ShortcutParser.parse(it.text) != null }.map { it.text }
    checkEq("bench: no prompt is caught by the shortcut matcher", emptyList<String>(), shortcutHits)
    for (p in prompts.filter { it.expected != null }) {
        val call = p.expectedCall
        check("bench: expected call valid for \"${p.text}\"", call != null && call.tool == p.expected && ToolValidator.validate(call).isEmpty()) { "$call" }
    }
    check("bench: none prompts have no expected call", prompts.filter { it.expected == null }.all { it.expectedCall == null })
    check("argsMatch alarm", LlmBenchPrompts.argsMatch(ToolCall.SetAlarm(7, 30), ToolCall.SetAlarm(7, 30)))
    check("argsMatch alarm differs", !LlmBenchPrompts.argsMatch(ToolCall.SetAlarm(7, 30), ToolCall.SetAlarm(19, 30)))
    check("argsMatch app ignores case and spaces", LlmBenchPrompts.argsMatch(ToolCall.OpenApp("Google Maps"), ToolCall.OpenApp(" google maps ")))
    check("argsMatch app accepts the label without the vendor", LlmBenchPrompts.argsMatch(ToolCall.OpenApp("Google Maps"), ToolCall.OpenApp("maps")))
    check("argsMatch app rejects another app", !LlmBenchPrompts.argsMatch(ToolCall.OpenApp("maps"), ToolCall.OpenApp("camera")))
    check("argsMatch dial normalizes", LlmBenchPrompts.argsMatch(ToolCall.Dial("020 7946 0018"), ToolCall.Dial("02079460018")))
    check("argsMatch other tool", !LlmBenchPrompts.argsMatch(ToolCall.Flashlight(true), ToolCall.StartTimer(5)))
}
