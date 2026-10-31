package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.ModelFile
import io.github.saadahmed1122.bibi.domain.model.ModelState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Where the model bytes come from (a SAF Uri in the app, a byte array in tests). */
interface ModelSource {
    /** Byte count when the provider reports one, otherwise null. */
    val size: Long?

    fun open(): InputStream
}

/**
 * Copies a model into [targetDir] as `<fileName>.part`, hashing as it goes, and renames it into place
 * only when size and SHA-256 match (SPEC-model-import.md).
 *
 * Returns [ModelState.Ready] or [ModelState.Invalid] ("size" or "checksum"). Progress goes to
 * `onProgress` (0..1, rising, once per whole percent, last call 1.0 on a full copy). Cancelling the
 * caller's coroutine deletes the `.part` file and rethrows `CancellationException`; the caller maps
 * that to [ModelState.Missing].
 */
class ImportModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fileName: String = ModelFile.NAME,
    private val expectedSize: Long = ModelFile.SIZE_BYTES,
    private val expectedSha256: String = ModelFile.SHA256,
    private val bufferSize: Int = 64 * 1024,
) {
    suspend operator fun invoke(
        source: ModelSource,
        targetDir: File,
        onProgress: (Float) -> Unit = {},
    ): ModelState = withContext(dispatcher) {
        val known = source.size
        if (known != null && known != expectedSize) return@withContext ModelState.Invalid("size")

        targetDir.mkdirs()
        val part = File(targetDir, "$fileName.part")
        val target = File(targetDir, fileName)
        var done = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastPercent = 0L
            source.open().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(bufferSize)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > expectedSize) return@withContext ModelState.Invalid("size")
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        val percent = copied * 100 / expectedSize
                        if (percent > lastPercent) {
                            lastPercent = percent
                            onProgress(copied.toFloat() / expectedSize)
                        }
                    }
                }
            }
            if (copied != expectedSize) return@withContext ModelState.Invalid("size")
            if (digest.digest().toHex() != expectedSha256.lowercase()) return@withContext ModelState.Invalid("checksum")
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            done = true
            ModelState.Ready(target.path)
        } finally {
            if (!done) part.delete()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
