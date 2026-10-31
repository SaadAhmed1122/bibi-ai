package io.github.saadahmed1122.bibi.companion.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme

/**
 * The chat card: a second, focusable overlay window (so the keyboard works) near the bottom of the
 * screen. It closes on Back, a tap outside it, or its Close button. Main thread only.
 */
internal class ChatCardWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val owner: OverlayLifecycleOwner,
    private val host: ChatCardHost,
) {
    private var root: FrameLayout? = null

    /** How far the card is lifted for the keyboard, in px; 0 when the keyboard is hidden. */
    private var keyboardLift = 0

    val isShown: Boolean get() = root != null

    fun show() {
        if (root != null) return
        val frame = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    close()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            // Before the children: the ComposeView would otherwise take ACTION_OUTSIDE (its point is 0,0).
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    close()
                    return true
                }
                return super.dispatchTouchEvent(event)
            }
        }
        owner.attachTo(frame)
        // The system would push the card up without shortening it, leaving its bottom under the
        // keyboard in portrait. Instead the card moves above the keyboard and shrinks itself.
        frame.setOnApplyWindowInsetsListener { v, insets ->
            val onScreen = IntArray(2).also(v::getLocationOnScreen)
            val lift = KeyboardLift.next(
                current = keyboardLift,
                keyboardVisible = insets.isVisible(WindowInsets.Type.ime()),
                overlap = insets.getInsets(WindowInsets.Type.ime()).bottom,
                windowBottom = onScreen[1] + v.height,
                restBottom = restBottom(),
                gap = margin(),
            )
            if (lift != keyboardLift) {
                keyboardLift = lift
                updateLayout()
            }
            v.onApplyWindowInsets(insets)
        }
        frame.addView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { BibiTheme { host.ChatCardContent(onClose = ::close) } }
            },
        )
        try {
            windowManager.addView(frame, layoutParams())
        } catch (e: WindowManager.BadTokenException) {
            return // the overlay permission was revoked
        }
        root = frame
        owner.setVisible(true)
    }

    fun close() {
        val frame = root ?: return
        root = null
        keyboardLift = 0
        owner.setVisible(false)
        try {
            windowManager.removeView(frame)
        } catch (e: IllegalArgumentException) {
            // Already detached.
        }
    }

    /** After a rotation: new width and height for the new screen; the keyboard lift is measured again. */
    fun relayout() {
        keyboardLift = 0
        updateLayout()
    }

    private fun updateLayout() {
        val frame = root ?: return
        try {
            windowManager.updateViewLayout(frame, layoutParams())
        } catch (e: IllegalArgumentException) {
            // Detached in between.
        }
    }

    private fun margin() = (MARGIN_DP * context.resources.displayMetrics.density).toInt()

    /** Screen y of the card's bottom edge with no keyboard. */
    private fun restBottom(): Int {
        val metrics = windowManager.currentWindowMetrics
        return metrics.bounds.height() - metrics.windowInsets.getInsets(WindowInsets.Type.systemBars()).bottom - margin()
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val bars = metrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
        val landscape = bounds.width() > bounds.height()
        val margin = margin()
        val y = margin + keyboardLift
        // Room between the status bar and the lifted bottom edge.
        val room = bounds.height() - bars.top - bars.bottom - y - margin
        return WindowManager.LayoutParams(
            // Landscape: a narrower card so the app behind stays visible.
            if (landscape) (bounds.width() * 0.6f).toInt() else bounds.width() - 2 * margin,
            minOf((bounds.height() * if (landscape) 0.85f else 0.6f).toInt(), room).coerceAtLeast(bounds.height() / 4),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not touch-modal, so touches outside reach the app behind and arrive here as ACTION_OUTSIDE.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            this.y = y
            // Fit the system bars only: the keyboard is handled by [keyboardLift].
            fitInsetsTypes = WindowInsets.Type.systemBars()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private companion object {
        const val MARGIN_DP = 12
    }
}
