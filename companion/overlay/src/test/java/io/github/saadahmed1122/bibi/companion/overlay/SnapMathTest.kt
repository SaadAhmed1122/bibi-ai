package io.github.saadahmed1122.bibi.companion.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapMathTest {
    @Test
    fun snapsLeftWhenCenterIsLeftOfMiddle() {
        assertEquals(0, SnapMath.snapTarget(x = 100, viewWidth = 200, screenWidth = 1080, insetLeft = 0, insetRight = 0))
    }

    @Test
    fun snapsRightWhenCenterIsRightOfMiddle() {
        assertEquals(880, SnapMath.snapTarget(x = 500, viewWidth = 200, screenWidth = 1080, insetLeft = 0, insetRight = 0))
    }

    @Test
    fun exactMiddleSnapsRight() {
        // Center 540 == middle 540: ties go right, as in S1.
        assertEquals(880, SnapMath.snapTarget(x = 440, viewWidth = 200, screenWidth = 1080, insetLeft = 0, insetRight = 0))
    }

    @Test
    fun oneLeftOfMiddleSnapsLeft() {
        assertEquals(0, SnapMath.snapTarget(x = 439, viewWidth = 200, screenWidth = 1080, insetLeft = 0, insetRight = 0))
    }

    @Test
    fun respectsInsets() {
        assertEquals(40, SnapMath.snapTarget(x = 60, viewWidth = 200, screenWidth = 1080, insetLeft = 40, insetRight = 80))
        assertEquals(800, SnapMath.snapTarget(x = 700, viewWidth = 200, screenWidth = 1080, insetLeft = 40, insetRight = 80))
    }

    @Test
    fun middleIsMeasuredInsideInsets() {
        // Usable area 100..980, middle 540: center 539 goes left, center 540 goes right.
        assertEquals(100, SnapMath.snapTarget(x = 439, viewWidth = 200, screenWidth = 1080, insetLeft = 100, insetRight = 100))
        assertEquals(780, SnapMath.snapTarget(x = 440, viewWidth = 200, screenWidth = 1080, insetLeft = 100, insetRight = 100))
    }

    @Test
    fun viewWiderThanUsableAreaPinsToLeftInset() {
        assertEquals(10, SnapMath.snapTarget(x = 500, viewWidth = 1200, screenWidth = 1080, insetLeft = 10, insetRight = 10))
    }

    @Test
    fun clampKeepsPositionThatAlreadyFits() {
        assertEquals(Point(100, 300), SnapMath.clamp(Point(100, 300), Size(200, 200), Size(1080, 2400), Insets()))
    }

    @Test
    fun clampAfterRotationLandscapeToPortrait() {
        // Snapped right in landscape (2400 wide), then rotated to portrait (1080 wide).
        val clamped = SnapMath.clamp(Point(2200, 700), Size(200, 200), Size(1080, 2400), Insets(top = 80, bottom = 48))
        assertEquals(Point(880, 700), clamped)
    }

    @Test
    fun clampAfterRotationPortraitToLandscape() {
        // Low on a portrait screen (2400 tall), then rotated to landscape (1080 tall).
        val clamped = SnapMath.clamp(Point(0, 2000), Size(200, 200), Size(2400, 1080), Insets(left = 80, bottom = 48))
        assertEquals(Point(80, 832), clamped)
    }

    @Test
    fun clampNegativePositionMovesToInsets() {
        assertEquals(Point(16, 24), SnapMath.clamp(Point(-50, -50), Size(200, 200), Size(1080, 2400), Insets(16, 24, 16, 24)))
    }

    @Test
    fun clampViewLargerThanScreenPinsToStartInsets() {
        assertEquals(Point(5, 7), SnapMath.clamp(Point(300, 300), Size(2000, 3000), Size(1080, 2400), Insets(5, 7, 5, 7)))
    }
}
