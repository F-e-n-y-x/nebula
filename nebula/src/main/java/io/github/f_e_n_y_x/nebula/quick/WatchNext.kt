package io.github.f_e_n_y_x.nebula.quick

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import io.github.f_e_n_y_x.nebula.R
import io.github.f_e_n_y_x.nebula.domain.RecentPlay
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Serves poster files to the TV launcher. Not exported: the launcher gets per-URI read grants. */
class PosterFileProvider : FileProvider(R.xml.poster_paths)

/**
 * Android TV "Watch Next" (Continue playing) entries for the recent games. Each entry opens the
 * game's play link. Rows Nebula no longer lists are removed; rows the user removed from the
 * launcher stay removed until the game is played again.
 */
object WatchNext {
    private const val MAX = 4

    suspend fun sync(context: Context, recents: List<RecentPlay>) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val resolver = app.contentResolver
        val existing = mutableMapOf<String, Pair<Long, Boolean>>() // internal id -> (row id, still browsable)
        val engagement = mutableMapOf<String, Long>()
        resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = WatchNextProgram.fromCursor(c)
                val key = p.internalProviderId ?: continue
                existing[key] = p.id to p.isBrowsable
                engagement[key] = p.lastEngagementTimeUtcMillis
            }
        }
        val wanted = recents.take(MAX)
        val launchers = launcherPackages(app)
        for (r in wanted) {
            val prior = existing[r.key]
            // The user removed it from the launcher and hasn't played it since: respect that.
            if (prior != null && !prior.second && (engagement[r.key] ?: 0L) >= r.playedAtMs) continue
            val poster = posterUri(app, r, launchers)
            val program = WatchNextProgram.Builder()
                .setType(TvContractCompat.PreviewProgramColumns.TYPE_GAME)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(r.playedAtMs)
                .setTitle(r.gameName)
                .setDescription("On ${r.hostName} · ${if (r.mode == DisplayMode.MIRROR) "Desktop (Mirror)" else "Virtual display"}")
                .setIntent(QuickConnect.launchIntent(app, r).apply { removeFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) })
                .setInternalProviderId(r.key)
                .apply {
                    if (poster != null) {
                        setPosterArtUri(poster)
                        setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
                    }
                }
                .build()
            if (prior != null) {
                resolver.update(TvContractCompat.buildWatchNextProgramUri(prior.first), program.toContentValues(), null, null)
            } else {
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
            }
        }
        val keep = wanted.map { it.key }.toSet()
        existing.filterKeys { it !in keep }.values.forEach { (id, _) ->
            resolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null)
        }
    }

    /** Writes the poster where [PosterFileProvider] serves it and grants the launchers read access. */
    private suspend fun posterUri(context: Context, r: RecentPlay, launchers: List<String>): Uri? {
        val bitmap = Posters.load(context, r.poster, 320, 480) ?: return null
        val dir = File(context.filesDir, "quick/posters").apply { mkdirs() }
        val file = File(dir, sha(r.key) + ".png")
        runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }.getOrElse { return null }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.posters", file)
        launchers.forEach { runCatching { context.grantUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        return uri
    }

    /** The home apps on this device (the TV launcher shows Watch Next). */
    @SuppressLint("QueryPermissionsNeeded")
    private fun launcherPackages(context: Context): List<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.distinct()
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}
