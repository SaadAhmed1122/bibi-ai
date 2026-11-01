package io.github.saadahmed1122.bibi.companion.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.saadahmed1122.bibi.core.designsystem.BibiTheme

/**
 * The tap-to-talk bubble (#118): a small window next to floating Bibi showing the conversation.
 * It never takes focus or touches outside itself, so the app behind keeps working. Showing it
 * starts the conversation; closing it stops it. Main thread only.
 */
internal class TalkWindow(
    private val context: Context,
    private val windowManager: WindowManager,
    private val owner: OverlayLifecycleOwner,
    private val host: ChatCardHost,
    /** Floating Bibi's window on screen, to sit next to it. */
    private val anchor: () -> Rect?,
    private val onOpenChat: () -> Unit,
) {
    private var root: FrameLayout? = null

    val isShown: Boolean get() = root != null

    fun show() {
        if (root != null) return
        val frame = FrameLayout(context)
        owner.attachTo(frame)
        frame.addView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { BibiTheme { host.TalkContent(onOpenChat = onOpenChat, onEnd = ::close) } }
            },
        )
        try {
            windowManager.addView(frame, layoutParams())
        } catch (e: WindowManager.BadTokenException) {
            return
        }
        root = frame
        owner.setVisible(true)
    }

    fun close() {
        val frame = root ?: return
        root = null
        owner.setVisible(false)
        try {
            windowManager.removeView(frame)
        } catch (e: IllegalArgumentException) {
            // Already detached.
        }
    }

    /** After Bibi moved or the screen rotated. */
    fun relayout() {
        val frame = root ?: return
        try {
            windowManager.updateViewLayout(frame, layoutParams())
        } catch (e: IllegalArgumentException) {
            // Detached in between.
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val bars = metrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
        val density = context.resources.displayMetrics.density
        val margin = (MARGIN_DP * density).toInt()
        val width = minOf(bounds.width() - 2 * margin, (MAX_WIDTH_DP * density).toInt())
        val bibi = anchor() ?: Rect(bounds.width() - margin, bounds.height() / 2, bounds.width() - margin, bounds.height() / 2)
        val onRight = bibi.centerX() > bounds.width() / 2
        val inLowerHalf = bibi.centerY() > bounds.height() / 2
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Beside Bibi's edge; above Bibi when it sits low on the screen, below it when high.
            gravity = (if (onRight) Gravity.END else Gravity.START) or (if (inLowerHalf) Gravity.BOTTOM else Gravity.TOP)
            x = margin
            y = if (inLowerHalf) {
                (bounds.height() - bibi.top + margin / 2).coerceAtLeast(bars.bottom + margin)
            } else {
                (bibi.bottom + margin / 2).coerceAtLeast(bars.top + margin)
            }
        }
    }

    private companion object {
        const val MARGIN_DP = 12
        const val MAX_WIDTH_DP = 340
    }
}
