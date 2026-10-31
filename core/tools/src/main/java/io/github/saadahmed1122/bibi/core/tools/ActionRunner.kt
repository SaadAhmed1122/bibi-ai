package io.github.saadahmed1122.bibi.core.tools

/** What running an action came to. [target] is what was opened or changed, for Bibi's reply. */
sealed interface ActionOutcome {
    data class Done(val target: String) : ActionOutcome
    data class Failed(val reason: String) : ActionOutcome
}

sealed interface RunResult {
    data class Done(val call: ToolCall, val target: String) : RunResult
    data class Failed(val call: ToolCall, val reason: String) : RunResult

    /** Show [request] (its exact summary) with Confirm and Cancel; answer with [ActionRunner.respond]. */
    data class NeedsConfirmation(val request: ConfirmationRequest) : RunResult
    data class Denied(val call: ToolCall, val reason: String) : RunResult
    data class Cancelled(val reason: String) : RunResult
}

/**
 * The single path to device actions (CLAUDE.md §4, SPEC-tools.md): every call goes through the
 * [PermissionEngine] before [execute] runs it, and every outcome is written to the [audit] log.
 * [execute] is the Android executor in the app; here it is a function so this stays JVM-tested.
 * Main thread only, like the engine.
 */
class ActionRunner(
    private val engine: PermissionEngine,
    private val audit: ToolAuditLog,
    private val execute: (ToolCall) -> ActionOutcome,
    private val deviceLocked: () -> Boolean,
    private val actionsEnabled: () -> Boolean = { true },
) {
    fun submit(call: ToolCall, provenance: Set<Provenance> = setOf(Provenance.USER_TYPED)): RunResult {
        val ctx = PolicyContext(provenance, deviceLocked(), actionsEnabled())
        return when (val d = engine.evaluate(call, ctx)) {
            is Decision.Allow -> run(d.call)
            is Decision.NeedsConfirmation -> RunResult.NeedsConfirmation(d.request)
            is Decision.Deny -> RunResult.Denied(call, d.reason).also { audit.record(call, "denied: ${d.reason}") }
        }
    }

    /** The user's answer to a confirmation card. Silence never runs anything: see [expire]. */
    fun respond(request: ConfirmationRequest, response: UserResponse): RunResult =
        when (val r = engine.respond(request.id, response, deviceLocked())) {
            is Resolution.Execute -> run(r.call)
            is Resolution.Cancelled -> RunResult.Cancelled(r.reason).also { audit.record(request.call, "cancelled: ${r.reason}") }
        }

    /** Confirmation cards whose time ran out; the UI closes them. */
    fun expire(): List<ConfirmationRequest> =
        engine.expire().onEach { audit.record(it.call, "cancelled: timeout") }

    private fun run(call: ToolCall): RunResult = when (val o = execute(call)) {
        is ActionOutcome.Done -> RunResult.Done(call, o.target).also { audit.record(call, "done: ${o.target}") }
        is ActionOutcome.Failed -> RunResult.Failed(call, o.reason).also { audit.record(call, "failed: ${o.reason}") }
    }
}
