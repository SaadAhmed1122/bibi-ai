package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.model.VoicePack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Finds one voice pack file in what the user picked (a folder tree in the app). */
fun interface VoicePackSource {
    /** The file named [name] inside a folder whose name contains [sourceHint], or null. */
    fun find(sourceHint: String, name: String): ModelSource?
}

/**
 * The voice pack's state (SPEC-voice.md). Ready means every file is in [voiceDir] with the right size
 * (checked at creation without hashing); an import checks each file's SHA-256 while copying.
 * Invalid reasons: "missing:<file>", "size", "checksum", "read".
 */
class VoicePackRepository(
    private val voiceDir: File,
    private val files: List<VoicePack.Entry> = VoicePack.FILES,
) {
    private val _state = MutableStateFlow(if (complete()) ModelState.Ready(voiceDir.path) else ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private fun target(e: VoicePack.Entry) = File(File(voiceDir, e.dir), e.name)

    private fun complete() = files.all { target(it).let { f -> f.isFile && f.length() == it.size } }

    suspend fun import(source: VoicePackSource) {
        val total = files.sumOf { it.size }.toFloat()
        var done = 0L
        _state.value = ModelState.Importing(0f)
        try {
            for (e in files) {
                // Already in place from an earlier, interrupted import: keep it.
                if (target(e).let { it.isFile && it.length() == e.size }) {
                    done += e.size
                    continue
                }
                val src = source.find(e.sourceHint, e.name) ?: run {
                    _state.value = ModelState.Invalid("missing:${e.name}")
                    return
                }
                val result = ImportModel(fileName = e.name, expectedSize = e.size, expectedSha256 = e.sha256)(src, File(voiceDir, e.dir)) {
                    _state.value = ModelState.Importing((done + it * e.size) / total)
                }
                if (result !is ModelState.Ready) {
                    _state.value = result
                    return
                }
                done += e.size
            }
            _state.value = ModelState.Ready(voiceDir.path)
        } catch (c: CancellationException) {
            _state.value = if (complete()) ModelState.Ready(voiceDir.path) else ModelState.Missing
            throw c
        } catch (e: IOException) {
            _state.value = ModelState.Invalid("read")
        }
    }

    fun remove() {
        voiceDir.deleteRecursively()
        _state.value = ModelState.Missing
    }
}
