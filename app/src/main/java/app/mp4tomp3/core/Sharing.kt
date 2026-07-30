package app.mp4tomp3.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.mp4tomp3.R

/**
 * MediaStore URIs are directly shareable, so no FileProvider is involved:
 * whatever the user picks in the chooser (kDrive, Telegram, WhatsApp, …) gets
 * a temporary read grant on the real file in Downloads.
 */
object Sharing {

    fun share(context: Context, items: List<ConversionItem>) {
        val uris = items.mapNotNull { it.outputUri }
        if (uris.isEmpty()) return

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = "audio/mpeg"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Without this the chooser preview falls back to the MediaStore row id.
        if (items.size == 1) {
            intent.putExtra(Intent.EXTRA_TITLE, items.first().outputName)
            intent.putExtra(Intent.EXTRA_SUBJECT, items.first().outputName)
        }

        val chooser = Intent.createChooser(intent, context.getString(R.string.share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
