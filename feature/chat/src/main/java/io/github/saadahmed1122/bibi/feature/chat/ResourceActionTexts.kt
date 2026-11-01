package io.github.saadahmed1122.bibi.feature.chat

import android.content.Context
import io.github.saadahmed1122.bibi.core.tools.PermissionEngine
import io.github.saadahmed1122.bibi.core.tools.ToolCall

/** [ActionTexts] from this module's string resources. */
class ResourceActionTexts(private val context: Context) : ActionTexts {
    override fun done(call: ToolCall, target: String): String = when (call) {
        is ToolCall.OpenApp -> context.getString(R.string.action_open_app, target)
        is ToolCall.SetAlarm -> context.getString(R.string.action_alarm, target)
        is ToolCall.StartTimer -> context.getString(R.string.action_timer, PermissionEngine.formatSeconds(call.seconds))
        is ToolCall.Flashlight -> context.getString(if (call.on) R.string.action_torch_on else R.string.action_torch_off)
        is ToolCall.Dial -> context.getString(R.string.action_dial, target)
    }

    override fun failed(reason: String) = context.getString(R.string.action_failed, reason)

    override fun denied(reason: String) = context.getString(R.string.action_denied, reason)

    override fun cancelled() = context.getString(R.string.action_cancelled)

    override fun timedOut() = context.getString(R.string.action_timed_out)

    override fun confirm() = context.getString(R.string.action_confirm)

    override fun safeMode() = context.getString(R.string.action_safe_mode)
}
