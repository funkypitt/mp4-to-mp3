package app.mp4tomp3.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * Writes finished MP3s into the shared Downloads collection. Going through
 * MediaStore means no storage permission is ever needed, the file shows up in
 * the Files app immediately, and name collisions are resolved by the system.
 */
object Downloads {

    /**
     * Creates a pending entry. The file is invisible to other apps until
     * [publish] is called, so a failed conversion never leaves a broken MP3
     * behind.
     */
    fun createPending(context: Context, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return context.contentResolver
            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw ConversionException("Could not create the file in Downloads")
    }

    fun publish(context: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }

    fun discard(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** Display name of the picked video, falling back to the URI's last segment. */
    fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()

        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "video"
    }

    /** "Holiday clip.mp4" -> "Holiday clip.mp3" */
    fun toMp3Name(videoName: String): String {
        val base = videoName.substringBeforeLast('.', videoName).trim().ifBlank { "audio" }
        return "$base.mp3"
    }
}
