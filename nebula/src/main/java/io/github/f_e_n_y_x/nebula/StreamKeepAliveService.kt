package io.github.f_e_n_y_x.nebula

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the process in the foreground while a stream runs, so switching apps or opening the
 * notification shade doesn't kill the session. Its ongoing notification returns to the stream or
 * disconnects it. Started when the stream goes live; stopped when it ends.
 */
class StreamKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            container.stream.stop(quitApp = false)
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "your PC"
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(title), type) }
            .onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    private fun notification(title: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Streaming", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a stream is running, so it survives switching apps."
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, StreamKeepAliveService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_nebula_monochrome)
            .setContentTitle("Streaming $title")
            .setContentText("Tap to return to the stream")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(open)
            .addAction(0, "Disconnect", disconnect)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "stream"
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_TITLE = "title"
        private const val ACTION_DISCONNECT = "io.github.f_e_n_y_x.nebula.DISCONNECT"

        /** Call while the app is in the foreground (a foreground service can't start from the background). */
        fun start(context: Context, title: String) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, StreamKeepAliveService::class.java).putExtra(EXTRA_TITLE, title))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StreamKeepAliveService::class.java))
        }
    }
}
