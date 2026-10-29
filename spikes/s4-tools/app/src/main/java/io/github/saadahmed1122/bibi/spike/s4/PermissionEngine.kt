package io.github.saadahmed1122.bibi.spike.s4

// Pure Kotlin (no android.*).

import java.util.Locale

/** Source label of content in the active context (PRD §16.6 rule 1). */
enum class Provenance(val untrusted: Boolean) {
    USER_TYPED(false),
    USER_VOICE(false),
    MEMORY(false),
    SYSTEM(false),
    NOTIFICATION(true),
    FILE(true),
    WEB(true),
}

data class PolicyContext(
    val provenance: Set<Provenance> = setOf(Provenance.USER_TYPED),
    val deviceLocked: Boolean = false,
    /** Global "Disable all actions" kill switch (PRD §16.4). */
    val actionsEnabled: Boolean = true,
) {
    val tainted: Boolean get() = provenance.any { it.untrusted }
}

data class ConfirmationRequest(
    val id: Long,
    val call: ToolCall,
    /** Exact resolved parameters, never the model's paraphrase (PRD §16.6 rule 5). */
    val summary: String,
    val effectiveRisk: Risk,
    val reasons: List<String>,
    val deadlineMs: Long,
)

sealed interface Decision {
    data class Allow(val call: ToolCall) : Decision
    data class NeedsConfirmation(val request: ConfirmationRequest) : Decision
    data class Deny(val reason: String) : Decision
}

enum class UserResponse { CONFIRM, CANCEL }

sealed interface Resolution {
    data class Execute(val call: ToolCall) : Resolution
    data class Cancelled(val reason: String) : Resolution
}

/**
 * PRD §16.4 policy for the five S4 tools:
 * - SAFE and untainted → allow (also while locked: the S4 SAFE actions only start a UI or toggle the torch).
 * - Untrusted provenance in the context (NOTIFICATION, FILE, WEB) upgrades SAFE to SENSITIVE (§16.6 rule 2, FR-124).
 * - SENSITIVE → a confirmation request with a deadline; refused outright while the device is locked.
 * - CRITICAL → denied (identity verification is not part of this spike).
 * - Invalid parameters or the kill switch → denied.
 * Each confirmation is single-use and covers exactly one call; a timeout cancels, silence is never consent.
 * Not thread-safe: the spike calls it from the main thread only.
 */
class PermissionEngine(
    private val nowMs: () -> Long,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val pending = LinkedHashMap<Long, ConfirmationRequest>()
    private var nextId = 1L

    fun evaluate(call: ToolCall, ctx: PolicyContext, resolvedTarget: String? = null): Decision {
        if (!ctx.actionsEnabled) return Decision.Deny("all actions are disabled")
        val errors = ToolValidator.validate(call)
        if (errors.isNotEmpty()) return Decision.Deny("invalid parameters: ${errors.joinToString("; ")}")

        val reasons = mutableListOf<String>()
        var risk = call.tool.risk
        if (risk == Risk.SENSITIVE) reasons += "${call.tool.wire} is a sensitive action"
        if (ctx.tainted && risk == Risk.SAFE) {
            risk = Risk.SENSITIVE
            reasons += "untrusted content in context (${ctx.provenance.filter { it.untrusted }.joinToString()})"
        }
        return when (risk) {
            Risk.SAFE -> Decision.Allow(call)
            Risk.CRITICAL -> Decision.Deny("critical actions need identity verification (not in this spike)")
            Risk.SENSITIVE -> {
                if (ctx.deviceLocked) return Decision.Deny("sensitive actions are not allowed while the device is locked")
                val req = ConfirmationRequest(nextId++, call, summary(call, resolvedTarget), risk, reasons, nowMs() + timeoutMs)
                pending[req.id] = req
                Decision.NeedsConfirmation(req)
            }
        }
    }

    /** The user's answer to request [id]. Consumes the request whatever the answer. */
    fun respond(id: Long, response: UserResponse, deviceLocked: Boolean): Resolution {
        val req = pending.remove(id) ?: return Resolution.Cancelled("unknown or already answered request")
        return when {
            nowMs() >= req.deadlineMs -> Resolution.Cancelled("timeout: no answer within ${timeoutMs / 1000} s")
            response == UserResponse.CANCEL -> Resolution.Cancelled("cancelled by user")
            deviceLocked -> Resolution.Cancelled("device locked before confirmation")
            else -> Resolution.Execute(req.call)
        }
    }

    /** Removes and returns every request whose deadline has passed (the UI closes their dialogs). */
    fun expire(): List<ConfirmationRequest> {
        val now = nowMs()
        val gone = pending.values.filter { now >= it.deadlineMs }
        gone.forEach { pending.remove(it.id) }
        return gone
    }

    fun pendingCount(): Int = pending.size

    companion object {
        /** PRD §16.4 default. */
        const val DEFAULT_TIMEOUT_MS = 30_000L

        fun summary(call: ToolCall, resolvedTarget: String?): String = when (call) {
            is ToolCall.Dial ->
                "Open the dialer with ${ToolValidator.normalizeNumber(call.number)} prefilled? Nothing is called until you press Call in the dialer."
            is ToolCall.OpenApp -> "Open ${resolvedTarget ?: call.app}?"
            is ToolCall.SetAlarm -> "Set an alarm for %02d:%02d?".format(Locale.US, call.hour, call.minute)
            is ToolCall.StartTimer -> "Start a timer for ${formatSeconds(call.seconds)}?"
            is ToolCall.Flashlight -> "Turn the flashlight ${if (call.on) "on" else "off"}?"
        }

        fun formatSeconds(s: Int): String {
            val h = s / 3600
            val m = s % 3600 / 60
            val sec = s % 60
            return listOfNotNull(
                if (h > 0) "$h h" else null,
                if (m > 0) "$m min" else null,
                if (sec > 0 || s == 0) "$sec s" else null,
            ).joinToString(" ")
        }
    }
}
