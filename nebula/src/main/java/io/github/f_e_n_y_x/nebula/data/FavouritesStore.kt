package io.github.f_e_n_y_x.nebula.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.f_e_n_y_x.nebula.domain.FavouritesRepository
import io.github.f_e_n_y_x.nebula.domain.withFavourite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.favouritesPrefs by preferencesDataStore(name = "nebula_favourites")

/**
 * Favourites in their own DataStore file, one key per host holding its pinned game ids in pin
 * order. A separate file keeps them out of the settings store (and its backup/reset paths).
 */
class FavouritesStore(private val store: DataStore<Preferences>) : FavouritesRepository {
    constructor(context: Context) : this(context.applicationContext.favouritesPrefs)

    override fun observe(hostId: String): Flow<List<String>> =
        store.data.map { decode(it[key(hostId)]) }.distinctUntilChanged()

    override suspend fun setFavourite(hostId: String, gameId: String, favourite: Boolean) = update(hostId) { it.withFavourite(gameId, favourite) }

    override suspend fun toggle(hostId: String, gameId: String) = update(hostId) { it.withFavourite(gameId, gameId !in it) }

    private suspend fun update(hostId: String, change: (List<String>) -> List<String>) {
        store.edit { p ->
            val next = change(decode(p[key(hostId)]))
            if (next.isEmpty()) p.remove(key(hostId)) else p[key(hostId)] = encode(next)
        }
    }

    internal companion object {
        /** Unit separator: never part of a host's app id. */
        private const val SEP = '\u001F'
        fun key(hostId: String) = stringPreferencesKey("favourites_$hostId")
        fun encode(ids: List<String>) = ids.joinToString(SEP.toString())
        fun decode(raw: String?): List<String> = raw?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList()
    }
}
