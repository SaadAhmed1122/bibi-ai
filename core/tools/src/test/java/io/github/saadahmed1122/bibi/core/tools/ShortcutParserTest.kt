package io.github.saadahmed1122.bibi.core.tools


// Shortcut matcher phrase coverage (PRD §13.5 layer 3). The matcher is anchored: the whole utterance
// must be a command, so negations and compound sentences fall through to the LLM.

fun shortcutParserTests() {
    val positives: List<Pair<String, ToolCall>> = listOf(
        // timer
        "set a timer for 5 minutes" to ToolCall.StartTimer(300),
        "Set a timer for 5 minutes." to ToolCall.StartTimer(300),
        "timer 10 min" to ToolCall.StartTimer(600),
        "start a 30 second timer" to ToolCall.StartTimer(30),
        "5 minute timer" to ToolCall.StartTimer(300),
        "set timer for 1 hour 30 minutes" to ToolCall.StartTimer(5400),
        "timer for 90 seconds" to ToolCall.StartTimer(90),
        "please set a timer for twenty minutes" to ToolCall.StartTimer(1200),
        "set a timer for an hour" to ToolCall.StartTimer(3600),
        "hey bibi, timer for 2 mins" to ToolCall.StartTimer(120),
        "set a timer for 1 min 15 sec" to ToolCall.StartTimer(75),
        // alarm
        "wake me at 7:30" to ToolCall.SetAlarm(7, 30),
        "wake me up at 6 am" to ToolCall.SetAlarm(6, 0),
        "set an alarm for 7:30 pm" to ToolCall.SetAlarm(19, 30),
        "alarm at 18:45" to ToolCall.SetAlarm(18, 45),
        "set alarm for 7am" to ToolCall.SetAlarm(7, 0),
        "set an alarm for 12 am" to ToolCall.SetAlarm(0, 0),
        "set an alarm for 12:15 pm" to ToolCall.SetAlarm(12, 15),
        "Wake me up at 6.45" to ToolCall.SetAlarm(6, 45),
        // flashlight
        "flashlight on" to ToolCall.Flashlight(true),
        "flashlight off" to ToolCall.Flashlight(false),
        "turn on the flashlight" to ToolCall.Flashlight(true),
        "turn off torch" to ToolCall.Flashlight(false),
        "torch on please" to ToolCall.Flashlight(true),
        "switch the flashlight off" to ToolCall.Flashlight(false),
        // "light" means the flashlight (owner's try on the vivo, 2026-11-01: "open the light" looked for an app)
        "open the light" to ToolCall.Flashlight(true),
        // Owner's tries on the vivo (2026-11-01): both turned the light on. "Of" is Parakeet's "off".
        "close the flashlight" to ToolCall.Flashlight(false),
        // Parakeet writes a spoken "Bibi" as "BB" (owner's try through the floating card, 2026-11-01).
        "Hey BB, turn on the flashlight." to ToolCall.Flashlight(true),
        "BB, flashlight off" to ToolCall.Flashlight(false),
        "shut the torch" to ToolCall.Flashlight(false),
        "of the flashlight" to ToolCall.Flashlight(false),
        "off the light" to ToolCall.Flashlight(false),
        "turn on the light" to ToolCall.Flashlight(true),
        "lights off" to ToolCall.Flashlight(false),
        "switch off the lights" to ToolCall.Flashlight(false),
        "open the flashlight" to ToolCall.Flashlight(true),
        // open app
        "open maps" to ToolCall.OpenApp("maps"),
        "Open Google Maps" to ToolCall.OpenApp("Google Maps"), // case kept: package names are case-sensitive
        "open com.google.android.GoogleCamera" to ToolCall.OpenApp("com.google.android.GoogleCamera"),
        "launch the camera app" to ToolCall.OpenApp("camera"),
        "start youtube" to ToolCall.OpenApp("youtube"),
        "open com.android.settings" to ToolCall.OpenApp("com.android.settings"),
        // dial (literal numbers only)
        "call 0123 456 789" to ToolCall.Dial("0123 456 789"),
        "dial +44 20 7946 0958" to ToolCall.Dial("+44 20 7946 0958"),
        "phone 555-1234" to ToolCall.Dial("555-1234"),
        "call (212) 555 1212" to ToolCall.Dial("(212) 555 1212"),
    )
    for ((phrase, expected) in positives) {
        checkEq("shortcut \"$phrase\"", expected, ShortcutParser.parse(phrase))
    }

    // Phrases that must not produce a tool call.
    val negatives = listOf(
        "what's the weather today",
        "",
        "   ",
        "set a timer", // no duration: the LLM or a clarifying question handles it
        "flashlight", // no state; toggling is not a shortcut
        "don't turn on the flashlight",
        "please don't call 0123 456 789",
        "call mom", // contacts lookup is out of MVP scope (PRD 0.9)
        "call 0123 456 789 and open maps",
        "open",
        "open the pod bay doors, now", // punctuation inside → no match
        "remind me to call 555 1234",
        "tell me about alarms",
        "call *#06#", // MMI/USSD codes are rejected
        "timer for -5 minutes",
        "wake me at 7:30 tomorrow and every monday",
    )
    for (phrase in negatives) {
        checkEq("no shortcut for \"$phrase\"", null, ShortcutParser.parse(phrase))
    }

    // Parsed but out of range: the shared validator rejects it, so the router never executes it.
    val outOfRange = listOf(
        "set an alarm for 25:00",
        "wake me at 7:75",
        "set a timer for 25 hours",
        "set a timer for 0 minutes",
        "set an alarm for 13 pm",
        "call 12",
    )
    for (phrase in outOfRange) {
        val call = ShortcutParser.parse(phrase)
        check("out of range \"$phrase\" fails validation", call == null || ToolValidator.validate(call).isNotEmpty()) {
            "parsed $call with no validation error"
        }
    }

    check("≥ 25 shortcut phrases tested", positives.size + negatives.size + outOfRange.size >= 25) { "too few" }
}
