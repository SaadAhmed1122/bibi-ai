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
 * - Behaviors ([Behaviors], SPEC-behaviors.md): a hop on tap, a squash while held and on landing,
 *   short reactions with a pose and a hop, sleep in quiet hours. The clock runs at 24 fps only while
 *   an act plays (at most 2.5 s), so the S1 battery numbers hold the rest of the time. On the right
 *   half Bibi is mirrored, so the thought dots and the raised tail point into the screen.
 */
@SuppressLint("ViewConstructor")
class BibiView(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onMoved: (x: Int, y: Int) -> Unit,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit = {},
    /** Reactions, greeting and sleep (SPEC-behaviors.md); the service owns it so it survives Hide. */
    private val behaviors: Behaviors = Behaviors(),
    private val hour: () -> Int = { 12 },
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
            behaviors.calm = value
            updateRate(System.nanoTime() / 1_000_000L)
            scheduleNextBlink(System.nanoTime())
            invalidate()
        }

    /** Plays [act] if [Behaviors] allowed it (non-null): faster clock until it ends. */
    fun play(act: Act?) {
        if (act == null) return
        updateRate(System.nanoTime() / 1_000_000L)
        invalidate()
    }

    private fun updateRate(nowMs: Long) {
        val fps = when {
            calm -> FrameSchedule.FPS_CALM
            behaviors.current(nowMs) != null -> FPS_ACT
            else -> FrameSchedule.FPS_NORMAL
        }
        val interval = FrameSchedule.intervalNanos(fps)
        if (schedule.intervalNanos != interval) {
            schedule.intervalNanos = interval
            schedule.reset()
        }
    }

    private val schedule = FrameSchedule(FrameSchedule.intervalNanos(FrameSchedule.FPS_NORMAL))

    /** One vsync period, read from the display once attached; 60 Hz until then. */
    private var vsyncNanos = 1_000_000_000L / 60

    init {
        contentDescription = context.getString(R.string.overlay_bibi_description_state, context.getString(BibiMood.IDLE.description))
        scheduleNextBlink(startNanos)
        setOnLongClickListener {
            onLongPress()
            true
        }
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
            updateRate(frameTimeNanos / 1_000_000L)
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
        post { updateMirror() }
    }

    private fun scheduleNextBlink(fromNanos: Long) {
        val delayMs = if (calm) 8_000L else Random.nextLong(3_000L, 6_001L)
        nextBlinkNanos = fromNanos + delayMs * 1_000_000L
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val nowMs = now / 1_000_000L
        val act = behaviors.current(nowMs)
        val t = behaviors.progress(nowMs)
        val asleep = mood == BibiMood.IDLE && behaviors.sleeping(nowMs, hour())
        // Asleep: a slower, deeper breath. Awake idle: the 3.5 s cycle. Off in Calm and during an act.
        val period = if (asleep) 6.0 else 3.5
        val breath = if (calm || mood != BibiMood.IDLE || act != null) 0f else (sin(2 * PI * ((now - startNanos) / 1e9) / period) * 0.02).toFloat()
        var squashY = if (act != null) ActMotion.squashY(act, t) else 1f
        if (dragging) squashY = GRABBED_SQUASH
        canvas.save()
        canvas.translate(0f, -(if (act != null) ActMotion.lift(act, t) else 0f) * height)
        // Scale around the paws so Bibi breathes and squashes upward instead of floating.
        canvas.scale((1f / squashY) * (1f - breath * 0.5f) * if (mirrored) -1f else 1f, squashY * (1f + breath), width / 2f, height.toFloat())
        dst.set(0, 0, width, height)
        val pose = if (mood != BibiMood.IDLE) mood else act?.pose
        val frame = when {
            pose != null -> frames.getValue(pose)
            asleep || now < blinkUntilNanos -> blink
            else -> frames.getValue(BibiMood.IDLE)
        }
        canvas.drawBitmap(frame, src, dst, paint)
        canvas.restore()
    }

    /** Mirrored on the right half of the screen (set after each snap). */
    private var mirrored = false

    private fun updateMirror() {
        val m = params.x + width / 2 > screenSize().width / 2
        if (m != mirrored) {
            mirrored = m
            invalidate()
        }
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
                longPressed = false
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
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
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                    behaviors.onTouch(System.nanoTime() / 1_000_000L)
                    removeCallbacks(longPress)
                    invalidate()
                }
                if (dragging) {
                    params.x = downX + dx.toInt()
                    params.y = downY + dy.toInt()
                    moveWindow()
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (dragging) drop() else if (!longPressed) performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (dragging) drop()
            }
        }
        return true
    }

    /** Held still for the long-press time: TalkBack's long-press action ends up here too. */
    private var longPressed = false
    private val longPress = Runnable {
        longPressed = true
        performLongClick()
    }

    override fun performClick(): Boolean {
        super.performClick()
        play(behaviors.onTap(System.nanoTime() / 1_000_000L))
        onTap()
        return true
    }

    private fun drop() {
        dragging = false
        play(behaviors.onDrop(System.nanoTime() / 1_000_000L))
        snapToEdge()
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
        updateMirror()
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
                    if (cancelled) return
                    updateMirror()
                    onMoved(params.x, params.y)
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

        /** Short acts get a smoother clock; 12 fps hops look jerky. Calm never uses it. */
        const val FPS_ACT = 24

        /** Held and dragged: a little flatter and wider, like a picked-up cat. */
        const val GRABBED_SQUASH = 0.94f
    }
}
