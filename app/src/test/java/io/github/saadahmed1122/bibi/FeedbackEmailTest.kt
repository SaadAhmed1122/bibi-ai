package io.github.saadahmed1122.bibi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class FeedbackEmailTest {
    private val uri = FeedbackEmail.mailto(
        address = "someone@example.com",
        appVersion = "0.3.0-p3",
        model = "V2310",
        android = "15",
    )

    private fun param(name: String): String {
        val raw = uri.substringAfter("?").split("&").first { it.startsWith("$name=") }.substringAfter("=")
        return URLDecoder.decode(raw, "UTF-8")
    }

    @Test
    fun goesToTheAddressWithAVersionedSubject() {
        assertTrue(uri.startsWith("mailto:someone@example.com?"))
        assertEquals("Bibi AI feedback 0.3.0-p3", param("subject"))
    }

    @Test
    fun bodyHasVersionPhoneAndAndroidAndTheThreePromptsOnly() {
        val body = param("body")
        // Built from the version and phone only: no chat or memory text can reach it.
        assertEquals(9, body.lines().size)
        assertTrue(body.contains("App version: 0.3.0-p3"))
        assertTrue(body.contains("Phone: V2310, Android 15"))
        assertTrue(body.contains("What did you try?"))
        assertTrue(body.contains("What happened?"))
        assertTrue(body.contains("What did you expect?"))
    }

    @Test
    fun spacesAreEncodedAsPercent20NotPlus() {
        // Many mail apps show a literal "+" for spaces encoded as "+".
        assertFalse(uri.contains("+"))
        assertTrue(uri.contains("%20"))
    }
}
