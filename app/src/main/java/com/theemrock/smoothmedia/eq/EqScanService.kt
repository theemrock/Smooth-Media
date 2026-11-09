package com.theemrock.smoothmedia.eq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.theemrock.smoothmedia.playback.CatalogPlaybackLocationResolver
import com.theemrock.smoothmedia.playback.PlaybackLocationSelector
import com.theemrock.smoothmedia.source.SourceRegistryFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Progress of the running scan, polled by the UI. */
object EqScanState {
    @Volatile var running = false
    @Volatile var done = 0
    @Volatile var total = 0
}

/**
 * Foreground service that scans the library for song EQ profiles, started and stopped by the user.
 * Resumable: it only works on tracks without a current profile, so stopping loses nothing.
 */
class EqScanService : Service() {
    @Volatile private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "EQ analizi", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            if (!EqScanState.running) stopSelf()
            return START_NOT_STICKY
        }
        if (EqScanState.running) return START_NOT_STICKY
        EqScanState.running = true
        stopRequested = false
        startForeground(NOTIFICATION_ID, notification(EqScanState.done, EqScanState.total), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        thread(name = "eq-scan") { runScan() }
        return START_NOT_STICKY
    }

    /** Android 15 caps data-sync services; stop cleanly, the next manual start resumes where this one ended. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopRequested = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRequested = true
        super.onDestroy()
    }

    private fun runScan() {
        val wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmoothMedia:EqScan")
            .apply { setReferenceCounted(false); acquire(WAKE_LOCK_MS) }
        val runtime = SourceRegistryFactory.create(this)
        try {
            val dao = runtime.database.eqDao()
            val store = RoomEqProfileStore(dao)
            val resolver = CatalogPlaybackLocationResolver(runtime.database)
            val scanner = EqScanner(
                decoder = MediaCodecPcmDecoder(this),
                store = store,
                uriFor = { id -> PlaybackLocationSelector.select(resolver.resolve(id))?.contentUri },
                onTiming = { id, totalMs, analyzeMs -> Log.i(TAG, "track=$id total=${totalMs}ms analyze=${analyzeMs}ms") },
            )
            val pending = store.pendingTrackIds(EqProfileCodec.ANALYZER_VERSION)
            val done = AtomicInteger(dao.scannedCount(EqProfileCodec.ANALYZER_VERSION))
            EqScanState.total = dao.activeTrackCount()
            EqScanState.done = done.get()
            val next = AtomicInteger()
            val workers = List(WORKERS) { index ->
                thread(name = "eq-scan-$index") {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    while (!stopRequested) {
                        val i = next.getAndIncrement()
                        if (i >= pending.size) break
                        if (runCatching { scanner.scanTrack(pending[i]) }.getOrDefault(false)) {
                            val count = done.incrementAndGet()
                            EqScanState.done = count
                            if (count % NOTIFY_EVERY == 0) notifyProgress(count, EqScanState.total)
                        }
                    }
                }
            }
            workers.forEach { it.join() }
            if (!stopRequested && store.pendingTrackIds(EqProfileCodec.ANALYZER_VERSION).isEmpty()) {
                EqModeStore(this).markFirstScanComplete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "scan ended with error", e)
        } finally {
            runCatching { runtime.close() }
            if (wakeLock.isHeld) wakeLock.release()
            EqScanState.running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notifyProgress(done: Int, total: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(done, total))
    }

    @Suppress("DEPRECATION")
    private fun notification(done: Int, total: Int): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, EqScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("EQ analizi")
            .setContentText("$done / $total şarkı")
            .setProgress(total.coerceAtLeast(1), done, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stop)
            .build()
    }

    companion object {
        private const val TAG = "EqScan"
        private const val CHANNEL = "eq_scan"
        private const val NOTIFICATION_ID = 4211
        private const val ACTION_STOP = "com.theemrock.smoothmedia.eq.STOP_SCAN"
        // Measured: each worker sat at ~50% CPU (waiting on file reads and the codec) with most cores idle.
        private const val WORKERS = 6
        private const val NOTIFY_EVERY = 5
        private const val WAKE_LOCK_MS = 8 * 60 * 60 * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, EqScanService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, EqScanService::class.java).setAction(ACTION_STOP))
        }
    }
}
