package io.github.f_e_n_y_x.nebula.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.f_e_n_y_x.nebula.domain.GamePreset
import io.github.f_e_n_y_x.nebula.domain.GamePresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.gamePresetPrefs by preferencesDataStore(name = "nebula_game_presets")

/**
 * Per-game presets in their own DataStore file: one key per game ("preset_hostId:gameId") holding
 * the preset as JSON. An empty preset removes its key, so a game without one uses the global
 * settings exactly as before.
 */
class GamePresetStore(private val store: DataStore<Preferences>) : GamePresetRepository {
    constructor(context: Context) : this(context.applicationContext.gamePresetPrefs)

    override fun observe(hostId: String, gameId: String): Flow<GamePreset> =
        store.data.map { GamePreset.fromJson(it[key(hostId, gameId)]) }.distinctUntilChanged()

    override fun observeAll(): Flow<Map<String, GamePreset>> =
        store.data.map { p ->
            p.asMap().entries
                .filter { it.key.name.startsWith(PREFIX) }
                .associate { it.key.name.removePrefix(PREFIX) to GamePreset.fromJson(it.value as? String) }
                .filterValues { !it.isEmpty }
        }.distinctUntilChanged()

    override suspend fun set(hostId: String, gameId: String, preset: GamePreset) = update(hostId, gameId) { preset }

    override suspend fun update(hostId: String, gameId: String, transform: (GamePreset) -> GamePreset) {
        store.edit { p ->
            val k = key(hostId, gameId)
            val next = transform(GamePreset.fromJson(p[k]))
            if (next.isEmpty) p.remove(k) else p[k] = next.toJson().toString()
        }
    }

    internal companion object {
        private const val PREFIX = "preset_"
        fun key(hostId: String, gameId: String) = stringPreferencesKey("$PREFIX$hostId:$gameId")
    }
}
