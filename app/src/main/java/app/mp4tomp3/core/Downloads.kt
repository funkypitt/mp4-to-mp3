package app.mp4tomp3.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.mp4tomp3.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /**
     * A name for the picked video, good enough to become the MP3's name.
     *
     * The system photo picker deliberately hides real filenames: it reports
     * `_display_name` as `<media id>.mp4`. So for picker URIs we prefer the
     * video's own title tag, then its capture date, and only fall back to the
     * synthetic name. Files chosen through the file browser, or shared in from
     * another app, do carry their real name and are used as-is.
     */
    fun displayName(context: Context, uri: Uri): String {
        var display: String? = null
        var takenAtMillis: Long? = null

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    display = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)
                    takenAtMillis = cursor.longOrNull(MediaStore.MediaColumns.DATE_TAKEN)
                        ?: cursor.longOrNull(MediaStore.MediaColumns.DATE_ADDED)?.times(1000)
                }
            }
        }

        if (!isPhotoPickerUri(uri)) {
            return display ?: uri.lastPathSegment?.substringAfterLast('/') ?: fallbackName(context)
        }

        return embeddedTitle(context, uri)
            ?: takenAtMillis?.let { dateName(context, it) }
            ?: display
            ?: fallbackName(context)
    }

    /** "Holiday clip.mp4" -> "Holiday clip.mp3" */
    fun toMp3Name(videoName: String): String {
        val base = videoName.substringBeforeLast('.', videoName).trim().ifBlank { "audio" }
        return "$base.mp3"
    }

    /** content://media/picker/0/<authority>/media/<id> */
    private fun isPhotoPickerUri(uri: Uri): Boolean =
        uri.authority == MediaStore.AUTHORITY &&
            uri.pathSegments.firstOrNull()?.startsWith("picker") == true

    /** Videos downloaded from the web often carry a real title; camera clips do not. */
    private fun embeddedTitle(context: Context, uri: Uri): String? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun dateName(context: Context, millis: Long): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date(millis))
        return "${context.getString(R.string.video_name_prefix)} $stamp"
    }

    private fun fallbackName(context: Context): String =
        context.getString(R.string.video_name_prefix)

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index).takeIf { it > 0 } else null
    }
}
