package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.Game
import kotlinx.coroutines.flow.Flow

/**
 * Games the user pinned, per host. Kept on this device only (the host never sees them), in the
 * order they were pinned: the first favourite stays first.
 */
interface FavouritesRepository {
    /** Pinned game ids for [hostId], oldest pin first. */
    fun observe(hostId: String): Flow<List<String>>
    suspend fun setFavourite(hostId: String, gameId: String, favourite: Boolean)
    suspend fun toggle(hostId: String, gameId: String)
}

/** A library split into the pinned games (pin order) and everything else (library order). */
data class PinnedLibrary(val favourites: List<Game>, val rest: List<Game>) {
    val all: List<Game> get() = favourites + rest
    val favouriteIds: Set<String> get() = favourites.mapTo(HashSet()) { it.id }

    companion object {
        /**
         * [games] is already in library order; [pinned] is the pin order. Pins for games the host
         * no longer lists are skipped (and kept, so the pin returns if the game comes back).
         */
        fun of(games: List<Game>, pinned: List<String>): PinnedLibrary {
            if (pinned.isEmpty()) return PinnedLibrary(emptyList(), games)
            val byId = games.associateBy { it.id }
            val favs = pinned.distinct().mapNotNull { byId[it] }
            val ids = favs.mapTo(HashSet()) { it.id }
            return PinnedLibrary(favs, games.filter { it.id !in ids })
        }
    }
}

/** The stored pin list after pinning or unpinning [gameId]: new pins go to the end. */
internal fun List<String>.withFavourite(gameId: String, favourite: Boolean): List<String> =
    if (favourite) (if (gameId in this) this else this + gameId) else this - gameId
