package io.github.saadahmed1122.bibi.companion.overlay

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
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service (type `specialUse`) that shows Bibi over other apps (SPEC-overlay.md), ported
 * from spike S1 without its measurement code.
 *
 * Tapping Bibi opens or closes the chat card ([ChatCardWindow]). The notification offers
 * Hide/Show, Open chat (the app) and Turn off. Hide removes the windows but keeps the service. `START_NOT_STICKY`: if the system kills the process, Bibi comes back the next time the
 * app starts the service, instead of S1's restart dance around Android 15's background-start rules.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var view: BibiView? = null
    private var screenOn = true
    private val cardOwner = OverlayLifecycleOwner()
    private val card by lazy { ChatCardWindow(this, windowManager, cardOwner, application as ChatCardHost) }

    /** Follows Bibi's mood for the floating view; cancelled in onDestroy. */
    private val scope = MainScope()

    private val controller = OverlayController(
        object : OverlayWindow {
            override fun add(): Boolean = addWindow()
            override fun remove() = removeWindow()
        },
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action == Intent.ACTION_SCREEN_ON
            view?.setRunning(screenOn)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        windowManager = getSystemService(WindowManager::class.java)
        scope.launch { (application as ChatCardHost).bibiMood.collect { view?.mood = it } }
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // SCREEN_ON/OFF are protected broadcasts, so other apps can't send them.
        registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_SHOW -> {
                // Answer startForegroundService() first, even if the overlay can't be shown.
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                // START comes on every app visit (FloatingToggle): it must not undo a Hide.
                val hidden = controller.state == OverlayController.State.Hidden
                if (intent.action == ACTION_START && hidden) {
                    refreshNotification()
                } else if (!Settings.canDrawOverlays(this) || !controller.show()) {
                    stopEverything()
                } else {
                    refreshNotification()
                }
            }
            ACTION_HIDE -> {
                card.close()
                controller.hide()
                refreshNotification()
            }
            ACTION_CALM -> view?.calm = prefs.getBoolean(KEY_CALM, false)
            ACTION_TURN_OFF -> {
                // The notification's Turn off: the app's switch shows off from now on.
                prefs.edit().putBoolean(KEY_ENABLED, false).apply()
                stopEverything()
            }
            else -> stopEverything() // ACTION_STOP, or an unexpected restart
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation: the saved spot may be off-screen now. Post so the new metrics are in place.
        view?.let { v ->
            v.post {
                v.clampToScreen()
                card.relayout()
            }
        }
    }

    private fun addWindow(): Boolean {
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
            val maxX = (screen.width() - size).coerceAtLeast(0)
            val maxY = (screen.height() - size).coerceAtLeast(0)
            x = prefs.getInt(KEY_X, maxX).coerceIn(0, maxX)
            y = prefs.getInt(KEY_Y, screen.height() / 2).coerceIn(0, maxY)
        }
        val v = BibiView(
            context = this,
            windowManager = windowManager,
            params = params,
            onMoved = { x, y -> prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply() },
            onTap = ::toggleCard,
        )
        v.calm = prefs.getBoolean(KEY_CALM, false)
        v.mood = (application as ChatCardHost).bibiMood.value
        try {
            windowManager.addView(v, params)
        } catch (e: WindowManager.BadTokenException) {
            return false // permission revoked or no token
        }
        view = v
        v.setRunning(screenOn)
        return true
    }

    private fun removeWindow() {
        val v = view ?: return
        view = null
        v.setRunning(false)
        try {
            windowManager.removeView(v)
        } catch (e: IllegalArgumentException) {
            // Already detached.
        }
    }

    private fun toggleCard() {
        if (card.isShown) card.close() else card.show()
    }

    private fun stopEverything() {
        card.close()
        controller.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        unregisterReceiver(screenReceiver)
        card.close()
        cardOwner.destroy()
        controller.stop()
        super.onDestroy()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val hidden = controller.state == OverlayController.State.Hidden
        val open = PendingIntent.getActivity(this, 0, openChatIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bibi)
            .setContentTitle(getString(if (hidden) R.string.overlay_notif_hidden else R.string.overlay_notif_title))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(action(if (hidden) ACTION_SHOW else ACTION_HIDE, if (hidden) R.string.overlay_show else R.string.overlay_hide, 1))
            .addAction(Notification.Action.Builder(icon(), getString(R.string.overlay_open_chat), open).build())
            .addAction(action(ACTION_TURN_OFF, R.string.overlay_stop, 2))
            .build()
    }

    private fun icon() = Icon.createWithResource(this, R.drawable.ic_stat_bibi)

    private fun action(action: String, label: Int, requestCode: Int): Notification.Action {
        val pending = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, OverlayService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(icon(), getString(label), pending).build()
    }

    companion object {
        /** Extra on the app's launch intent: open the chat screen. */
        const val EXTRA_OPEN_CHAT = "io.github.saadahmed1122.bibi.OPEN_CHAT"

        private const val ACTION_START = "io.github.saadahmed1122.bibi.overlay.START"
        private const val ACTION_SHOW = "io.github.saadahmed1122.bibi.overlay.SHOW"
        private const val ACTION_HIDE = "io.github.saadahmed1122.bibi.overlay.HIDE"
        private const val ACTION_STOP = "io.github.saadahmed1122.bibi.overlay.STOP"
        private const val ACTION_TURN_OFF = "io.github.saadahmed1122.bibi.overlay.TURN_OFF"
        private const val ACTION_CALM = "io.github.saadahmed1122.bibi.overlay.CALM"

        private const val CHANNEL_ID = "bibi_overlay"
        private const val NOTIFICATION_ID = 1
        private const val BIBI_SIZE_DP = 96
        private const val PREFS = "overlay"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_CALM = "calm"
        private const val KEY_ENABLED = "enabled"

        /** The saved switch and Calm mode, in the same file the service reads. */
        fun settings(context: Context): FloatingSettings = object : FloatingSettings {
            private val prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE)
            override var enabled: Boolean
                get() = prefs.getBoolean(KEY_ENABLED, false)
                set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            override var calm: Boolean
                get() = prefs.getBoolean(KEY_CALM, false)
                set(value) = prefs.edit().putBoolean(KEY_CALM, value).apply()
        }

        /** Start needs a visible activity and the overlay permission (FloatingToggle checks it). */
        fun control(context: Context): OverlayControl = object : OverlayControl {
            override fun start() {
                context.startForegroundService(Intent(context, OverlayService::class.java).setAction(ACTION_START))
            }

            override fun stop() {
                // startService of a stopped service would start it just to stop: skip when not running.
                if (running) send(context, ACTION_STOP)
            }

            override fun applyCalm() {
                if (running) send(context, ACTION_CALM)
            }
        }

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, OverlayService::class.java).setAction(action))
        }

        /** True between onCreate and onDestroy; the service runs in the app's own process. */
        @Volatile private var running = false

        private fun openChatIntent(context: Context): Intent =
            checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
                .putExtra(EXTRA_OPEN_CHAT, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
