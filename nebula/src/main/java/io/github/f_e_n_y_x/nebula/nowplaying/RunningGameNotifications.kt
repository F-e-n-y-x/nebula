package io.github.f_e_n_y_x.nebula.nowplaying

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.f_e_n_y_x.nebula.MainActivity
import io.github.f_e_n_y_x.nebula.R
import io.github.f_e_n_y_x.nebula.domain.Elapsed
import io.github.f_e_n_y_x.nebula.domain.KeyValueStore
import io.github.f_e_n_y_x.nebula.domain.NowPlaying
import io.github.f_e_n_y_x.nebula.domain.PostedNotification
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import java.security.SecureRandom

/**
 * The "game running on your PC" notification: one per host (tagged with the host id), on its own
 * channel, dismissible. Every PendingIntent is immutable and explicit; the actions go to the
 * non-exported [RunningGameActionReceiver], Resume to [MainActivity] with a private token.
 */
class RunningGameNotifications(private val context: Context, private val store: KeyValueStore) {
    private val nm = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sys = context.getSystemService(NotificationManager::class.java) ?: return
        if (sys.getNotificationChannel(CHANNEL) != null) return
        sys.createNotificationChannel(
            NotificationChannel(CHANNEL, "Games running on your PC", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "After you leave Nebula, says when a game is still running on a paired PC, so you can stop it to save power."
                setShowBadge(true)
            },
        )
    }

    /** Checked at every post: the Android 13+ permission, app-level notifications and our channel. */
    fun permitted(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL)
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /** What is shown for [hostId] now; null when nothing (the user may have cleared it). */
    fun posted(hostId: String): PostedNotification? {
        val saved = store.get(KEY_POSTED + hostId) ?: return null
        val active = runCatching { nm.activeNotifications.any { it.id == ID && it.tag == hostId } }.getOrDefault(true)
        if (!active) {
            store.put(KEY_POSTED + hostId, null)
            return null
        }
        val stopped = saved.endsWith(STOPPED_SUFFIX)
        return PostedNotification(saved.removeSuffix(STOPPED_SUFFIX), stopped)
    }

    fun dismissed(hostId: String): String? = store.get(KEY_DISMISSED + hostId)
    fun markDismissed(hostId: String, sessionKey: String) {
        store.put(KEY_DISMISSED + hostId, sessionKey)
        store.put(KEY_POSTED + hostId, null)
    }
    fun clearDismissed() = hostIds(KEY_DISMISSED).forEach { store.put(KEY_DISMISSED + it, null) }

    /** Random, per install, never leaves the app: proves a Resume intent came from our notification. */
    fun resumeToken(): String = store.get(KEY_TOKEN) ?: ByteArray(24).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }.also { store.put(KEY_TOKEN, it) }

    fun showRunning(np: NowPlaying, alert: Boolean, nowEpochS: Long, error: String? = null, icon: Bitmap? = null) {
        val b = base(np.hostId)
            .setContentTitle(if (error != null) "Couldn't stop ${np.gameName}" else np.gameName)
            .setContentText(error ?: Elapsed.notificationText(np, nowEpochS))
            .setStyle(NotificationCompat.BigTextStyle().bigText(error ?: Elapsed.notificationText(np, nowEpochS)))
            .setOnlyAlertOnce(!alert)
            .setDeleteIntent(broadcast(ACTION_DISMISSED, np, 3))
            .addAction(0, "Resume", resumeIntent(np))
            .addAction(0, "Stop", broadcast(ACTION_STOP, np, 1))
            .addAction(0, "Don't notify", broadcast(ACTION_MUTE, np, 2))
        // A host-reported start time runs a live chronometer; a local one is only a lower bound.
        if (np.exact) b.setShowWhen(true).setWhen(np.sinceEpochS * 1000).setUsesChronometer(true) else b.setShowWhen(false)
        icon?.let { b.setLargeIcon(it) }
        if (post(np.hostId, b)) store.put(KEY_POSTED + np.hostId, np.sessionKey)
    }

    /** Stop pressed: no actions until the PC answers. */
    fun showStopping(np: NowPlaying) {
        val b = base(np.hostId)
            .setContentTitle("Stopping ${np.gameName}…")
            .setContentText("Asking ${np.hostName} to close it.")
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
        post(np.hostId, b)
    }

    /** The game closed; the note stays (dismissible) and goes away by itself after a while. */
    fun showStopped(np: NowPlaying, message: String) {
        val b = base(np.hostId)
            .setContentTitle("Stopped")
            .setContentText(message)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(STOPPED_TIMEOUT_MS)
        if (post(np.hostId, b)) store.put(KEY_POSTED + np.hostId, np.sessionKey + STOPPED_SUFFIX)
    }

    fun cancel(hostId: String) {
        nm.cancel(hostId, ID)
        store.put(KEY_POSTED + hostId, null)
    }

    fun cancelAll() {
        val tags = runCatching { nm.activeNotifications.filter { it.id == ID }.mapNotNull { it.tag } }.getOrDefault(emptyList())
        (tags + hostIds(KEY_POSTED)).toSet().forEach(::cancel)
    }

    private fun hostIds(prefix: String): List<String> =
        (store as? PrefixListing)?.keysWithPrefix(prefix)?.map { it.removePrefix(prefix) } ?: emptyList()

    private fun base(hostId: String): NotificationCompat.Builder {
        ensureChannel()
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_nebula_monochrome)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setGroup(GROUP)
            .setContentIntent(openApp(hostId))
    }

    private fun post(hostId: String, b: NotificationCompat.Builder): Boolean {
        if (!permitted()) return false
        return try {
            nm.notify(hostId, ID, b.build())
            true
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
            false
        }
    }

    private fun code(hostId: String, n: Int) = (hostId.hashCode() and 0x0fffffff) * 8 + n

    private fun openApp(hostId: String): PendingIntent = PendingIntent.getActivity(
        context, code(hostId, 0),
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun resumeIntent(np: NowPlaying): PendingIntent = PendingIntent.getActivity(
        context, code(np.hostId, 4),
        Intent(context, MainActivity::class.java).setAction(ACTION_RESUME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_HOST, np.hostId).putExtra(EXTRA_GAME, np.gameId)
            .putExtra(EXTRA_MODE, np.display?.name).putExtra(EXTRA_TOKEN, resumeToken()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(action: String, np: NowPlaying, n: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code(np.hostId, n),
        Intent(context, RunningGameActionReceiver::class.java).setAction(action).setPackage(context.packageName).also { putSession(it, np) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Implemented by the SharedPreferences store so every host's entries can be found. */
    interface PrefixListing { fun keysWithPrefix(prefix: String): List<String> }

    companion object {
        const val CHANNEL = "games_running"
        private const val GROUP = "games_running"
        private const val ID = 7301
        private const val STOPPED_TIMEOUT_MS = 30 * 60_000L
        private const val STOPPED_SUFFIX = "|stopped"
        private const val KEY_POSTED = "nowplaying_posted_"
        private const val KEY_DISMISSED = "nowplaying_dismissed_"
        private const val KEY_TOKEN = "nowplaying_resume_token"

        const val ACTION_RESUME = "io.github.f_e_n_y_x.nebula.action.RESUME_RUNNING"
        const val ACTION_STOP = "io.github.f_e_n_y_x.nebula.action.STOP_RUNNING"
        const val ACTION_MUTE = "io.github.f_e_n_y_x.nebula.action.MUTE_RUNNING"
        const val ACTION_DISMISSED = "io.github.f_e_n_y_x.nebula.action.DISMISSED_RUNNING"
        const val EXTRA_HOST = "host"
        const val EXTRA_GAME = "game"
        const val EXTRA_MODE = "mode"
        const val EXTRA_TOKEN = "token"
        private const val EXTRA_HOST_NAME = "host_name"
        private const val EXTRA_GAME_NAME = "game_name"
        private const val EXTRA_SINCE = "since"
        private const val EXTRA_EXACT = "exact"

        fun putSession(i: Intent, np: NowPlaying) {
            i.putExtra(EXTRA_HOST, np.hostId).putExtra(EXTRA_HOST_NAME, np.hostName).putExtra(EXTRA_GAME, np.gameId)
                .putExtra(EXTRA_GAME_NAME, np.gameName).putExtra(EXTRA_SINCE, np.sinceEpochS).putExtra(EXTRA_EXACT, np.exact)
                .putExtra(EXTRA_MODE, np.display?.name)
        }

        /** The session an action intent carries (our own immutable PendingIntent; still checked). */
        fun sessionOf(i: Intent): NowPlaying? {
            val host = i.getStringExtra(EXTRA_HOST)?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
            val game = i.getStringExtra(EXTRA_GAME)?.takeIf { it.isNotBlank() && it.length <= 128 } ?: return null
            val since = i.getLongExtra(EXTRA_SINCE, -1L).takeIf { it >= 0 } ?: return null
            return NowPlaying(
                hostId = host,
                hostName = i.getStringExtra(EXTRA_HOST_NAME)?.take(64) ?: "your PC",
                gameId = game,
                gameName = i.getStringExtra(EXTRA_GAME_NAME)?.take(128) ?: "The game",
                art = GameArt(),
                sinceEpochS = since,
                exact = i.getBooleanExtra(EXTRA_EXACT, false),
                display = i.getStringExtra(EXTRA_MODE)?.let { m -> DisplayMode.entries.firstOrNull { it.name == m } },
            )
        }
    }
}
