package io.github.saadahmed1122.bibi

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Gradle runs unit tests with the module directory as the working directory. */
class GemmaNoticeAssetTest {
    private val notice = File("src/main/assets/gemma_notice.txt").readText()

    @Test
    fun namesTheModelAndGoogle() {
        assertTrue(notice.contains("Gemma 3 1B"))
        assertTrue(notice.contains("Gemma is a model by Google"))
    }

    @Test
    fun pointsToTheGemmaTermsOfUse() {
        assertTrue(notice.contains("provided under and subject to the Gemma Terms of Use"))
        assertTrue(notice.contains("https://ai.google.dev/gemma/terms"))
    }
}
