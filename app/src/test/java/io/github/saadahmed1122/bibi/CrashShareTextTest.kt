package io.github.saadahmed1122.bibi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashShareTextTest {
    @Test
    fun emptyLogSharesTheEmptyMessage() {
        assertEquals("none", crashShareText("  \n", "H", "none"))
    }

    @Test
    fun keepsTheHeaderAndOnlyTheNewestPart() {
        val text = crashShareText("old-" + "x".repeat(100) + "-newest", "H", "none", maxChars = 20)
        assertTrue(text.startsWith("H\n\n"))
        assertTrue(text.endsWith("-newest"))
        assertEquals("H\n\n".length + 20, text.length)
    }
}
