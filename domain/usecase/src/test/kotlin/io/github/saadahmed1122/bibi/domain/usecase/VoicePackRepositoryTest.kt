package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.model.VoicePack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class VoicePackRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private val sttTokens = "stt tokens".toByteArray()
    private val ttsTokens = "tts tokens!".toByteArray()
    private val model = ByteArray(1000) { it.toByte() }
    private val files = listOf(
        VoicePack.Entry(VoicePack.STT_DIR, "parakeet", "tokens.txt", sttTokens.size.toLong(), sha(sttTokens)),
        VoicePack.Entry(VoicePack.TTS_DIR, "piper", "tokens.txt", ttsTokens.size.toLong(), sha(ttsTokens)),
        VoicePack.Entry(VoicePack.TTS_DIR, "piper", "model.onnx", model.size.toLong(), sha(model)),
    )

    private fun source(content: Map<Pair<String, String>, ByteArray>) = VoicePackSource { hint, name ->
        content[hint to name]?.let { bytes -> object : ModelSource { override val size = bytes.size.toLong(); override fun open() = ByteArrayInputStream(bytes) } }
    }

    private val all = mapOf(("parakeet" to "tokens.txt") to sttTokens, ("piper" to "tokens.txt") to ttsTokens, ("piper" to "model.onnx") to model)

    @Test
    fun importsEveryFileIntoItsFolderTellingTheTwoTokensApart() = runTest {
        val dir = tmp.newFolder("voice")
        val repo = VoicePackRepository(dir, files)
        assertEquals(ModelState.Missing, repo.state.value)
        repo.import(source(all))
        assertTrue(repo.state.value is ModelState.Ready)
        assertEquals("stt tokens", dir.resolve("${VoicePack.STT_DIR}/tokens.txt").readText())
        assertEquals("tts tokens!", dir.resolve("${VoicePack.TTS_DIR}/tokens.txt").readText())
        // A restart sees the complete pack without hashing.
        assertTrue(VoicePackRepository(dir, files).state.value is ModelState.Ready)
    }

    @Test
    fun aMissingFileIsNamed() = runTest {
        val repo = VoicePackRepository(tmp.newFolder("voice"), files)
        repo.import(source(all - ("piper" to "model.onnx")))
        assertEquals(ModelState.Invalid("missing:model.onnx"), repo.state.value)
    }

    @Test
    fun aWrongFileIsRefused() = runTest {
        val repo = VoicePackRepository(tmp.newFolder("voice"), files)
        repo.import(source(all + (("piper" to "model.onnx") to ByteArray(1000) { 7 })))
        assertEquals(ModelState.Invalid("checksum"), repo.state.value)
    }

    @Test
    fun removeDeletesThePack() = runTest {
        val dir = tmp.newFolder("voice")
        val repo = VoicePackRepository(dir, files)
        repo.import(source(all))
        repo.remove()
        assertEquals(ModelState.Missing, repo.state.value)
        assertTrue(!dir.exists())
    }
}
