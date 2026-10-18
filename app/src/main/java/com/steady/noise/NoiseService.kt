package com.steady.noise

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.steady.noise.audio.NoisePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NoiseService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var session = 0
    private lateinit var player: NoisePlayer

    override fun onCreate() {
        super.onCreate()
        createChannel()
        player = NoisePlayer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopPlayback()
            ACTION_START -> {
                val type = intent.getStringExtra(EXTRA_TYPE)
                    ?.let { runCatching { NoiseType.valueOf(it) }.getOrNull() }
                    ?: NoiseType.WHITE
                val now = SystemClock.elapsedRealtime()
                val requestedEnd = intent.getLongExtra(EXTRA_END_ELAPSED, 0L)
                val end = if (requestedEnd > now && requestedEnd - now <= MAX_DURATION_MS) {
                    requestedEnd
                } else {
                    val minutes = intent.getIntExtra(EXTRA_MINUTES, DEFAULT_MINUTES)
                        .coerceIn(1, MAX_MINUTES)
                    now + minutes * 60_000L
                }
                val detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty()
                startPlayback(type, end, detail)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session += 1
        ticker?.cancel()
        if (::player.isInitialized) {
            player.stop()
        }
        PlaybackState.snapshot.value = PlaybackSnapshot(playing = false)
        scope.cancel()
        super.onDestroy()
    }

    private fun startPlayback(type: NoiseType, end: Long, detail: String) {
        val id = ++session
        val notification = buildNotification(type, end - SystemClock.elapsedRealtime(), detail)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        PlaybackState.snapshot.value = PlaybackSnapshot(
            playing = true,
            noiseType = type,
            endElapsedRealtime = end,
            detail = detail,
        )
        player.start(type, end) {
            if (id == session) {
                finishPlayback()
            }
        }
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && id == session) {
                val remaining = end - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(type, remaining, detail))
                delay(1_000)
            }
        }
    }

    private fun stopPlayback() {
        session += 1
        player.stop()
        finishPlayback()
    }

    private fun finishPlayback() {
        ticker?.cancel()
        if (!PlaybackState.snapshot.value.playing) return
        PlaybackState.snapshot.value = PlaybackSnapshot(playing = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setSound(null, null)
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(type: NoiseType, remainingMs: Long, detail: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, NoiseService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(type.label())
            .setContentText(
                if (detail.isEmpty()) {
                    formatRemaining(remainingMs) + " left"
                } else {
                    detail + " · " + formatRemaining(remainingMs) + " left"
                },
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.stop),
                    stop,
                ).build(),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_START = "com.steady.noise.START"
        const val ACTION_STOP = "com.steady.noise.STOP"
        const val EXTRA_TYPE = "type"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_END_ELAPSED = "end_elapsed"
        const val EXTRA_DETAIL = "detail"
        const val MAX_MINUTES = 720
        const val DEFAULT_MINUTES = 30
        private const val MAX_DURATION_MS = 24 * 60 * 60 * 1000L
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "playback"
    }
}

fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
