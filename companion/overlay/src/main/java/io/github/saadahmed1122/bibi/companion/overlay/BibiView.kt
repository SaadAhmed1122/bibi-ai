package io.github.saadahmed1122.bibi.companion.overlay

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
import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws Bibi from pre-rendered frames on a Canvas, ported from spike S1 (PASS: 27.8 mA at 12 fps,
 * 11.5 mA in Calm on the vivo).
 *
 * - One Choreographer clock that wakes only at the target rate ([FrameSchedule], 12 fps or 2 fps in
 *   Calm) and stops while not running (hidden, screen off).
 * - Breathing: a 3.5 s scale cycle, off in Calm. Blink every 3–6 s, every 8 s in Calm.
 * - Drag moves the window; release snaps to the nearer side edge in 280 ms, inside the system bars
 *   and cutout ([SnapMath]).
 */
@SuppressLint("ViewConstructor")
class BibiView(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onMoved: (x: Int, y: Int) -> Unit,
    private val onTap: () -> Unit,
) : View(context), Choreographer.FrameCallback {

    /** One 288 px frame per state, decoded once (about 330 KB each). */
    private val frames: Map<BibiMood, Bitmap> = BibiMood.entries.associateWith { BitmapFactory.decodeResource(resources, it.frame) }
    private val blink: Bitmap = BitmapFactory.decodeResource(resources, BibiMood.BLINK_FRAME)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect(0, 0, blink.width, blink.height)

    /** What Bibi shows (SPEC-avatar-states.md); the blink and breathing belong to idle only. */
    var mood: BibiMood = BibiMood.IDLE
        set(value) {
            if (value == field) return
            field = value
            contentDescription = context.getString(R.string.overlay_bibi_description_state, context.getString(value.description))
            invalidate()
        }
    private val dst = Rect()

    private val startNanos = System.nanoTime()
    private var running = false
    private var blinkUntilNanos = 0L
    private var nextBlinkNanos = 0L

    var calm: Boolean = false
        set(value) {
            field = value
            schedule.intervalNanos = FrameSchedule.intervalNanos(if (value) FrameSchedule.FPS_CALM else FrameSchedule.FPS_NORMAL)
            scheduleNextBlink(System.nanoTime())
            invalidate()
        }

    private val schedule = FrameSchedule(FrameSchedule.intervalNanos(FrameSchedule.FPS_NORMAL))

    /** One vsync period, read from the display once attached; 60 Hz until then. */
    private var vsyncNanos = 1_000_000_000L / 60

    init {
        contentDescription = context.getString(R.string.overlay_bibi_description_state, context.getString(BibiMood.IDLE.description))
        scheduleNextBlink(startNanos)
    }

    fun setRunning(run: Boolean) {
        if (run == running) return
        running = run
        val choreographer = Choreographer.getInstance()
        if (run) {
            updateVsync()
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
                // In Calm a frame lasts 500 ms, so the blink frame shows for one frame.
                blinkUntilNanos = frameTimeNanos + max(160L, schedule.intervalNanos / 1_000_000L) * 1_000_000L
                scheduleNextBlink(frameTimeNanos)
            }
            invalidate()
        }
        Choreographer.getInstance().postFrameCallbackDelayed(this, schedule.delayMs)
    }

    private fun updateVsync() {
        display?.refreshRate?.takeIf { it > 0f }?.let { vsyncNanos = (1e9f / it).toLong() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateVsync()
    }

    private fun scheduleNextBlink(fromNanos: Long) {
        val delayMs = if (calm) 8_000L else Random.nextLong(3_000L, 6_001L)
        nextBlinkNanos = fromNanos + delayMs * 1_000_000L
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val breath = if (calm || mood != BibiMood.IDLE) 0f else (sin(2 * PI * ((now - startNanos) / 1e9) / 3.5) * 0.02).toFloat()
        canvas.save()
        // Scale around the paws so Bibi breathes upward instead of floating.
        canvas.scale(1f - breath * 0.5f, 1f + breath, width / 2f, height.toFloat())
        dst.set(0, 0, width, height)
        val frame = if (mood == BibiMood.IDLE && now < blinkUntilNanos) blink else frames.getValue(mood)
        canvas.drawBitmap(frame, src, dst, paint)
        canvas.restore()
    }

    // --- touch: drag, snap to edge, tap ---

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

    /** Keeps Bibi on screen and on an edge after a rotation or display change, and saves the result. */
    fun clampToScreen() {
        snapAnimator?.cancel()
        val screen = screenSize()
        val insets = insets()
        val p = SnapMath.clamp(Point(params.x, params.y), Size(width, height), screen, insets)
        params.x = SnapMath.snapTarget(p.x, width, screen.width, insets.left, insets.right)
        params.y = p.y
        moveWindow()
        onMoved(params.x, params.y)
    }

    private fun snapToEdge() {
        val screen = screenSize()
        val insets = insets()
        val targetX = SnapMath.snapTarget(params.x, width, screen.width, insets.left, insets.right)
        params.y = SnapMath.clamp(Point(targetX, params.y), Size(width, height), screen, insets).y
        var cancelled = false
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_MS
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

    private fun screenSize(): Size = windowManager.currentWindowMetrics.bounds.let { Size(it.width(), it.height()) }

    private fun insets(): Insets {
        val i = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return Insets(i.left, i.top, i.right, i.bottom)
    }

    private fun moveWindow() {
        if (!isAttachedToWindow) return
        try {
            windowManager.updateViewLayout(this, params)
        } catch (e: IllegalArgumentException) {
            // Removed between the check and the call (Hide during a snap): nothing to move.
        }
    }

    override fun onDetachedFromWindow() {
        setRunning(false)
        snapAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SNAP_MS = 280L
    }
}
