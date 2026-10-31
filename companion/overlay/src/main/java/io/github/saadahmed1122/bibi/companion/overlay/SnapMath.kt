package io.github.saadahmed1122.bibi.companion.overlay

/** Screen insets in pixels (system bars, cutouts) that the overlay must stay out of. */
data class Insets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/** A window position in pixels (top-left corner). */
data class Point(val x: Int, val y: Int)

/** A width and height in pixels. */
data class Size(val width: Int, val height: Int)

/** Pure placement math for the floating window. No Android imports, so it runs on a plain JVM. */
object SnapMath {
    /**
     * The x to snap to on release: the left edge ([insetLeft]) or the right edge
     * (`screenWidth - insetRight - viewWidth`), whichever is nearer to the view's center.
     * The middle is measured inside the insets. A center exactly on the middle snaps right, as in S1.
     * If the view is wider than the usable area, it pins to the left edge.
     */
    fun snapTarget(x: Int, viewWidth: Int, screenWidth: Int, insetLeft: Int, insetRight: Int): Int {
        val left = insetLeft
        val right = screenWidth - insetRight - viewWidth
        if (right <= left) return left
        // center < middle, doubled to stay in integers: 2x + w < insetLeft + (screenWidth - insetRight).
        return if (2 * x + viewWidth < insetLeft + screenWidth - insetRight) left else right
    }

    /**
     * Keeps the view fully inside the screen minus [insets] on both axes, e.g. after a rotation.
     * If the view doesn't fit on an axis, it pins to that axis's start inset.
     */
    fun clamp(position: Point, viewSize: Size, screenSize: Size, insets: Insets): Point = Point(
        x = clampAxis(position.x, viewSize.width, screenSize.width, insets.left, insets.right),
        y = clampAxis(position.y, viewSize.height, screenSize.height, insets.top, insets.bottom),
    )

    private fun clampAxis(value: Int, view: Int, screen: Int, start: Int, end: Int): Int =
        value.coerceIn(start, (screen - end - view).coerceAtLeast(start))
}
