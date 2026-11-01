package io.github.saadahmed1122.bibi.core.tools

import java.util.Locale

// Pass 2 of the real-LLM path (Decision 2026-10-30): deterministic slot extraction after the
// Decision Maker's label. Every result goes through the same parser → validator path; a missing or
// ambiguous slot gives no call (ask the user), never a guess.

/** A launcher list holding every benchmark app plus distractors (Google, YouTube Music, Phone…). Assumed, not a real phone's [VERIFY]. */
val BENCH_LAUNCHER = listOf(
    AppEntry("Maps", "com.google.android.apps.maps"),
    AppEntry("Gmail", "com.google.android.gm"),
    AppEntry("Camera", "com.android.camera2"),
    AppEntry("Settings", "com.android.settings"),
    AppEntry("YouTube", "com.google.android.youtube"),
    AppEntry("YouTube Music", "com.google.android.apps.youtube.music"),
    AppEntry("Calendar", "com.google.android.calendar"),
    AppEntry("Photos", "com.google.android.apps.photos"),
    AppEntry("Play Store", "com.android.vending"),
    AppEntry("Chrome", "com.android.chrome"),
    AppEntry("Calculator", "com.google.android.calculator"),
    AppEntry("WhatsApp", "com.whatsapp"),
    AppEntry("Clock", "com.google.android.deskclock"),
    AppEntry("Spotify", "com.spotify.music"),
    AppEntry("Files", "com.google.android.apps.nbu.files"),
    AppEntry("Messages", "com.google.android.apps.messaging"),
    AppEntry("Gallery", "com.sec.android.gallery3d"),
    AppEntry("Drive", "com.google.android.apps.docs"),
    AppEntry("Instagram", "com.instagram.android"),
    AppEntry("Samsung Notes", "com.samsung.android.app.notes"),
    AppEntry("Weather", "com.sec.android.daemonapp"),
    AppEntry("Google", "com.google.android.googlequicksearchbox"),
    AppEntry("Phone", "com.google.android.dialer"),
    AppEntry("Contacts", "com.google.android.contacts"),
)

private fun filled(tool: ToolName, text: String, apps: AppResolver? = null): Map<String, Any?>? =
    (SlotExtractor.extract(tool, text, apps) as? Slots.Filled)?.args

private fun alarmOf(text: String) = filled(ToolName.SET_ALARM, text)?.let { it["hour"] to it["minute"] }

private fun timerOf(text: String) = filled(ToolName.START_TIMER, text)?.get("seconds")

private fun missing(tool: ToolName, text: String, apps: AppResolver? = null) =
    SlotExtractor.extract(tool, text, apps) is Slots.Missing

