package app.mp4tomp3.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import app.mp4tomp3.MainActivity
import app.mp4tomp3.R
import app.mp4tomp3.core.AudioConverter
import app.mp4tomp3.core.ConversionItem
import app.mp4tomp3.core.ConversionState
import app.mp4tomp3.core.Downloads
import app.mp4tomp3.core.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Runs the batch in the foreground so that leaving the app (to answer a
 * message, to lock the screen) does not kill a half-written file.
 */
class ConversionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            worker?.cancel()
            ConversionState.cancelPending()
            finish()
            return START_NOT_STICKY
        }

        startForegroundSafely(buildNotification(null, 0, 0))

        @Suppress("DEPRECATION")
        val uris: List<Uri> = intent?.getParcelableArrayListExtra<Uri>(EXTRA_URIS).orEmpty()
        if (uris.isNotEmpty()) {
            ConversionState.enqueue(uris.map { it to Downloads.displayName(this, it) })
        }

        if (worker?.isActive != true) {
            worker = scope.launch { runBatch() }
        }
        return START_NOT_STICKY
    }

    private suspend fun runBatch() {
        ConversionState.setRunning(true)
        acquireWakeLock()
        val job = coroutineContext.job
        try {
            while (coroutineContext.isActive) {
                val item = ConversionState.nextQueued() ?: break
                convert(item) { !job.isActive }
            }
        } finally {
            releaseWakeLock()
            ConversionState.setRunning(false)
            finish()
        }
    }

    private fun convert(item: ConversionItem, isCancelled: () -> Boolean) {
        ConversionState.update(item.id) { it.copy(status = Status.CONVERTING, progress = 0f) }
        lastNotifiedPercent = -1
        notifyProgress(item.sourceName, 0f)

        var target: Uri? = null
        try {
            val uri = Downloads.createPending(this, item.outputName)
            target = uri
            contentResolver.openOutputStream(uri).use { stream ->
                requireNotNull(stream) { "Could not open the destination file" }
                AudioConverter.convert(
                    context = this,
                    source = item.source,
                    title = item.outputName.substringBeforeLast('.'),
                    output = stream,
                    onProgress = { p ->
                        ConversionState.update(item.id) { it.copy(progress = p) }
                        notifyProgress(item.sourceName, p)
                    },
                    isCancelled = isCancelled,
                )
            }
            Downloads.publish(this, uri)
            ConversionState.update(item.id) {
                it.copy(status = Status.DONE, progress = 1f, outputUri = uri)
            }
        } catch (e: InterruptedException) {
            target?.let { Downloads.discard(this, it) }
            ConversionState.update(item.id) { it.copy(status = Status.CANCELLED) }
            throw CancellationException("cancelled")
        } catch (e: CancellationException) {
            target?.let { Downloads.discard(this, it) }
            ConversionState.update(item.id) { it.copy(status = Status.CANCELLED) }
            throw e
        } catch (e: Exception) {
            target?.let { Downloads.discard(this, it) }
            ConversionState.update(item.id) {
                it.copy(status = Status.FAILED, error = e.message ?: "Conversion failed")
            }
        }
    }

    // --- notification ------------------------------------------------------

    private var lastNotifiedPercent = -1

    private fun notifyProgress(name: String, progress: Float) {
        val percent = (progress * 100).toInt()
        if (percent == lastNotifiedPercent) return
        lastNotifiedPercent = percent

        val items = ConversionState.items.value
        val done = items.count { it.status == Status.DONE }
        val total = items.size
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(name, percent, done, total))
    }

    private fun buildNotification(
        name: String?,
        percent: Int,
        done: Int,
        total: Int = 0,
    ): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, ConversionService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (total > 0) getString(R.string.notif_title, (done + 1).coerceAtMost(total), total)
                else getString(R.string.notif_title_generic)
            )
            .setContentText(name ?: getString(R.string.notif_starting))
            .setProgress(100, percent, name == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.cancel), cancel)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundSafely(notification: Notification) {
        startForeground(
            NOTIFICATION_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- wake lock ---------------------------------------------------------

    private fun acquireWakeLock() {
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mp4tomp3:convert").apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "conversion"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_URIS = "uris"
        private const val ACTION_CANCEL = "app.mp4tomp3.CANCEL"

        fun start(context: Context, uris: List<Uri>) {
            val intent = Intent(context, ConversionService::class.java)
                .putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ConversionService::class.java).setAction(ACTION_CANCEL)
            context.startService(intent)
        }
    }
}
