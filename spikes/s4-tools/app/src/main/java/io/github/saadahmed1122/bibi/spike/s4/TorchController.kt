package io.github.saadahmed1122.bibi.spike.s4

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock

/**
 * Flashlight through CameraManager.setTorchMode, which needs no camera permission and doesn't open
 * the camera (developer.android.com, CameraManager reference). The torch callback is registered
 * only while the activity is started ([start]/[stop]), so nothing listens in the background.
 */
class TorchController(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val mainExecutor = context.mainExecutor

    /** First camera with a flash unit, found once; null if the phone has none. */
    val cameraId: String? by lazy {
        try {
            cameras.cameraIdList.firstOrNull {
                cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            null
        }
    }

    /** Last state reported by the callback; null until the first report. */
    var isOn: Boolean? = null
        private set

    private var waiter: ((Long) -> Unit)? = null
    private var waitingFor: Boolean? = null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id != cameraId) return
            val now = SystemClock.elapsedRealtimeNanos()
            isOn = enabled
            val w = waiter
            if (w != null && waitingFor == enabled) {
                waiter = null
                waitingFor = null
                w(now)
            }
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) isOn = null
        }
    }

    fun start() = cameras.registerTorchCallback(mainExecutor, callback)

    fun stop() {
        cameras.unregisterTorchCallback(callback)
        waiter = null
        waitingFor = null
    }

    /**
     * Sets the torch and calls [onChanged] with the elapsedRealtimeNanos of the matching
     * onTorchModeChanged callback. Returns an error message, or null if setTorchMode returned normally.
     * The caller times out a missing callback.
     */
    fun set(on: Boolean, onChanged: (Long) -> Unit): String? {
        val id = cameraId ?: return "no camera with a flash unit"
        waiter = onChanged
        waitingFor = on
        return try {
            cameras.setTorchMode(id, on)
            null
        } catch (e: CameraAccessException) {
            waiter = null
            "torch unavailable: reason ${e.reason}"
        } catch (e: IllegalArgumentException) {
            waiter = null
            "torch error: ${e.message}"
        }
    }

    fun cancelWait() {
        waiter = null
        waitingFor = null
    }
}
