package io.github.saadahmed1122.bibi.spike.s1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast

/**
 * Foreground service (type specialUse) that hosts the Bibi overlay window and samples metrics every minute.
 *
 * Modes: "bibi" (overlay shown) or "baseline" (same service and logging, no overlay) so Bibi's own
 * battery cost can be separated from the screen's (see SPEC.md §2).
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var log: SpikeLog
    private val handler = Handler(Looper.getMainLooper())

    private var bibi: BibiView? = null
    private var baseline = false
    private var hidden = false
    private var screenOn = true
    private var lastSampleAt = 0L

    private val mode get() = if (baseline) "baseline" else "bibi"

    private val sampler = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action == Intent.ACTION_SCREEN_ON
            updateRendering()
            log.event(if (screenOn) "screen_on" else "screen_off", "", screenOn, prefs.calm, mode)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        prefs = Prefs(this)
        log = SpikeLog(this)
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // System broadcasts need RECEIVER_EXPORTED; SCREEN_ON/OFF are protected, so other apps can't send them.
        registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null -> {
                // Restarted by the system after the process was killed (START_STICKY).
                baseline = prefs.baseline
                if (!startInForeground(restart = true)) return START_NOT_STICKY
            }
            ACTION_START, ACTION_BASELINE -> {
                baseline = intent.action == ACTION_BASELINE
                prefs.baseline = baseline
                if (!startInForeground(restart = false)) return START_NOT_STICKY
            }
            ACTION_TOGGLE_VISIBLE -> if (isRunning) toggleVisible() else stopSelf()
            ACTION_TOGGLE_CALM -> if (isRunning) setCalm(!prefs.calm) else stopSelf()
            ACTION_SET_CALM -> if (isRunning) setCalm(intent.getBooleanExtra(EXTRA_CALM, false)) else stopSelf()
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    /**
     * Adds the overlay first, then promotes to foreground. Apps targeting Android 15+ may start a
     * foreground service from the background only while they have a visible overlay window, which is
     * exactly the situation after a START_STICKY restart. Any refusal is logged as restart_blocked.
     */
    private fun startInForeground(restart: Boolean): Boolean {
        if (!baseline) addOverlay()
        return try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            if (!isRunning) {
                isRunning = true
                val kind = when {
                    restart -> "restart"
                    baseline -> "start_baseline"
                    else -> "start"
                }
                log.event(kind, "", screenOn, prefs.calm, mode)
                lastSampleAt = System.currentTimeMillis()
                handler.postDelayed(sampler, SAMPLE_INTERVAL_MS)
            }
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException or SecurityException
            log.event(if (restart) "restart_blocked" else "start_blocked", e.javaClass.simpleName, screenOn, prefs.calm, mode)
            removeOverlay()
            stopSelf()
            false
        }
    }

    private fun addOverlay() {
        if (bibi != null) return
        if (!Settings.canDrawOverlays(this)) {
            log.event("no_overlay_permission", "", screenOn, prefs.calm, mode)
            return
        }
        val size = (BIBI_SIZE_DP * resources.displayMetrics.density).toInt()
        val screen = windowManager.currentWindowMetrics.bounds
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.x.takeIf { it >= 0 } ?: (screen.width() - size)
            y = prefs.y.takeIf { it >= 0 } ?: (screen.height() / 2)
        }
        val view = BibiView(
            context = this,
            windowManager = windowManager,
            params = params,
            onMoved = { x, y ->
                prefs.x = x
                prefs.y = y
            },
            onTap = {
                Toast.makeText(this, R.string.tap_hello, Toast.LENGTH_SHORT).show()
                log.event("tap", "", screenOn, prefs.calm, mode)
            },
        )
        view.calm = prefs.calm
        windowManager.addView(view, params)
        bibi = view
        updateRendering()
    }

    private fun removeOverlay() {
        bibi?.let {
            it.setRunning(false)
            windowManager.removeView(it)
        }
        bibi = null
    }

    private fun updateRendering() {
        bibi?.setRunning(screenOn && !hidden)
    }

    private fun toggleVisible() {
        hidden = !hidden
        bibi?.visibility = if (hidden) View.GONE else View.VISIBLE
        updateRendering()
        log.event(if (hidden) "hide" else "show", "", screenOn, prefs.calm, mode)
        refreshNotification()
    }

    private fun setCalm(calm: Boolean) {
        prefs.calm = calm
        bibi?.calm = calm
        log.event("calm", calm.toString(), screenOn, calm, mode)
        refreshNotification()
    }

    private fun sample() {
        val now = System.currentTimeMillis()
        val frames = bibi?.takeFrameCount() ?: 0
        log.sample(frames, now - lastSampleAt, screenOn, prefs.calm, mode)
        lastSampleAt = now
    }

    private fun stopEverything() {
        if (isRunning) {
            sample()
            log.event("stop", "", screenOn, prefs.calm, mode)
        }
        handler.removeCallbacks(sampler)
        removeOverlay()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(screenReceiver)
        removeOverlay()
        isRunning = false
        super.onDestroy()
    }

    // --- notification ----------------------------------------------------------------------

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bibi)
            .setContentTitle(getString(if (baseline) R.string.notif_baseline else R.string.notif_running))
            .setContentText(if (baseline) "Logging every minute" else "Calm: ${if (prefs.calm) "on (2 fps)" else "off (12 fps)"}")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
        if (!baseline) {
            builder.addAction(action(ACTION_TOGGLE_VISIBLE, if (hidden) R.string.action_show else R.string.action_hide, 1))
            builder.addAction(action(ACTION_TOGGLE_CALM, R.string.action_calm, 2))
        }
        builder.addAction(action(ACTION_STOP, R.string.action_stop, 3))
        return builder.build()
    }

    private fun action(action: String, label: Int, requestCode: Int): Notification.Action {
        val intent = Intent(this, OverlayService::class.java).setAction(action)
        val pending = PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_bibi), getString(label), pending).build()
    }

    companion object {
        const val ACTION_START = "io.github.saadahmed1122.bibi.spike.s1.START"
        const val ACTION_BASELINE = "io.github.saadahmed1122.bibi.spike.s1.BASELINE"
        const val ACTION_TOGGLE_VISIBLE = "io.github.saadahmed1122.bibi.spike.s1.TOGGLE_VISIBLE"
        const val ACTION_TOGGLE_CALM = "io.github.saadahmed1122.bibi.spike.s1.TOGGLE_CALM"
        const val ACTION_SET_CALM = "io.github.saadahmed1122.bibi.spike.s1.SET_CALM"
        const val ACTION_STOP = "io.github.saadahmed1122.bibi.spike.s1.STOP"
        const val EXTRA_CALM = "calm"

        private const val CHANNEL_ID = "bibi_overlay"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_INTERVAL_MS = 60_000L
        private const val BIBI_SIZE_DP = 96

        /** True while the service is in the foreground in this process. */
        @Volatile
        var isRunning = false
            private set
    }
}
