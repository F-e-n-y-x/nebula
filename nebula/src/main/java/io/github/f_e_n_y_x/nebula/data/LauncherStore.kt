package io.github.f_e_n_y_x.nebula.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.f_e_n_y_x.nebula.domain.HomeStyle
import io.github.f_e_n_y_x.nebula.domain.LauncherRepository
import io.github.f_e_n_y_x.nebula.domain.RecentGames
import io.github.f_e_n_y_x.nebula.domain.RecentPlay
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.launcherPrefs by preferencesDataStore(name = "launcher")

/** Home style and the local recent-games list (its own DataStore file, separate from stream settings). */
class LauncherStore(context: Context) : LauncherRepository {
    private val store = context.applicationContext.launcherPrefs

    private object Keys {
        val homeStyle = stringPreferencesKey("home_style")
        val recents = stringPreferencesKey("recent_plays")
    }

    override val homeStyle: Flow<HomeStyle> = store.data
        .map { p -> p[Keys.homeStyle]?.let { runCatching { HomeStyle.valueOf(it) }.getOrNull() } ?: HomeStyle.AUTO }
        .distinctUntilChanged()

    override suspend fun setHomeStyle(style: HomeStyle) {
        store.edit { it[Keys.homeStyle] = style.name }
    }

    override val recents: Flow<List<RecentPlay>> = store.data.map { decode(it[Keys.recents]) }.distinctUntilChanged()

    override suspend fun record(play: RecentPlay) {
        store.edit { p -> p[Keys.recents] = encode(RecentGames.add(decode(p[Keys.recents]), play)) }
    }

    private fun encode(list: List<RecentPlay>): String = JSONArray().apply {
        list.forEach { r ->
            put(
                JSONObject()
                    .put("host", r.hostId).put("hostName", r.hostName)
                    .put("game", r.gameId).put("gameName", r.gameName)
                    .put("mode", r.mode.name).put("at", r.playedAtMs)
                    .put("poster", r.poster ?: "").put("hero", r.hero ?: ""),
            )
        }
    }.toString()

    private fun decode(raw: String?): List<RecentPlay> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                runCatching {
                    RecentPlay(
                        hostId = o.getString("host"), hostName = o.optString("hostName"),
                        gameId = o.getString("game"), gameName = o.optString("gameName"),
                        mode = DisplayMode.valueOf(o.optString("mode", DisplayMode.VIRTUAL.name)),
                        playedAtMs = o.getLong("at"),
                        poster = o.optString("poster").ifBlank { null }, hero = o.optString("hero").ifBlank { null },
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}
