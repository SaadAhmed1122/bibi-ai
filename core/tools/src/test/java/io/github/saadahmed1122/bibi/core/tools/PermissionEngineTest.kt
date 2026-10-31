package io.github.saadahmed1122.bibi.core.tools


// Permission Engine policy table (PRD §16.4, FR-124): risk class × taint × lock state, plus the
// confirmation lifecycle (timeout cancels, single use, no chaining).

private val SAFE_CALLS = listOf(
    ToolCall.OpenApp("maps"),
    ToolCall.SetAlarm(7, 30),
    ToolCall.StartTimer(300),
    ToolCall.Flashlight(true),
)
private val DIAL = ToolCall.Dial("0123 456 789")

fun permissionEngineTests() {
    var now = 1_000_000L
    val engine = PermissionEngine(nowMs = { now }, timeoutMs = 30_000)
    val typed = PolicyContext(setOf(Provenance.USER_TYPED), deviceLocked = false)
    val tainted = PolicyContext(setOf(Provenance.USER_TYPED, Provenance.NOTIFICATION), deviceLocked = false)
    val locked = PolicyContext(setOf(Provenance.USER_TYPED), deviceLocked = true)
    val lockedTainted = PolicyContext(setOf(Provenance.USER_TYPED, Provenance.FILE), deviceLocked = true)

    // Risk classes (open item 8 default: dial is SENSITIVE).
    for (c in SAFE_CALLS) checkEq("risk ${c.tool.wire} is SAFE", Risk.SAFE, c.tool.risk)
    checkEq("risk dial is SENSITIVE", Risk.SENSITIVE, DIAL.tool.risk)

    // Policy table.
    for (c in SAFE_CALLS) {
        checkEq("SAFE ${c.tool.wire}, typed, unlocked → allow", Decision.Allow(c), engine.evaluate(c, typed))
        check("SAFE ${c.tool.wire}, tainted → confirmation", engine.evaluate(c, tainted) is Decision.NeedsConfirmation) { "${engine.evaluate(c, tainted)}" }
        checkEq("SAFE ${c.tool.wire}, typed, locked → allow (display-level action)", Decision.Allow(c), engine.evaluate(c, locked))
        check("SAFE ${c.tool.wire}, tainted + locked → deny", engine.evaluate(c, lockedTainted) is Decision.Deny) { "${engine.evaluate(c, lockedTainted)}" }
    }
    check("SENSITIVE dial, typed → confirmation", engine.evaluate(DIAL, typed) is Decision.NeedsConfirmation) { "" }
    check("SENSITIVE dial, tainted → confirmation", engine.evaluate(DIAL, tainted) is Decision.NeedsConfirmation) { "" }
    check("SENSITIVE dial, locked → deny", engine.evaluate(DIAL, locked) is Decision.Deny) { "" }
    check("SENSITIVE dial, locked + tainted → deny", engine.evaluate(DIAL, lockedTainted) is Decision.Deny) { "" }
    for (p in listOf(Provenance.NOTIFICATION, Provenance.FILE, Provenance.WEB)) {
        val d = engine.evaluate(ToolCall.Flashlight(true), PolicyContext(setOf(p)))
        check("taint from $p upgrades SAFE", d is Decision.NeedsConfirmation && d.request.effectiveRisk == Risk.SENSITIVE) { "$d" }
    }
    for (p in listOf(Provenance.USER_TYPED, Provenance.USER_VOICE, Provenance.MEMORY, Provenance.SYSTEM)) {
        checkEq("no taint from $p", Decision.Allow(ToolCall.Flashlight(true)), engine.evaluate(ToolCall.Flashlight(true), PolicyContext(setOf(p))))
    }
    check("kill switch denies SAFE", engine.evaluate(ToolCall.Flashlight(true), typed.copy(actionsEnabled = false)) is Decision.Deny) { "" }
    check("kill switch denies SENSITIVE", engine.evaluate(DIAL, typed.copy(actionsEnabled = false)) is Decision.Deny) { "" }
    check("invalid parameters denied even if SAFE", engine.evaluate(ToolCall.StartTimer(0), typed) is Decision.Deny) { "" }
    check("invalid number denied", engine.evaluate(ToolCall.Dial("*#06#"), typed) is Decision.Deny) { "" }

    // Confirmation card shows the resolved target, not a paraphrase (PRD §16.6 rule 5).
    val dialReq = (engine.evaluate(DIAL, typed) as Decision.NeedsConfirmation).request
    check("dial summary shows normalized number", dialReq.summary.contains("0123456789")) { dialReq.summary }
    check("dial summary says the user presses Call", dialReq.summary.contains("press Call")) { dialReq.summary }
    val appReq = (engine.evaluate(ToolCall.OpenApp("maps"), tainted, resolvedTarget = "Maps (com.google.android.apps.maps)") as Decision.NeedsConfirmation).request
    check("open_app summary shows resolved target", appReq.summary.contains("com.google.android.apps.maps")) { appReq.summary }
    checkEq("deadline = now + 30 s", now + 30_000, dialReq.deadlineMs)

    // The policy-table calls above left unanswered requests: once their deadline passes they all expire.
    val leftOver = engine.pendingCount()
    now += 30_000
    checkEq("expire clears every unanswered request", leftOver, engine.expire().size)

    // Lifecycle: confirm within time → execute once.
    val r1 = (engine.evaluate(DIAL, typed) as Decision.NeedsConfirmation).request
    now += 5_000
    checkEq("confirm in time → execute", Resolution.Execute(DIAL), engine.respond(r1.id, UserResponse.CONFIRM, deviceLocked = false))
    check("second confirm of same request refused (single use, no chaining)",
        engine.respond(r1.id, UserResponse.CONFIRM, deviceLocked = false) is Resolution.Cancelled) { "" }

    // Cancel → cancelled.
    val r2 = (engine.evaluate(DIAL, typed) as Decision.NeedsConfirmation).request
    check("cancel → cancelled", engine.respond(r2.id, UserResponse.CANCEL, deviceLocked = false) is Resolution.Cancelled) { "" }

    // Timeout: silence is never consent; a late confirm is refused.
    val r3 = (engine.evaluate(DIAL, typed) as Decision.NeedsConfirmation).request
    now += 30_000
    val late = engine.respond(r3.id, UserResponse.CONFIRM, deviceLocked = false)
    check("confirm at deadline → cancelled (timeout)", late is Resolution.Cancelled && late.reason.contains("timeout")) { "$late" }

    val r4 = (engine.evaluate(DIAL, typed) as Decision.NeedsConfirmation).request
    now += 29_999
    checkEq("expire before deadline → nothing", emptyList<Long>(), engine.expire().map { it.id })
    now += 1
    checkEq("expire at deadline → cancels it", listOf(r4.id), engine.expire().map { it.id })
    check("confirm after expire refused", engine.respond(r4.id, UserResponse.CONFIRM, deviceLocked = false) is Resolution.Cancelled) { "" }
    checkEq("no pending left", 0, engine.pendingCount())

    // Device locked between card and tap → refused.
    val r5 = (engine.evaluate(DIAL, typed) as Decision.NeedsConfirmation).request
    check("confirm while locked → cancelled", engine.respond(r5.id, UserResponse.CONFIRM, deviceLocked = true) is Resolution.Cancelled) { "" }

    // One confirmation covers exactly one call: confirming r6 never executes r7.
    val r6 = (engine.evaluate(DIAL, typed) as Decision.NeedsConfirmation).request
    val r7 = (engine.evaluate(ToolCall.Dial("555 0100"), typed) as Decision.NeedsConfirmation).request
    check("distinct request ids", r6.id != r7.id) { "" }
    checkEq("confirm r6 executes r6's call only", Resolution.Execute(DIAL), engine.respond(r6.id, UserResponse.CONFIRM, deviceLocked = false))
    checkEq("r7 still pending", 1, engine.pendingCount())
    check("unknown id refused", engine.respond(999_999, UserResponse.CONFIRM, deviceLocked = false) is Resolution.Cancelled) { "" }
}
