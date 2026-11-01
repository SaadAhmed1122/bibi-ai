package io.github.saadahmed1122.bibi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Gradle runs unit tests with the module directory as the working directory. */
class AssetTextTest {
    private fun asset(text: AssetText) = File("src/main/assets/${text.file}")

    @Test
    fun everyAssetExists() {
        AssetText.entries.forEach { assertTrue(it.file, asset(it).isFile) }
    }

    @Test
    fun inAppPolicyMatchesTheHostedOne() {
        assertEquals(File("../docs/privacy-policy.md").readText(), asset(AssetText.PRIVACY_POLICY).readText())
    }

    @Test
    fun licensesCreditEveryModelAndRuntime() {
        val licenses = asset(AssetText.LICENSES).readText()
        listOf(
            "Gemma Terms of Use", "EmbeddingGemma", "Parakeet", "CC BY 4.0", "amy", "CC BY-SA 4.0",
            "LiteRT-LM", "MediaPipe", "sherpa-onnx", "ONNX Runtime", "MIT License", "Apache License 2.0",
        ).forEach { assertTrue(it, licenses.contains(it)) }
    }
}
