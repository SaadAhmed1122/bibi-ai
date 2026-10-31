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

/**
 * Foreground service (type `specialUse`) that shows Bibi over other apps (SPEC-overlay.md), ported
 * from spike S1 without its measurement code.
 *
 * The notification offers Hide/Show, Open chat and Stop. Hide removes the window but keeps the
 * service. `START_NOT_STICKY`: if the system kills the process, Bibi comes back the next time the
 * app starts the service, instead of S1's restart dance around Android 15's background-start rules.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var view: BibiView? = null
    private var screenOn = true

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
        windowManager = getSystemService(WindowManager::class.java)
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
                if (!Settings.canDrawOverlays(this) || !controller.show()) stopEverything() else refreshNotification()
            }
            ACTION_HIDE -> {
                controller.hide()
                refreshNotification()
            }
            else -> stopEverything() // ACTION_STOP, or an unexpected restart
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation: the saved spot may be off-screen now. Post so the new metrics are in place.
        view?.let { v -> v.post { v.clampToScreen() } }
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
            onTap = ::openChat,
        )
        v.calm = prefs.getBoolean(KEY_CALM, false)
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

    /**
     * Opens the app's chat. A tap on an overlay is a user action, and apps holding the overlay
     * permission may start activities from the background. TODO(#45): open the chat card instead.
     */
    private fun openChat() {
        startActivity(openChatIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun stopEverything() {
        controller.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
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
            .addAction(action(ACTION_STOP, R.string.overlay_stop, 2))
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

        private const val CHANNEL_ID = "bibi_overlay"
        private const val NOTIFICATION_ID = 1
        private const val BIBI_SIZE_DP = 96
        private const val PREFS = "overlay"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val KEY_CALM = "calm"

        /** Call from a visible activity, with the overlay permission granted. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }

        private fun openChatIntent(context: Context): Intent =
            checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
                .putExtra(EXTRA_OPEN_CHAT, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
