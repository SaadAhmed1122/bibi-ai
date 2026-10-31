package io.github.saadahmed1122.bibi.core.tools


// Tool-call parser, validator and the one-repair rule (PRD §13.5 layer 4, FR-120/121).

private fun valid(output: String): ToolCall? = (ToolCallParser.parse(output) as? ToolCallParse.Valid)?.call

private fun errors(output: String): List<String> = (ToolCallParser.parse(output) as? ToolCallParse.Invalid)?.errors.orEmpty()

private fun rejects(name: String, output: String, errorContains: String) {
    val e = errors(output)
    check("rejects $name", e.any { it.contains(errorContains) }) { "errors=$e for $output" }
}

fun toolCallParserTests() {
    // Valid calls for each of the five tools.
    checkEq("json open_app", ToolCall.OpenApp("maps"), valid("""{"tool":"open_app","args":{"app":"maps"}}"""))
    checkEq("json set_alarm", ToolCall.SetAlarm(7, 30), valid("""{"tool": "set_alarm", "args": {"hour": 7, "minute": 30}}"""))
    checkEq("json start_timer", ToolCall.StartTimer(300), valid("""{"tool":"start_timer","args":{"seconds":300}}"""))
    checkEq("json flashlight", ToolCall.Flashlight(false), valid("""{"tool":"flashlight","args":{"on":false}}"""))
    checkEq("json dial", ToolCall.Dial("+44 20 7946 0958"), valid("""{"tool":"dial","args":{"number":"+44 20 7946 0958"}}"""))
    checkEq(
        "json wrapped in <tool_call> with surrounding text",
        ToolCall.StartTimer(60),
        valid("Sure!\n<tool_call>{\"tool\":\"start_timer\",\"args\":{\"seconds\":60}}</tool_call>"),
    )
    checkEq("json with escapes in string", ToolCall.OpenApp("café"), valid("""{"tool":"open_app","args":{"app":"café"}}"""))
    checkEq("no tool call in plain text", ToolCallParse.NoToolCall, ToolCallParser.parse("It's 7:30 now."))

    // Malformed JSON.
    rejects("truncated json", """{"tool":"start_timer","args":{"seconds":300}""", "malformed JSON")
    rejects("trailing garbage", """{"tool":"flashlight","args":{"on":true}} extra""", "malformed JSON")
    rejects("single quotes", """{'tool':'flashlight','args':{'on':true}}""", "malformed JSON")
    rejects("top-level array", """[{"tool":"flashlight"}]""", "must be a JSON object")
    rejects("over-long output", "{\"tool\":\"open_app\",\"args\":{\"app\":\"" + "a".repeat(5000) + "\"}}", "too long")
    rejects("deep nesting", "{\"tool\":\"flashlight\",\"args\":" + "{\"a\":".repeat(40) + "1" + "}".repeat(40) + "}", "nested")

    // Schema: tool name and structure.
    rejects("unknown tool", """{"tool":"send_sms","args":{"to":"123"}}""", "unknown tool")
    rejects("raw intent tool", """{"tool":"start_activity","args":{"action":"android.intent.action.CALL"}}""", "unknown tool")
    rejects("missing tool", """{"args":{"on":true}}""", "missing \"tool\"")
    rejects("missing args", """{"tool":"flashlight"}""", "missing \"args\"")
    rejects("extra top-level key", """{"tool":"flashlight","args":{"on":true},"intent":"x"}""", "unexpected key \"intent\"")
    rejects("extra arg key", """{"tool":"dial","args":{"number":"555 1234","action":"CALL"}}""", "unexpected argument \"action\"")
    rejects("missing arg", """{"tool":"set_alarm","args":{"hour":7}}""", "missing argument \"minute\"")

    // Schema: types and ranges.
    rejects("string for int", """{"tool":"start_timer","args":{"seconds":"five"}}""", "\"seconds\" must be an integer")
    rejects("fraction for int", """{"tool":"start_timer","args":{"seconds":5.5}}""", "\"seconds\" must be an integer")
    rejects("timer zero", """{"tool":"start_timer","args":{"seconds":0}}""", "seconds must be 1..86400")
    rejects("timer > 24 h", """{"tool":"start_timer","args":{"seconds":86401}}""", "seconds must be 1..86400")
    rejects("huge number", """{"tool":"start_timer","args":{"seconds":99999999999999999999}}""", "\"seconds\" must be an integer")
    rejects("hour 24", """{"tool":"set_alarm","args":{"hour":24,"minute":0}}""", "hour must be 0..23")
    rejects("minute 60", """{"tool":"set_alarm","args":{"hour":7,"minute":60}}""", "minute must be 0..59")
    rejects("flashlight string", """{"tool":"flashlight","args":{"on":"yes"}}""", "\"on\" must be true or false")

    // Schema: phone numbers (literal only; PRD §16.6 argument sanitization).
    rejects("dial letters", """{"tool":"dial","args":{"number":"mom"}}""", "number")
    rejects("dial MMI code", """{"tool":"dial","args":{"number":"*#06#"}}""", "number")
    rejects("dial tel: uri", """{"tool":"dial","args":{"number":"tel:5551234"}}""", "number")
    rejects("dial too short", """{"tool":"dial","args":{"number":"12"}}""", "number")
    rejects("dial too long", """{"tool":"dial","args":{"number":"1234567890123456"}}""", "number")
    rejects("dial plus inside", """{"tool":"dial","args":{"number":"12+34567"}}""", "number")
    rejects("dial number as int", """{"tool":"dial","args":{"number":5551234}}""", "\"number\" must be a string")
    checkEq("normalize number", "+442079460958", ToolValidator.normalizeNumber("+44 (20) 7946-0958"))
    checkEq("normalize rejects pause chars", null, ToolValidator.normalizeNumber("555,1234"))

    // Schema: open_app target (app name or package name; executor resolves against the launcher list).
    rejects("package bad pattern", """{"tool":"open_app","args":{"app":"com..evil"}}""", "app")
    rejects("package with slash", """{"tool":"open_app","args":{"app":"com.evil/.Main"}}""", "app")
    rejects("intent uri as app", """{"tool":"open_app","args":{"app":"intent:#Intent;action=android.intent.action.CALL;end"}}""", "app")
    rejects("empty app", """{"tool":"open_app","args":{"app":""}}""", "app")
    rejects("app name too long", "{\"tool\":\"open_app\",\"args\":{\"app\":\"" + "x".repeat(41) + "\"}}", "app")
    checkEq("package name accepted", ToolCall.OpenApp("com.google.android.apps.maps"), valid("""{"tool":"open_app","args":{"app":"com.google.android.apps.maps"}}"""))

    // Repair message: one message that lists the errors and the schema of the five tools.
    val bad = ToolCallParser.parse("""{"tool":"start_timer","args":{"seconds":"five"}}""") as ToolCallParse.Invalid
    val repair = bad.repairPrompt()
    check("repair prompt names the error", repair.contains("\"seconds\" must be an integer")) { repair }
    check("repair prompt lists all five tools", listOf("open_app", "set_alarm", "start_timer", "flashlight", "dial").all { repair.contains(it) }) { repair }

    // Router: at most one repair attempt, then ask the user.
    var calls = 0
    val repaired = ToolRouter.fromModel("""{"tool":"start_timer","args":{"seconds":"five"}}""") { prompt ->
        calls++
        check("repair callback gets the repair prompt", prompt.contains("must be an integer")) { prompt }
        """{"tool":"start_timer","args":{"seconds":300}}"""
    }
    checkEq("router: repaired call", RouterOutcome.Call(ToolCall.StartTimer(300), attempts = 2), repaired)
    checkEq("router: one repair call", 1, calls)

    calls = 0
    val gaveUp = ToolRouter.fromModel("not json {") { calls++; "still {not json" }
    check("router: second failure asks the user", gaveUp is RouterOutcome.AskUser) { "$gaveUp" }
    checkEq("router: still only one repair call", 1, calls)

    calls = 0
    val first = ToolRouter.fromModel("""{"tool":"flashlight","args":{"on":true}}""") { calls++; "" }
    checkEq("router: valid first time", RouterOutcome.Call(ToolCall.Flashlight(true), attempts = 1), first)
    checkEq("router: no repair when valid", 0, calls)
    checkEq("router: no tool call passes through", RouterOutcome.NoToolCall, ToolRouter.fromModel("Hello!") { calls++; "" })

    // Encoder round trip (FakeLlm and logs use it).
    for (call in listOf(ToolCall.OpenApp("maps"), ToolCall.SetAlarm(6, 5), ToolCall.StartTimer(42), ToolCall.Flashlight(true), ToolCall.Dial("+1 555 0100"), ToolCall.OpenApp("a \"quoted\" \\ name"))) {
        val json = ToolCallJson.encode(call)
        val back = ToolCallParser.parse(json)
        if (call is ToolCall.OpenApp && call.app.contains('"')) {
            check("encoder escapes quotes (then validator rejects the name)", back is ToolCallParse.Invalid) { "$back" }
        } else {
            checkEq("round trip $json", ToolCallParse.Valid(call), back)
        }
    }

    // Redaction for logs (FR-122, PRD §16.4: keep the last 2 digits).
    checkEq("dial redacted", "dial number=" + "*".repeat(11) + "58", ToolCall.Dial("+44 20 7946 0958").redacted())
    checkEq("timer not redacted", "start_timer seconds=300", ToolCall.StartTimer(300).redacted())

    // Fake LLM: fixed mappings, a deliberately malformed first answer that repairs once.
    val fake = FakeLlm()
    checkEq("fake llm mapping", ToolCallParse.Valid(ToolCall.Flashlight(true)), ToolCallParser.parse(fake.complete("it's dark in here")))
    val fakeOutcome = ToolRouter.fromModel(fake.complete("broken timer test")) { fake.repair(it) }
    checkEq("fake llm malformed then repaired", RouterOutcome.Call(ToolCall.StartTimer(300), attempts = 2), fakeOutcome)
    checkEq("fake llm unknown text → no tool call", ToolCallParse.NoToolCall, ToolCallParser.parse(fake.complete("tell me a joke")))
}
