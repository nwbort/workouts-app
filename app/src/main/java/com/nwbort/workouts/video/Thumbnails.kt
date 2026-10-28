package com.nwbort.workouts.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Still frames for the video list. Pulling a frame out of a half-hour video
 * takes a moment, so each one is kept on disk, keyed by the file's URI (a
 * re-linked video gets a fresh one).
 */
class Thumbnails(private val context: Context) {
    private val memory = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    /** URIs that couldn't be decoded this run, so scrolling doesn't keep retrying them. */
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val dir = File(context.cacheDir, "thumbnails")

    // Decoders are heavy, and the list asks for every visible row at once.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(2)

    /** Without touching the disk, so a row scrolled back into view shows straight away. */
    fun cached(uri: Uri): Bitmap? = memory.get(key(uri))

    suspend fun load(uri: Uri): Bitmap? {
        val key = key(uri)
        memory.get(key)?.let { return it }
        if (key in failed) return null
        return withContext(io) {
            val file = File(dir, "$key.jpg")
            val bitmap = BitmapFactory.decodeFile(file.path) ?: extract(uri)?.also { save(it, file) }
            if (bitmap == null) failed += key else memory.put(key, bitmap)
            bitmap
        }
    }

    private fun extract(uri: Uri): Bitmap? = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, uri)
            // A fifth of the way in: the opening seconds are usually a title card.
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            r.getScaledFrameAtTime(ms * 1000 / 5, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT)
        }
    }.getOrNull()

    private fun save(bitmap: Bitmap, file: File) {
        runCatching {
            dir.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
    }

    private fun key(uri: Uri) = UUID.nameUUIDFromBytes(uri.toString().toByteArray()).toString()

    private companion object {
        const val WIDTH = 384
        const val HEIGHT = 216
    }
}
