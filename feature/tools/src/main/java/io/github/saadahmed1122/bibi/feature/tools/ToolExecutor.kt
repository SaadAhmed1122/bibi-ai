package io.github.saadahmed1122.bibi.feature.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import io.github.saadahmed1122.bibi.core.tools.ActionOutcome
import io.github.saadahmed1122.bibi.core.tools.AppResolver
import io.github.saadahmed1122.bibi.core.tools.ToolCall
import io.github.saadahmed1122.bibi.core.tools.ToolValidator
import java.util.Locale

/**
 * Runs a validated [ToolCall] (ported from S4). Every intent is built here from the call's checked
 * values (PRD §16.6 rule 3): fixed actions and extras, a `tel:` URI from the normalized number. The
 * model's or user's text never becomes an action, URI or component. Intents follow
 * developer.android.com "Common intents" (Alarm clock, Phone) and the AlarmClock reference.
 *
 * Only [io.github.saadahmed1122.bibi.core.tools.ActionRunner] calls [execute], after the Permission
 * Engine. Activities start from the application context with NEW_TASK, so the floating card can
 * use it too (an app with a visible overlay may start activities from the background).
 */
class ToolExecutor(
    private val context: Context,
    /** The launcher list; null while it is still loading. */
    private val apps: () -> AppResolver?,
) {
    private val pm = context.packageManager
    private val cameras = context.getSystemService(CameraManager::class.java)

    /** First camera with a flash unit, found once; null if the phone has none. */
    private val torchId: String? by lazy {
        try {
            cameras.cameraIdList.firstOrNull {
                cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            null
        }
    }

    fun execute(call: ToolCall): ActionOutcome {
        val errors = ToolValidator.validate(call)
        if (errors.isNotEmpty()) return ActionOutcome.Failed("invalid: ${errors.joinToString("; ")}")
        return when (call) {
            is ToolCall.OpenApp -> {
                val resolver = apps() ?: return ActionOutcome.Failed("app list still loading")
                val app = resolver.resolve(call.app) ?: return ActionOutcome.Failed("no app called \"${call.app}\"")
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                    ?: return ActionOutcome.Failed("${app.label} can't be opened")
                launch(intent, app.label)
            }
            is ToolCall.SetAlarm -> launch(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, call.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, call.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                "%02d:%02d".format(Locale.US, call.hour, call.minute),
            )
            is ToolCall.StartTimer -> launch(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, call.seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                "${call.seconds} s",
            )
            is ToolCall.Flashlight -> torch(call.on)
            is ToolCall.Dial -> {
                val number = ToolValidator.normalizeNumber(call.number) ?: return ActionOutcome.Failed("invalid number")
                launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)), number)
            }
        }
    }

    private fun launch(intent: Intent, target: String): ActionOutcome = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionOutcome.Done(target)
    } catch (e: ActivityNotFoundException) {
        ActionOutcome.Failed("no app on this phone handles it")
    } catch (e: SecurityException) {
        ActionOutcome.Failed("not allowed: ${e.message}")
    }

    private fun torch(on: Boolean): ActionOutcome {
        val id = torchId ?: return ActionOutcome.Failed("this phone has no flashlight")
        return try {
            cameras.setTorchMode(id, on)
            ActionOutcome.Done(if (on) "on" else "off")
        } catch (e: CameraAccessException) {
            // E.g. the camera app is using the flash.
            ActionOutcome.Failed("the flashlight is busy")
        } catch (e: IllegalArgumentException) {
            ActionOutcome.Failed("the flashlight is unavailable")
        }
    }

    companion object {
        /** The label on alarms and timers Bibi sets, so the user can tell them apart. */
        const val LABEL = "Bibi"
    }
}
