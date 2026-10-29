package io.github.saadahmed1122.bibi.spike.s4

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock

/**
 * Builds every intent itself from a validated [ToolCall] (PRD §16.6 rule 3): fixed actions, fixed
 * extras, a `tel:` URI built with Uri.fromParts from the normalized literal number. The model's
 * text never becomes an action, URI or component. Intents and extras follow developer.android.com
 * "Common intents" (Alarm clock, Phone) and the AlarmClock reference.
 */
class ToolExecutor(private val activity: Activity) {
    private val pm: PackageManager = activity.packageManager

    sealed interface Built {
        data class Launch(val intent: Intent, val target: String) : Built
        data class Torch(val on: Boolean) : Built
        data class Error(val reason: String) : Built
    }

    /** [apps] is the precomputed launcher list; null while it is still loading. */
    fun build(call: ToolCall, apps: AppResolver?): Built {
        val errors = ToolValidator.validate(call)
        if (errors.isNotEmpty()) return Built.Error("invalid: ${errors.joinToString("; ")}")
        return when (call) {
            is ToolCall.OpenApp -> {
                val resolver = apps ?: return Built.Error("app list still loading")
                val app = resolver.resolve(call.app) ?: return Built.Error("no launcher app matches \"${call.app}\"")
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                    ?: return Built.Error("${app.packageName} has no launch intent")
                Built.Launch(intent, "${app.label} (${app.packageName})")
            }
            is ToolCall.SetAlarm -> Built.Launch(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, call.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, call.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                "alarm %02d:%02d".format(java.util.Locale.US, call.hour, call.minute),
            )
            is ToolCall.StartTimer -> Built.Launch(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, call.seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                "timer ${call.seconds} s",
            )
            is ToolCall.Flashlight -> Built.Torch(call.on)
            is ToolCall.Dial -> {
                val number = ToolValidator.normalizeNumber(call.number) ?: return Built.Error("invalid number")
                Built.Launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)), "dialer")
            }
        }
    }

    /**
     * Dry run: resolves the intent like startActivity would (MATCH_DEFAULT_ONLY) without launching.
     * Returns the resolved package, or null if nothing handles it. With several handlers and no
     * default the result can be the system resolver (PackageManager reference).
     */
    fun resolve(intent: Intent): String? =
        pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            ?.activityInfo?.packageName

    /** Real launch. Returns an error message, or null when startActivity returned normally. */
    fun launch(intent: Intent): String? = try {
        activity.startActivity(intent)
        null
    } catch (e: ActivityNotFoundException) {
        "no app handles ${intent.action}"
    } catch (e: SecurityException) {
        "not allowed: ${e.message}"
    }

    companion object {
        /** EXTRA_MESSAGE on spike alarms and timers, so the owner can find and delete them. */
        const val LABEL = "Bibi S4 test"
    }
}
