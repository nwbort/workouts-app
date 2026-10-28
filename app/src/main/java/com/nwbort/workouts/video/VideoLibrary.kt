package com.nwbort.workouts.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.nwbort.workouts.data.Repository
import com.nwbort.workouts.data.VideoEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoState(
    /** The folder the user granted, if any. */
    val tree: Uri? = null,
    /** False when the folder is set but Android no longer lets us read it. */
    val treeReadable: Boolean = true,
    val scanning: Boolean = false,
    /** Vimeo id -> playable document URI. */
    val found: Map<String, Uri> = emptyMap(),
    /** Ids that were linked by hand rather than found in the folder. */
    val linked: Set<String> = emptySet(),
)

/**
 * Finds the downloaded session videos on the phone.
 *
 * The user grants one folder through the system picker (Storage Access
 * Framework); the grant is persisted, so no storage permission is needed and
 * the folder can be anywhere. Files are matched to sessions by name: the
 * streek downloader names each one after its session title
 * ("strength-workout-9.mp4"), and matching is forgiving about case,
 * punctuation and extension. Anything it can't match can be linked by hand.
 */
class VideoLibrary(
    private val context: Context,
    private val repo: Repository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(VideoState())
    val state: StateFlow<VideoState> = _state.asStateFlow()

    init {
        scope.launch {
            repo.settings.map { it.videoTree to it.videoLinks }.distinctUntilChanged().collect { (tree, links) ->
                rescan(tree?.let(Uri::parse), links)
            }
        }
    }

    fun uriFor(videoId: String?): Uri? = videoId?.let { _state.value.found[it] }

    /** Call with the result of ACTION_OPEN_DOCUMENT_TREE. */
    suspend fun setFolder(tree: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val old = repo.settings.first().videoTree
        if (old != null && old != tree.toString()) release(Uri.parse(old))
        repo.updateSettings { videoTree = tree.toString() }
    }

    /** Call with the result of ACTION_OPEN_DOCUMENT for one video, or null to unlink it. */
    suspend fun link(videoId: String, file: Uri?) {
        val old = repo.settings.first().videoLinks[videoId]
        if (file != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(file, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (old != null && old != file?.toString()) release(Uri.parse(old))
        repo.updateSettings { linkVideo(videoId, file?.toString()) }
    }

    fun refresh() {
        scope.launch {
            val s = repo.settings.first()
            rescan(s.videoTree?.let(Uri::parse), s.videoLinks)
        }
    }

    private fun release(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private suspend fun rescan(tree: Uri?, links: Map<String, String>) {
        _state.update { it.copy(tree = tree, scanning = tree != null) }
        val (readable, files) = withContext(Dispatchers.IO) {
            if (tree == null) true to emptyMap() else runCatching { true to listVideos(tree) }.getOrElse { false to emptyMap() }
        }
        val found = HashMap<String, Uri>()
        for (v in repo.videos) match(v, files)?.let { found[v.id] = it }
        val linked = links.keys.filter { it in repo.videosById }.toSet()
        for (id in linked) found[id] = Uri.parse(links.getValue(id))
        _state.value = VideoState(tree, readable, false, found, linked)
    }

    /** Normalised file stem -> document URI, for every video up to a few folders deep. */
    private fun listVideos(tree: Uri): Map<String, Uri> {
        val out = HashMap<String, Uri>()
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (mime == Document.MIME_TYPE_DIR) {
                        if (depth < 3) walk(id, depth + 1)
                    } else if (mime?.startsWith("video/") == true || name.substringAfterLast('.').lowercase() in EXTENSIONS) {
                        out.putIfAbsent(normalise(name.substringBeforeLast('.')), DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
            } ?: error("folder not readable")
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        return out
    }

    companion object {
        private val EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "mov")

        fun normalise(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        /** [files] is keyed by normalised file stem. */
        fun <T> match(v: VideoEntry, files: Map<String, T>): T? =
            files[normalise(v.file.substringBeforeLast('.'))]
                ?: files[normalise(v.title)]
                // The downloader appends the Vimeo id if two titles ever collide.
                ?: files.entries.firstOrNull { it.key.split('-').contains(v.id) }?.value

        /** "primary:Movies/Streek" -> "Movies/Streek", for showing which folder is in use. */
        fun describe(tree: Uri): String = runCatching {
            val id = DocumentsContract.getTreeDocumentId(tree)
            id.substringAfter(':').ifBlank { "Internal storage" }
        }.getOrDefault(tree.toString())
    }
}
