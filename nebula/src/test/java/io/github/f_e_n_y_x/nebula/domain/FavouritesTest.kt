package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesTest {
    private fun game(id: String, played: Long? = null, running: Boolean = false, kind: GameKind = GameKind.GAME) =
        Game(id, "h", id, kind, GameArt(), running = running, lastPlayedEpochS = played)

    private val library = SortLibrary(
        listOf(game("desk", kind = GameKind.DESKTOP), game("old", 10), game("new", 500), game("live", 1, running = true), game("never")),
    )

    @Test fun noPinsKeepsLibraryOrder() {
        val p = PinnedLibrary.of(library, emptyList())
        assertTrue(p.favourites.isEmpty())
        assertEquals(listOf("live", "new", "old", "never", "desk"), p.all.map { it.id })
    }

    @Test fun favouritesComeFirstInPinOrder() {
        val p = PinnedLibrary.of(library, listOf("desk", "old"))
        assertEquals(listOf("desk", "old"), p.favourites.map { it.id })
        assertEquals(listOf("live", "new", "never"), p.rest.map { it.id })
        assertEquals(listOf("desk", "old", "live", "new", "never"), p.all.map { it.id })
        assertEquals(setOf("desk", "old"), p.favouriteIds)
    }

    @Test fun pinsForMissingGamesAndDuplicatesAreSkipped() {
        val p = PinnedLibrary.of(library, listOf("gone", "new", "new"))
        assertEquals(listOf("new"), p.favourites.map { it.id })
        assertEquals(5, p.all.size)
    }

    @Test fun newPinsGoLastAndUnpinRemoves() {
        val pins = emptyList<String>().withFavourite("a", true).withFavourite("b", true).withFavourite("a", true)
        assertEquals(listOf("a", "b"), pins)
        assertEquals(listOf("b"), pins.withFavourite("a", false))
        assertEquals(listOf("b", "a"), pins.withFavourite("a", false).withFavourite("a", true))
    }
}
