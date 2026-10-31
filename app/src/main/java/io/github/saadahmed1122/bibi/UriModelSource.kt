package io.github.saadahmed1122.bibi

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.github.saadahmed1122.bibi.domain.usecase.ModelSource
import java.io.FileNotFoundException
import java.io.InputStream

/** A file the user picked with the system file picker (SPEC-model-import.md: SAF, no storage permission). */
class UriModelSource(private val resolver: ContentResolver, private val uri: Uri) : ModelSource {
    override val size: Long? by lazy {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }

    override fun open(): InputStream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
}
