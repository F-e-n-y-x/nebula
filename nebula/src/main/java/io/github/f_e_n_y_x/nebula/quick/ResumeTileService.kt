package io.github.f_e_n_y_x.nebula.quick

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.f_e_n_y_x.nebula.R
import io.github.f_e_n_y_x.nebula.container
import io.github.f_e_n_y_x.nebula.domain.RecentPlay
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: "Resume <last game> on <host>". Tapping it opens the game's play link, which
 * goes through the same paired-host check as every other link. With nothing to resume it opens Nebula.
 */
class ResumeTileService : TileService() {
    private var scope: CoroutineScope? = null
    private var last: RecentPlay? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        s.launch {
            val c = applicationContext.container
            last = runCatching { c.quick.items.first().firstOrNull() }.getOrNull()
            val asleep = last?.let { r -> runCatching { c.hosts.observeHosts().first().firstOrNull { it.id == r.hostId } }.getOrNull() }
                ?.let { it.status == HostStatus.OFFLINE && it.canWake } == true
            render(last, asleep)
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        // Resume wakes a sleeping PC, then reconnects to what runs there (or the last game played).
        val target = if (last != null) QuickConnect.resumeIntent(this) else QuickConnect.openAppIntent(this)
        if (isLocked) unlockAndRun { open(target) } else open(target)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(r: RecentPlay?, asleep: Boolean) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_nebula_star)
        if (r == null) {
            tile.label = getString(R.string.tile_resume_label)
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) tile.subtitle = "Nothing to resume yet"
            tile.contentDescription = "Nebula: nothing to resume yet. Opens Nebula."
        } else {
            tile.label = r.gameName
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (asleep) "Wake ${r.hostName} and resume" else "Resume on ${r.hostName}"
            tile.contentDescription = (if (asleep) "Wake ${r.hostName} and resume " else "Resume ") + "${r.gameName} on ${r.hostName}"
        }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to re-bind the tile so it shows the newest recent game. */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, ResumeTileService::class.java)) }
        }
    }
}
