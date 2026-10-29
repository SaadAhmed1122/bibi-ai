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
 * The [RunMode] comes from the start intent ([EXTRA_MODE]) and is fixed for the whole run; it is
 * persisted so a START_STICKY restart resumes the same mode and appends to the same run file
 * (see SPEC.md §2). Blocked activity starts never create a run file; they are written to
 * exit_reasons.csv. Window manager failures are logged as `overlay_error` instead of crashing.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var log: SpikeLog
    private val handler = Handler(Looper.getMainLooper())

    private var bibi: BibiView? = null
    private var mode = RunMode.BASELINE
    private var hidden = false
    private var screenOn = true
    private var lastSampleAt = 0L

    /** A Bibi restart has added the overlay and not yet attempted startForeground (see [restartBibi]). */
    private var awaitingFirstDraw = false
    private val promoteFallback = Runnable { promoteRestart("fallback_timer") }

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
            log.event(if (screenOn) "screen_on" else "screen_off", "", screenOn, mode)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        prefs = Prefs(this)
        log = SpikeLog(this)
        log.logNewExitReasons()
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
                if (isRunning || awaitingFirstDraw) return START_STICKY
                val saved = prefs.runMode
                if (saved == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                mode = saved
                if (!log.hasRun) log.beginRun(mode)
                val ok = if (mode.showsBibi) restartBibi() else restartBaseline()
                if (!ok) return START_NOT_STICKY
            }
            ACTION_START -> {
                if (isRunning) {
                    // The mode can't change during a run. Re-post the same foreground notification
                    // to answer this startForegroundService() call; the requested mode is ignored.
                    startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    return START_STICKY
                }
                if (awaitingFirstDraw) {
                    // A restart is waiting for Bibi's first frame; the app is visible now, so promote
                    // the restarted run (its mode wins) and answer this startForegroundService() call.
                    promoteRestart("activity_start")
                    return if (isRunning) START_STICKY else START_NOT_STICKY
                }
                val raw = intent.getStringExtra(EXTRA_MODE)
                val requested = RunMode.fromLogName(raw)
                if (requested == null) {
                    blockStart("bad_mode:$raw")
                    return START_NOT_STICKY
                }
                mode = requested
                if (mode.showsBibi && !Settings.canDrawOverlays(this)) {
                    blockStart("no_overlay_permission")
                    return START_NOT_STICKY
                }
                if (!startFromActivity()) return START_NOT_STICKY
            }
            ACTION_TOGGLE_VISIBLE -> if (isRunning) toggleVisible() else if (!awaitingFirstDraw) stopSelf()
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    /**
     * Refuses an ACTION_START. startForeground() is called first because the caller used
     * startForegroundService(), which crashes the app if the service stops without it. No run file is
     * created: the row goes to exit_reasons.csv via [SpikeLog.diagnostic].
     */
    private fun blockStart(reason: String) {
        val promoted = try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            ""
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException or SecurityException
            " startForeground:${e.javaClass.simpleName}"
        }
        log.diagnostic("start_blocked", "$reason mode=${mode.logName}$promoted")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * ACTION_START from the visible activity: promote first (allowed while the app is visible), then add
     * the overlay. The run file is created only after both succeed, so a failed start leaves no run file.
     */
    private fun startFromActivity(): Boolean {
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            log.diagnostic("start_blocked", "${e.javaClass.simpleName} mode=${mode.logName}")
            stopSelf()
            return false
        }
        if (mode.showsBibi && addOverlay() == null) {
            log.diagnostic("start_blocked", "addView_failed mode=${mode.logName}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return false
        }
        prefs.runMode = mode
        log.beginRun(mode)
        markRunning(if (mode == RunMode.BASELINE) "start_baseline" else "start")
        return true
    }

    /**
     * START_STICKY restart of a Bibi run. Apps targeting Android 15+ may start a foreground service
     * from the background only while they have a visible overlay window, so the overlay goes up first
     * and startForeground() waits for its first frame: once the view is attached, a one-shot
     * frame-commit callback fires when that frame has been rendered and submitted to the display
     * pipeline. [promoteFallback] calls it anyway after [FIRST_DRAW_TIMEOUT_MS] (e.g. screen off, no
     * hardware rendering); [awaitingFirstDraw] makes sure it is attempted once.
     */
    private fun restartBibi(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            log.event("restart_blocked", "no_overlay_permission", screenOn, mode)
            stopSelf()
            return false
        }
        awaitingFirstDraw = true // set before addOverlay so its errors go to the run file
        val view = addOverlay()
        if (view == null) {
            awaitingFirstDraw = false
            log.event("restart_blocked", "addView_failed", screenOn, mode)
            stopSelf()
            return false
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                // The thread this runs on isn't documented, so hop to the main thread.
                v.viewTreeObserver.registerFrameCommitCallback { handler.post { promoteRestart("first_frame") } }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        handler.postDelayed(promoteFallback, FIRST_DRAW_TIMEOUT_MS)
        return true
    }

    /** Second half of [restartBibi]; runs at most once per restart. */
    private fun promoteRestart(trigger: String) {
        if (!awaitingFirstDraw) return
        awaitingFirstDraw = false
        handler.removeCallbacks(promoteFallback)
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException or SecurityException
            log.event("restart_blocked", "${e.javaClass.simpleName} after $trigger", screenOn, mode)
            removeOverlay()
            stopSelf()
            return
        }
        markRunning("restart", trigger)
    }

    /** START_STICKY restart of a baseline run: no overlay, so no Android 15 overlay exemption. */
    private fun restartBaseline(): Boolean {
        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            log.event("baseline_restart_blocked", e.javaClass.simpleName, screenOn, mode)
            stopSelf()
            return false
        }
        markRunning("baseline_restart")
        return true
    }

    private fun markRunning(kind: String, detail: String = "") {
        isRunning = true
        log.event(kind, detail, screenOn, mode)
        lastSampleAt = System.currentTimeMillis()
        handler.postDelayed(sampler, SAMPLE_INTERVAL_MS)
    }

    /** Adds Bibi's window; returns the view, or null after logging `overlay_error` if the window manager refused. */
    private fun addOverlay(): BibiView? {
        bibi?.let { return it }
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
                log.event("tap", "", screenOn, mode)
            },
            onWindowError = ::overlayError,
        )
        view.calm = mode.calm
        try {
            windowManager.addView(view, params)
        } catch (e: RuntimeException) {
            // BadTokenException (permission revoked, no token), InvalidDisplayException, or the
            // IllegalState/IllegalArgument errors the window manager throws; anything else is a bug.
            if (e !is WindowManager.BadTokenException && e !is WindowManager.InvalidDisplayException &&
                e !is IllegalStateException && e !is IllegalArgumentException
            ) {
                throw e
            }
            overlayError("addView", e)
            return null
        }
        bibi = view
        updateRendering()
        return view
    }

    /** Logs a refused window operation to the run file if a run is live, else to exit_reasons.csv. */
    private fun overlayError(op: String, e: RuntimeException) {
        val detail = "$op:${e.javaClass.simpleName}"
        if (isRunning || awaitingFirstDraw) {
            log.event("overlay_error", detail, screenOn, mode)
        } else {
            log.diagnostic("overlay_error", "$detail mode=${mode.logName}")
        }
    }

    private fun removeOverlay() {
        val view = bibi ?: return
        bibi = null
        view.setRunning(false)
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) { // not attached to the window manager
            overlayError("removeView", e)
        } catch (e: IllegalStateException) {
            overlayError("removeView", e)
        }
    }

    private fun updateRendering() {
        bibi?.setRunning(screenOn && !hidden)
    }

    private fun toggleVisible() {
        hidden = !hidden
        bibi?.visibility = if (hidden) View.GONE else View.VISIBLE
        updateRendering()
        log.event(if (hidden) "hide" else "show", "", screenOn, mode)
        refreshNotification()
    }

    private fun sample() {
        val now = System.currentTimeMillis()
        val frames = bibi?.takeFrameCount() ?: 0
        log.sample(frames, now - lastSampleAt, screenOn, mode)
        lastSampleAt = now
    }

    private fun stopEverything() {
        if (isRunning) {
            sample()
            log.event("stop", "", screenOn, mode)
        }
        handler.removeCallbacks(sampler)
        handler.removeCallbacks(promoteFallback)
        awaitingFirstDraw = false
        removeOverlay()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        awaitingFirstDraw = false // a frame-commit post arriving after this is ignored
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
            .setContentTitle(getString(if (mode.showsBibi) R.string.notif_running else R.string.notif_baseline))
            .setContentText("Mode: ${mode.label} (${mode.logName}), logging every minute")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
        if (mode.showsBibi) {
            builder.addAction(action(ACTION_TOGGLE_VISIBLE, if (hidden) R.string.action_show else R.string.action_hide, 1))
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
        const val ACTION_TOGGLE_VISIBLE = "io.github.saadahmed1122.bibi.spike.s1.TOGGLE_VISIBLE"
        const val ACTION_STOP = "io.github.saadahmed1122.bibi.spike.s1.STOP"
        /** [RunMode.logName] of the run to start; required with [ACTION_START]. */
        const val EXTRA_MODE = "mode"

        private const val CHANNEL_ID = "bibi_overlay"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_INTERVAL_MS = 10_000L
        private const val BIBI_SIZE_DP = 96
        /** Well inside the startForeground() deadline, which is comparable to the ANR interval. */
        private const val FIRST_DRAW_TIMEOUT_MS = 2_000L

        /** True while the service is in the foreground in this process. */
        @Volatile
        var isRunning = false
            private set
    }
}
