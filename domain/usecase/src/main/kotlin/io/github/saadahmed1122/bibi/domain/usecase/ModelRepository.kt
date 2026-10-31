package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.ModelFile
import io.github.saadahmed1122.bibi.domain.model.ModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * The model's state for the whole app (SPEC-model-import.md). At creation it checks only that the
 * file exists with the right size, so startup never hashes 584 MB; the full SHA-256 runs on import.
 */
class ModelRepository(
    private val modelsDir: File,
    private val importModel: ImportModel = ImportModel(),
    private val fileName: String = ModelFile.NAME,
    private val expectedSize: Long = ModelFile.SIZE_BYTES,
) {
    private val file = File(modelsDir, fileName)

    private val _state = MutableStateFlow(
        when {
            !file.isFile -> ModelState.Missing
            file.length() != expectedSize -> ModelState.Invalid("size")
            else -> ModelState.Ready(file.path)
        },
    )
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** Copies and verifies [source]. Cancelling the caller returns the state to Missing. */
    suspend fun import(source: ModelSource) {
        _state.value = ModelState.Importing(0f)
        _state.value = try {
            importModel(source, modelsDir) { _state.value = ModelState.Importing(it) }
        } catch (e: CancellationException) {
            _state.value = ModelState.Missing
            throw e
        } catch (e: IOException) {
            ModelState.Invalid("read")
        }
    }

    fun remove() {
        file.delete()
        _state.value = ModelState.Missing
    }
}
