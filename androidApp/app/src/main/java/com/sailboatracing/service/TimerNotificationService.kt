package com.sailboatracing.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sailboatracing.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerNotificationService : Service() {

    companion object {
        const val ACTION_START  = "timer.START"
        const val ACTION_STOP   = "timer.STOP"
        const val EXTRA_REMAINING_MS   = "remaining_ms"
        const val EXTRA_TARGET_EPOCH_MS = "target_epoch_ms"

        private const val CHANNEL_ID   = "race_timer"
        private const val NOTIFICATION_ID = 1001

        fun startIntent(ctx: Context, remainingMs: Long, targetEpochMs: Long?): Intent =
            Intent(ctx, TimerNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REMAINING_MS, remainingMs)
                if (targetEpochMs != null) putExtra(EXTRA_TARGET_EPOCH_MS, targetEpochMs)
            }

        fun stopIntent(ctx: Context): Intent =
            Intent(ctx, TimerNotificationService::class.java).apply { action = ACTION_STOP }
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var tickJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val remainingMs   = intent.getLongExtra(EXTRA_REMAINING_MS, 0L)
                val targetEpochMs = intent.getLongExtra(EXTRA_TARGET_EPOCH_MS, -1L)
                    .takeIf { it >= 0L }
                startForeground(NOTIFICATION_ID, buildNotification(remainingMs))
                startTicking(remainingMs, targetEpochMs)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startTicking(initialRemainingMs: Long, targetEpochMs: Long?) {
        tickJob?.cancel()
        tickJob = scope.launch {
            var remainingMs = initialRemainingMs
            while (remainingMs > 0L) {
                delay(1_000L)
                remainingMs = if (targetEpochMs != null) {
                    (targetEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
                } else {
                    (remainingMs - 1_000L).coerceAtLeast(0L)
                }
                notificationManager.notify(NOTIFICATION_ID, buildNotification(remainingMs))
            }
            // Timer hit zero — show "START!" briefly then stop
            notificationManager.notify(NOTIFICATION_ID, buildNotification(0L))
            delay(5_000L)
            stopSelf()
        }
    }

    private fun buildNotification(remainingMs: Long) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Race Timer")
            .setContentText(formatTime(remainingMs))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent())
            .build()

    private fun formatTime(ms: Long): String {
        if (ms <= 0L) return "START!"
        val minutes = ms / 60_000L
        val seconds = (ms % 60_000L) / 1_000L
        return "%d:%02d".format(minutes, seconds)
    }

    private fun tapIntent(): PendingIntent {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Race Timer",
            NotificationManager.IMPORTANCE_LOW  // silent — no sound on each update
        ).apply {
            description = "Shows countdown to race start"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
