package io.github.saadahmed1122.bibi.companion.overlay

import android.app.Notification
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import java.time.LocalTime
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.IBinder
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service (type `specialUse`) that shows Bibi over other apps (SPEC-overlay.md), ported
 * from spike S1 without its measurement code.
 *
 * Tapping Bibi starts a spoken conversation ([TalkWindow], #118); a long-press opens the chat card
 * ([ChatCardWindow]). The notification offers
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
    private val talk by lazy { TalkWindow(this, windowManager, cardOwner, application as ChatCardHost, ::bibiBounds, ::openCard) }
    private var bibiParams: WindowManager.LayoutParams? = null

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

    /** Reactions and sleep (SPEC-behaviors.md); kept here so cooldowns survive Hide and Show. */
    private val behaviors = Behaviors()

    /** B-02 unlock, B-05 charger, B-06 low battery. All are protected system broadcasts. */
    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val v = view ?: return
            refreshBusy()
            val now = System.nanoTime() / 1_000_000L
            v.play(
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> behaviors.onUnlock(now, LocalTime.now().hour)
                    Intent.ACTION_POWER_CONNECTED -> behaviors.onPowerConnected(now)
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                        if (level < 0 || scale <= 0) null else behaviors.onBattery(level * 100 / scale, plugged, now)
                    }
                    else -> null
                },
            )
        }
    }

    /** No spontaneous acts while Bibi thinks or talks, the card or talk window is open, or in a call. */
    private fun refreshBusy() {
        val mode = getSystemService(AudioManager::class.java).mode
        val inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        behaviors.busy = (application as ChatCardHost).bibiMood.value != BibiMood.IDLE || card.isShown || talk.isShown || inCall
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
        val events = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(eventReceiver, events, RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_SHOW -> {
                // Answer startForegroundService() first, even if the overlay can't be shown.
                startForeground(NOTIFICATION_ID, notification(), serviceTypes())
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
                talk.close()
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
                talk.relayout()
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
            onMoved = { x, y ->
                prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
                talk.relayout()
            },
            onTap = ::onBibiTap,
            onLongPress = ::openCard,
            behaviors = behaviors,
            hour = { LocalTime.now().hour },
        )
        v.calm = prefs.getBoolean(KEY_CALM, false)
        v.mood = (application as ChatCardHost).bibiMood.value
        try {
            windowManager.addView(v, params)
        } catch (e: WindowManager.BadTokenException) {
            return false // permission revoked or no token
        }
        view = v
        bibiParams = params
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
     * Tap (#118): closes whatever is open; otherwise starts a spoken conversation. Without the mic
     * permission the card opens instead (its mic button sends you to the app to grant it).
     */
    private fun onBibiTap() {
        when {
            card.isShown -> card.close()
            talk.isShown -> talk.close()
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> card.show()
            else -> talk.show()
        }
    }

    /** Long-press, "open chat", or talking isn't possible: the typed chat card. */
    private fun openCard() {
        talk.close()
        card.show()
    }

    private fun bibiBounds(): android.graphics.Rect? = bibiParams?.takeIf { view != null }?.let {
        android.graphics.Rect(it.x, it.y, it.x + it.width, it.y + it.height)
    }

    private fun stopEverything() {
        talk.close()
        card.close()
        controller.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        unregisterReceiver(screenReceiver)
        unregisterReceiver(eventReceiver)
        talk.close()
        card.close()
        cardOwner.destroy()
        controller.stop()
        super.onDestroy()
    }

    /**
     * specialUse for Bibi on screen, plus microphone once RECORD_AUDIO is granted, so push-to-talk in
     * the floating card can hear (measured: without it the card's recording is pure silence). Only
     * set from ACTION_START/SHOW, which the app sends while it is in front: Android refuses a
     * microphone service started from the background. The mic itself records only while the talk
     * button is held.
     */
    private fun serviceTypes(): Int {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or (if (mic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
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
