package io.github.saadahmed1122.bibi.spike.s1

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws Bibi from pre-rendered frames on a Canvas, driven by a single Choreographer clock (PRD §17.5).
 *
 * - The clock only wakes at the target rate (12 fps, or 2 fps in Calm mode) and stops when not running;
 *   [FrameSchedule] keeps frames on a fixed grid aligned to vsync.
 * - Breathing: 3.5 s scale cycle, off in Calm mode. Blink: every 3–6 s, every 8 s in Calm (PRD §20.13).
 * - Drag moves the overlay window; release snaps to the nearest side edge in ~280 ms.
 */
@SuppressLint("ViewConstructor")
class BibiView(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onMoved: (x: Int, y: Int) -> Unit,
    private val onTap: () -> Unit,
    /** Called instead of crashing when [WindowManager.updateViewLayout] fails (e.g. window already removed). */
    private val onWindowError: (op: String, e: RuntimeException) -> Unit,
) : View(context), Choreographer.FrameCallback {

    private val idle: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.bibi_idle)
    private val blink: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.bibi_idle_blink)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect(0, 0, idle.width, idle.height)
    private val dst = Rect()

    private val startNanos = System.nanoTime()
    private var running = false
    private var blinkUntilNanos = 0L
    private var nextBlinkNanos = 0L
    private var framesDrawn = 0

    var calm: Boolean = false
        set(value) {
            field = value
            schedule.intervalNanos = frameIntervalNanos(value)
            scheduleNextBlink(System.nanoTime())
            invalidate()
        }

    private val frameIntervalMs: Long get() = if (calm) 500L else 1000L / 12
    private val schedule = FrameSchedule(frameIntervalNanos(calm = false))

    /** One vsync period; read from the display when the clock starts, 60 Hz until then. */
    private var vsyncNanos = 1_000_000_000L / 60

    init {
        contentDescription = "Bibi, your companion. Double-tap to say hi."
        scheduleNextBlink(startNanos)
    }

    /** Returns the number of frames drawn since the previous call. */
    fun takeFrameCount(): Int = framesDrawn.also { framesDrawn = 0 }

    fun setRunning(run: Boolean) {
        if (run == running) return
        running = run
        val choreographer = Choreographer.getInstance()
        if (run) {
            display?.refreshRate?.takeIf { it > 0f }?.let { vsyncNanos = (1e9f / it).toLong() }
            schedule.reset()
            choreographer.postFrameCallback(this)
        } else {
            choreographer.removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (schedule.onFrame(frameTimeNanos, vsyncNanos)) {
            if (frameTimeNanos >= nextBlinkNanos) {
                // In Calm mode a frame lasts 500 ms, so the blink frame is shown for one frame.
                blinkUntilNanos = frameTimeNanos + max(160L, frameIntervalMs) * 1_000_000L
                scheduleNextBlink(frameTimeNanos)
            }
            invalidate()
        }
        // Runs on the first vsync after the delay; FrameSchedule subtracts one vsync so that is the due one.
        Choreographer.getInstance().postFrameCallbackDelayed(this, schedule.delayMs)
    }

    private fun scheduleNextBlink(fromNanos: Long) {
        val delayMs = if (calm) 8_000L else Random.nextLong(3_000L, 6_001L)
        nextBlinkNanos = fromNanos + delayMs * 1_000_000L
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val breath = if (calm) {
            0f
        } else {
            val seconds = (now - startNanos) / 1e9
            (sin(2 * PI * seconds / 3.5) * 0.02).toFloat()
        }
        canvas.save()
        // Scale around the paws so Bibi breathes upward instead of floating.
        canvas.scale(1f - breath * 0.5f, 1f + breath, width / 2f, height.toFloat())
        dst.set(0, 0, width, height)
        canvas.drawBitmap(if (now < blinkUntilNanos) blink else idle, src, dst, paint)
        canvas.restore()
        framesDrawn++
    }

    // --- touch: drag, snap to edge, tap -------------------------------------------------

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var dragging = false
    private var snapAnimator: ValueAnimator? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                downX = params.x
                downY = params.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                if (dragging) {
                    params.x = downX + dx.toInt()
                    params.y = downY + dy.toInt()
                    moveWindow()
                }
            }
            MotionEvent.ACTION_UP -> if (dragging) snapToEdge() else performClick()
            MotionEvent.ACTION_CANCEL -> if (dragging) snapToEdge()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    private fun snapToEdge() {
        val screen = windowManager.currentWindowMetrics.bounds
        val targetX = if (params.x + width / 2 < screen.width() / 2) 0 else screen.width() - width
        params.y = params.y.coerceIn(0, (screen.height() - height).coerceAtLeast(0))
        var cancelled = false
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 280L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                if (isAttachedToWindow) moveWindow()
            }
            addListener(object : AnimatorListenerAdapter() {
                // cancel() (a new touch, or detach) also calls onAnimationEnd: don't save a half-way x.
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onMoved(params.x, params.y)
                }
            })
            start()
        }
    }

    private fun moveWindow() {
        try {
            windowManager.updateViewLayout(this, params)
        } catch (e: IllegalArgumentException) { // view not attached to the window manager
            onWindowError("updateViewLayout", e)
        } catch (e: IllegalStateException) {
            onWindowError("updateViewLayout", e)
        }
    }

    override fun onDetachedFromWindow() {
        setRunning(false)
        snapAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        fun frameIntervalNanos(calm: Boolean): Long = if (calm) 500_000_000L else 1_000_000_000L / 12
    }
}