fun slotExtractorTests() {
    val apps = AppResolver(BENCH_LAUNCHER)

    // ---- set_alarm ------------------------------------------------------------------------------
    checkEq("alarm 6:45", 6L to 45L, alarmOf("wake me at 6:45"))
    checkEq("alarm seven thirty", 7L to 30L, alarmOf("seven thirty"))
    checkEq("alarm half six (British)", 6L to 30L, alarmOf("alarm for half six"))
    checkEq("alarm 6 am", 6L to 0L, alarmOf("get me up at 6 am"))
    checkEq("alarm 6 p.m.", 18L to 0L, alarmOf("alarm at 6 p.m."))
    checkEq("alarm 12 am is midnight", 0L to 0L, alarmOf("alarm at 12 am"))
    checkEq("alarm 12 pm is noon", 12L to 0L, alarmOf("alarm at 12pm"))
    checkEq("alarm quarter past 7", 7L to 15L, alarmOf("quarter past 7"))
    checkEq("alarm at 7", 7L to 0L, alarmOf("an alarm at 7"))
    checkEq("alarm twenty five to eight", 7L to 35L, alarmOf("alarm at twenty-five to eight"))
    checkEq("alarm seven oh five", 7L to 5L, alarmOf("wake me at seven oh five"))
    checkEq("alarm 7 o'clock", 7L to 0L, alarmOf("seven o'clock alarm"))
    checkEq("alarm midnight", 0L to 0L, alarmOf("alarm at midnight"))
    checkEq("alarm evening context → pm", 19L to 30L, alarmOf("alarm at 7:30 this evening"))
    checkEq("alarm 24-hour kept with evening context", 22L to 0L, alarmOf("alarm at 22:00 tonight"))
    check("alarm: no time → missing", missing(ToolName.SET_ALARM, "set an alarm"))
    check("alarm: negation with no time → missing", missing(ToolName.SET_ALARM, "don't set an alarm tonight"))
    check("alarm: two different times → missing (ambiguous)", missing(ToolName.SET_ALARM, "alarm at 6 or 7"))
    // A duration and no clock time under the set_alarm label is a timer (vivo run: 3 timer prompts labelled set_alarm, RESULTS.md §6).
    val asTimer = { text: String -> (SlotExtractor.extract(ToolName.SET_ALARM, text, null) as? Slots.Filled)?.let { it.tool to it.args["seconds"] } }
    checkEq("alarm: 'wake me in 20 minutes' → 20 min timer", ToolName.START_TIMER to 1200L, asTimer("wake me in 20 minutes"))
    checkEq("alarm: 'for 10 minutes' is a timer, not 10:00", ToolName.START_TIMER to 600L, asTimer("alarm for 10 minutes"))
    checkEq("alarm: 'let me know when 3 minutes are up' → timer", ToolName.START_TIMER to 180L, asTimer("let me know when 3 minutes are up"))
    checkEq("alarm: 'a quarter of an hour on the clock' → timer", ToolName.START_TIMER to 900L, asTimer("give me a quarter of an hour on the clock"))
    check("alarm: a clock time plus a duration stays ambiguous", missing(ToolName.SET_ALARM, "alarm at 7 for 10 minutes"))
    check("alarm: a duration with an invalid clock time stays missing", missing(ToolName.SET_ALARM, "alarm at 25:00 in 10 minutes"))
    check("alarm: 13 pm is not a time → missing", missing(ToolName.SET_ALARM, "alarm at 13 pm"))
    check("alarm: 25:00 → missing", missing(ToolName.SET_ALARM, "alarm at 25:00"))
    check("alarm: morning and evening → missing", missing(ToolName.SET_ALARM, "alarm at 7 in the morning or in the evening"))
    check("alarm: question about time → missing", missing(ToolName.SET_ALARM, "what time is it in Tokyo"))

    // ---- start_timer ----------------------------------------------------------------------------
    checkEq("timer ten minutes", 600L, timerOf("ten minutes"))
    checkEq("timer 8 min", 480L, timerOf("8 min please"))
    checkEq("timer an hour and a half", 5400L, timerOf("an hour and a half"))
    checkEq("timer one and a half hours", 5400L, timerOf("one and a half hours"))
    checkEq("timer 90 seconds", 90L, timerOf("90 seconds"))
    checkEq("timer half an hour", 1800L, timerOf("half an hour"))
    checkEq("timer quarter of an hour", 900L, timerOf("a quarter of an hour"))
    checkEq("timer three quarters of an hour", 2700L, timerOf("three quarters of an hour"))
    checkEq("timer 1 hour 30 minutes", 5400L, timerOf("1 hour 30 minutes"))
    checkEq("timer 2 minutes and 30 seconds", 150L, timerOf("2 minutes and 30 seconds"))
    checkEq("timer twenty-five minutes", 1500L, timerOf("twenty-five minutes"))
    checkEq("timer 1.5 hours", 5400L, timerOf("1.5 hours"))
    checkEq("timer a minute", 60L, timerOf("a minute"))
    check("timer: no unit → missing", missing(ToolName.START_TIMER, "set a timer for 10"))
    check("timer: no duration → missing", missing(ToolName.START_TIMER, "start a timer"))
    check("timer: two separate durations → missing (ambiguous)", missing(ToolName.START_TIMER, "5 minutes or 10 minutes"))
    check("timer: question → missing", missing(ToolName.START_TIMER, "how long should I boil an egg"))
    check("timer: half a second is not whole seconds → missing", missing(ToolName.START_TIMER, "half a second"))

    // ---- flashlight -----------------------------------------------------------------------------
    checkEq("torch dark → on", true, filled(ToolName.FLASHLIGHT, "it's so dark")?.get("on"))
    checkEq("torch on", true, filled(ToolName.FLASHLIGHT, "flashlight on")?.get("on"))
    checkEq("torch off", false, filled(ToolName.FLASHLIGHT, "torch off")?.get("on"))
    checkEq("torch too bright → off", false, filled(ToolName.FLASHLIGHT, "the torch is too bright")?.get("on"))
    checkEq("torch 'still on, turn it off' → off", false, filled(ToolName.FLASHLIGHT, "the torch is still on, turn it off")?.get("on"))
    checkEq("torch 'close' → off (owner's try on the vivo)", false, filled(ToolName.FLASHLIGHT, "can you close the flashlight for me")?.get("on"))
    checkEq("torch bare request → on", true, filled(ToolName.FLASHLIGHT, "torch")?.get("on"))
    check("torch: negated off → missing", missing(ToolName.FLASHLIGHT, "don't turn off the torch"))
    check("torch: negated on → missing", missing(ToolName.FLASHLIGHT, "do not turn on the flashlight"))
    check("torch: no light word and no on/off → missing (no default for a mislabel)", missing(ToolName.FLASHLIGHT, "tell me a joke"))

    // ---- dial -----------------------------------------------------------------------------------
    checkEq("dial strips spaces", "02079460018", filled(ToolName.DIAL, "call 020 7946 0018")?.get("number"))
    checkEq("dial keeps + and strips dashes", "+12025550147", filled(ToolName.DIAL, "phone +1-202-555-0147")?.get("number"))
    checkEq("dial keeps the leading 0", "07700900123", filled(ToolName.DIAL, "ring 07700 900123 please")?.get("number"))
    checkEq("dial ignores a short number elsewhere", "07700900123", filled(ToolName.DIAL, "at 5 dial 07700 900123")?.get("number"))
    check("dial: no number → missing", missing(ToolName.DIAL, "call mum"))
    check("dial: two numbers → missing (ambiguous)", missing(ToolName.DIAL, "call 020 7946 0018 or 020 7946 0019"))
    check("dial: spelled-out number unsupported → missing", missing(ToolName.DIAL, "call oh two oh seven"))
    check("dial: USSD code → no valid call", SlotToolRouter.route(ToolSelection.Expose(ToolName.DIAL), "dial *#06#", apps).route !is LlmRoute.Call)

    // ---- open_app -------------------------------------------------------------------------------
    checkEq("app exact label", "camera", filled(ToolName.OPEN_APP, "show me the camera", apps)?.get("app"))
    checkEq("app alias 'google maps' beats 'google'", "google maps", filled(ToolName.OPEN_APP, "bring up google maps", apps)?.get("app"))
    checkEq("app vendor prefix 'google drive' → drive", "drive", filled(ToolName.OPEN_APP, "get me into google drive", apps)?.get("app"))
    checkEq("app vendor-less 'notes' → Samsung Notes", "notes", filled(ToolName.OPEN_APP, "navigate to the notes app", apps)?.get("app"))
    checkEq("app longest label wins (YouTube Music)", "youtube music", filled(ToolName.OPEN_APP, "open youtube music", apps)?.get("app"))
    checkEq("app bare 'google'", "google", filled(ToolName.OPEN_APP, "open google", apps)?.get("app"))
    checkEq("app 'google app' → google", "google", filled(ToolName.OPEN_APP, "open the google app", apps)?.get("app"))
    // vivo run: Drive not installed, "google drive" opened Google. A vendor word + an unknown name asks instead.
    val noDrive = AppResolver(BENCH_LAUNCHER.filter { it.label != "Drive" })
    check("app 'google drive' without Drive → missing, not Google", missing(ToolName.OPEN_APP, "get me into google drive", noDrive))
    check("app 'samsung health' without it → missing", missing(ToolName.OPEN_APP, "open samsung health", noDrive))
    check("app: two apps → missing (ambiguous)", missing(ToolName.OPEN_APP, "open camera and maps", apps))
    check("app: not installed → missing", missing(ToolName.OPEN_APP, "open tiktok", apps))
    check("app: no launcher index → missing", missing(ToolName.OPEN_APP, "open camera", null))
    check("app: a phone number in the text → missing (that is dial)", missing(ToolName.OPEN_APP, "open the phone and call 020 7946 0018", apps))
    check("app: prefixes are not matched ('me' is not Messages)", missing(ToolName.OPEN_APP, "show me something", apps))
    val withEmail = AppResolver(BENCH_LAUNCHER + AppEntry("Email", "com.samsung.android.email.provider"))
    check("app: 'email in gmail' with an Email app installed → missing (ambiguous)", missing(ToolName.OPEN_APP, "I want to check my email in gmail", withEmail))
    for (key in listOf("camera", "google maps", "drive", "notes", "youtube music")) {
        val p = SlotExtractor.extract(ToolName.OPEN_APP, "open $key", apps) as? Slots.Filled
        check("app arg \"$key\" resolves to the matched app", p != null && apps.resolve(p.args["app"] as String) != null)
    }

    // ---- router: same parser/validator path, exposed-subset rule ----------------------------------
    val alarmSel = ToolSelection.Expose(ToolName.SET_ALARM)
    val ok = SlotToolRouter.route(alarmSel, "wake me at 6:45", apps)
    checkEq("router: slots → Call", LlmRoute.Call(ToolCall.SetAlarm(6, 45), attempts = 1), ok.route)
    checkEq("router: source is slots", CallSource.SLOTS, ok.first?.source)
    checkEq("router: parser input is the {tool,args} JSON", """{"tool":"set_alarm","args":{"hour":6,"minute":45}}""", ok.argsJson)
    check("router: args valid without repair", ok.argsValid && !ok.repaired)
    val miss = SlotToolRouter.route(alarmSel, "set an alarm", apps)
    check("router: missing slot → AskUser, no call", miss.route is LlmRoute.AskUser && miss.first == null)
    val tooLong = SlotToolRouter.route(ToolSelection.Expose(ToolName.START_TIMER), "a timer for 30 hours", apps)
    check("router: out-of-range value → validator rejects → AskUser", tooLong.route is LlmRoute.AskUser) { "${tooLong.route}" }
    val wrong = SlotToolRouter.fromSlots(Slots.Filled(ToolName.DIAL, mapOf("number" to "02079460018")), setOf(ToolName.FLASHLIGHT))
    check("router: a tool other than the Decision Maker's is rejected", wrong.route is LlmRoute.Rejected)
    checkEq("router: dial log is redacted", """{"tool":"dial","args":{"number":"*********18"}}""",
        SlotToolRouter.route(ToolSelection.Expose(ToolName.DIAL), "ring 020 7946 0018", apps).argsJsonForLog(setOf(ToolName.DIAL)))

    // ---- The 110 benchmark prompts, given the CORRECT tool label (offline ceiling) ----------------
    val prompts = LlmBenchPrompts.ALL
    val report = StringBuilder("SLOT CEILING (correct tool label, assumed launcher list of ${BENCH_LAUNCHER.size} apps):\n")
    var totalMatch = 0
    var totalN = 0
    for (t in ToolName.entries) {
        val ps = prompts.filter { it.expected == t }
        var match = 0
        val misses = mutableListOf<String>()
        for (p in ps) {
            val r = SlotToolRouter.route(ToolSelection.Expose(t), p.text, apps)
            val call = (r.route as? LlmRoute.Call)?.call
            if (call != null && LlmBenchPrompts.argsMatch(p.expectedCall!!, call)) match++ else misses += "\"${p.text}\" → ${call ?: r.route}"
        }
        totalMatch += match
        totalN += ps.size
        report.append(String.format(Locale.US, "  %-12s %2d/%2d args match\n", t.wire, match, ps.size))
        misses.forEach { report.append("      miss: $it\n") }
        check("bench ceiling ${t.wire}: at least 18/20 args match", match >= 18) { "$match/20 ${misses.joinToString()}" }
    }
    report.append(String.format(Locale.US, "  total        %d/%d", totalMatch, totalN))
    println(report)

    // "none" prompts the Decision Maker might mislabel: with any tool label, most give no call.
    val noneTexts = prompts.filter { it.expected == null }.map { it.text }
    val noneCalls = noneTexts.flatMap { text -> ToolName.entries.map { t -> t to SlotToolRouter.route(ToolSelection.Expose(t), text, apps) } }
        .filter { it.second.route is LlmRoute.Call }
        .map { "${it.first.wire}: ${(it.second.route as LlmRoute.Call).call}" }
    checkEq("none prompts give no call under any wrong tool label", emptyList<String>(), noneCalls)
}

