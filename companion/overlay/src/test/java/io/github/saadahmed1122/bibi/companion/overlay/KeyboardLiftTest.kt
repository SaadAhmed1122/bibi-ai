package io.github.saadahmed1122.bibi.companion.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLiftTest {
    // vivo Y17s portrait: the card rests with its bottom at 1545; the keyboard's top is at 986.
    private fun next(current: Int, visible: Boolean, overlap: Int, windowBottom: Int = REST) =
        KeyboardLift.next(current, visible, overlap, windowBottom, restBottom = REST, gap = GAP)

    @Test
    fun aCoveredCardMovesAboveTheKeyboard() {
        assertEquals(581, next(current = 0, visible = true, overlap = 559))
    }

    @Test
    fun theSameReportTwiceGivesTheSameLift() {
        // A second report can arrive before the window has moved.
        assertEquals(581, next(current = 581, visible = true, overlap = 559))
    }

    @Test
    fun aCardClearOfTheKeyboardStaysPut() {
        assertEquals(581, next(current = 581, visible = true, overlap = 0, windowBottom = 964))
    }

    @Test
    fun aTallerKeyboardLiftsTheCardFurther() {
        // The keyboard grows by 100 px while the card sits 22 px above it, so 78 px are covered.
        assertEquals(681, next(current = 581, visible = true, overlap = 78, windowBottom = 964))
    }

    @Test
    fun aHiddenKeyboardDropsTheCardBack() {
        assertEquals(0, next(current = 581, visible = false, overlap = 0, windowBottom = 964))
    }

    private companion object {
        const val REST = 1545
        const val GAP = 22
    }
}
