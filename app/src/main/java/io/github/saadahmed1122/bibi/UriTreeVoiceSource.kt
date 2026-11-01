package io.github.saadahmed1122.bibi

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import io.github.saadahmed1122.bibi.domain.usecase.VoicePackSource

/**
 * The voice pack files inside a folder the user picked (OpenDocumentTree, no storage permission).
 * Walks the tree once, up to [MAX_DEPTH] levels; a file counts when its name matches and one of its
 * parent folders' names contains the hint ("parakeet" or "piper").
 */
class UriTreeVoiceSource(private val resolver: ContentResolver, tree: Uri) : VoicePackSource {
    private data class Found(val uri: Uri, val size: Long, val path: String)

    private val files: List<Found> by lazy {
        val out = mutableListOf<Found>()
        fun walk(docId: String, path: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            resolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1)
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) walk(id, "$path/$name", depth + 1)
                    } else {
                        out += Found(DocumentsContract.buildDocumentUriUsingTree(tree, id), if (c.isNull(3)) -1 else c.getLong(3), "$path/$name")
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), "", 0)
        out
    }

    override fun find(sourceHint: String, name: String) = files
        .firstOrNull { it.path.substringAfterLast('/') == name && it.path.substringBeforeLast('/').contains(sourceHint, ignoreCase = true) }
        ?.let { UriModelSource(resolver, it.uri) }

    private companion object {
        const val MAX_DEPTH = 3
    }
}