/** Info + one check: action prompts under a WRONG tool label (Decision Maker confusions). */
fun slotWrongLabelTests() {
    val apps = AppResolver(BENCH_LAUNCHER)
    val calls = LlmBenchPrompts.ALL.filter { it.expected != null }.flatMap { p ->
        ToolName.entries.filter { it != p.expected }.mapNotNull { t ->
            val r = SlotToolRouter.route(ToolSelection.Expose(t), p.text, apps)
            (r.route as? LlmRoute.Call)?.let { "${p.expected!!.wire}→${t.wire}: \"${p.text}\" → ${it.call.redacted()}" }
        }
    }
    println("WRONG-LABEL calls (${calls.size} of ${LlmBenchPrompts.ALL.count { it.expected != null } * 4} prompt×wrong-tool pairs):")
    calls.forEach { println("  $it") }
    // A wrong-label call is one whose tool is not the prompt's tool; set_alarm → start_timer re-routing is allowed.
    val wrongTool = calls.filterNot { it.startsWith("start_timer→set_alarm") && it.contains("→ start_timer ") }
    check("wrong-tool calls stay rare (at most 2 of 400 pairs)", wrongTool.size <= 2) { "$wrongTool" }
    // The confusion seen on the vivo (RESULTS.md §3, §6): timer prompts labelled set_alarm never set an alarm.
    checkEq("timer prompts labelled set_alarm set no alarm", emptyList<String>(), calls.filter { it.startsWith("start_timer→set_alarm") && !it.contains("→ start_timer ") })
    val rerouted = LlmBenchPrompts.ALL.filter { it.expected == ToolName.START_TIMER }.count { p ->
        val call = (SlotToolRouter.route(ToolSelection.Expose(ToolName.SET_ALARM), p.text, apps).route as? LlmRoute.Call)?.call
        call != null && LlmBenchPrompts.argsMatch(p.expectedCall!!, call)
    }
    println("REROUTED: $rerouted/20 timer prompts labelled set_alarm start the right timer")
    check("timer prompts labelled set_alarm: at least 15/20 start the right timer", rerouted >= 15) { "$rerouted" }
}
