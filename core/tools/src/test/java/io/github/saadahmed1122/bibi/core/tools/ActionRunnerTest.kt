package io.github.saadahmed1122.bibi.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ActionRunnerTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000L
    private var locked = false
    private var enabled = true
    private val executed = mutableListOf<ToolCall>()
    private var outcome: ActionOutcome = ActionOutcome.Done("ok")

    private fun runner(): Pair<ActionRunner, ToolAuditLog> {
        val audit = ToolAuditLog(tmp.newFile(), Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC))
        val r = ActionRunner(
            engine = PermissionEngine(nowMs = { now }, timeoutMs = 30_000),
            audit = audit,
            execute = { executed += it; outcome },
            deviceLocked = { locked },
            actionsEnabled = { enabled },
        )
        return r to audit
    }

    @Test
    fun aSafeActionRunsAndIsLogged() {
        val (r, audit) = runner()
        assertEquals(RunResult.Done(ToolCall.StartTimer(300), "ok"), r.submit(ToolCall.StartTimer(300)))
        assertEquals(listOf<ToolCall>(ToolCall.StartTimer(300)), executed)
        assertTrue(audit.read().contains("start_timer | {\"tool\":\"start_timer\",\"args\":{\"seconds\":300}} | done: ok"))
    }

    @Test
    fun dialWaitsForConfirmThenRuns() {
        val (r, _) = runner()
        val ask = r.submit(ToolCall.Dial("0300 1234567")) as RunResult.NeedsConfirmation
        assertTrue(executed.isEmpty())
        assertEquals(RunResult.Done(ToolCall.Dial("0300 1234567"), "ok"), r.respond(ask.request, UserResponse.CONFIRM))
    }

    @Test
    fun cancelAndTimeoutRunNothing() {
        val (r, audit) = runner()
        val first = r.submit(ToolCall.Dial("0300 1234567")) as RunResult.NeedsConfirmation
        assertTrue(r.respond(first.request, UserResponse.CANCEL) is RunResult.Cancelled)
        r.submit(ToolCall.Dial("0300 1234567"))
        now += 30_000
        assertEquals(1, r.expire().size)
        assertTrue(executed.isEmpty())
        assertTrue(audit.read().contains("cancelled: timeout"))
    }

    @Test
    fun aConfirmAfterTheDeadlineRunsNothing() {
        val (r, _) = runner()
        val ask = r.submit(ToolCall.Dial("0300 1234567")) as RunResult.NeedsConfirmation
        now += 31_000
        assertTrue(r.respond(ask.request, UserResponse.CONFIRM) is RunResult.Cancelled)
        assertTrue(executed.isEmpty())
    }

    @Test
    fun dialIsRefusedWhileLocked() {
        val (r, _) = runner()
        locked = true
        assertTrue(r.submit(ToolCall.Dial("0300 1234567")) is RunResult.Denied)
        assertTrue(executed.isEmpty())
    }

    @Test
    fun theKillSwitchDeniesEverything() {
        val (r, _) = runner()
        enabled = false
        assertTrue(r.submit(ToolCall.Flashlight(true)) is RunResult.Denied)
        assertTrue(executed.isEmpty())
    }

    @Test
    fun untrustedTextMakesASafeActionAsk() {
        val (r, _) = runner()
        assertTrue(r.submit(ToolCall.Flashlight(true), setOf(Provenance.USER_TYPED, Provenance.NOTIFICATION)) is RunResult.NeedsConfirmation)
        assertTrue(executed.isEmpty())
    }

    @Test
    fun aFailedActionIsReportedAndLogged() {
        val (r, audit) = runner()
        outcome = ActionOutcome.Failed("no launcher app matches \"spotify\"")
        assertTrue(r.submit(ToolCall.OpenApp("spotify")) is RunResult.Failed)
        assertTrue(audit.read().contains("failed: no launcher app"))
    }

    @Test
    fun theAuditLogHidesAllButTheLastTwoDigitsOfANumber() {
        val (r, audit) = runner()
        val ask = r.submit(ToolCall.Dial("0300 1234567")) as RunResult.NeedsConfirmation
        r.respond(ask.request, UserResponse.CONFIRM)
        assertFalse(audit.read().contains("1234567"))
        assertTrue(audit.read().contains("**** *****67"))
    }

    @Test
    fun theAuditLogHidesTheNumberInTheOutcomeToo() {
        // The executor reports the dialled number as its target (found on the vivo, Task 4).
        val (r, audit) = runner()
        outcome = ActionOutcome.Done("03001234567")
        val ask = r.submit(ToolCall.Dial("0300 1234567")) as RunResult.NeedsConfirmation
        r.respond(ask.request, UserResponse.CONFIRM)
        assertFalse(audit.read().contains("1234567"))
        assertTrue(audit.read().contains("done: *********67"))
    }

    @Test
    fun theAuditLogKeepsTheNewestLinesUnderItsCap() {
        val audit = ToolAuditLog(tmp.newFile(), maxBytes = 300)
        repeat(20) { audit.record(ToolCall.StartTimer(it + 1), "done: timer $it") }
        val text = audit.read()
        assertTrue(text.length <= 300)
        assertTrue(text.contains("done: timer 19"))
        assertFalse(text.contains("done: timer 0\n"))
    }
}
