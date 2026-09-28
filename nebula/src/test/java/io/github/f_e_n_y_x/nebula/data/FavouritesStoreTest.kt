package io.github.f_e_n_y_x.nebula.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FavouritesStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private suspend fun <T> withStore(file: File, block: suspend (FavouritesStore) -> T): T {
        val job = SupervisorJob()
        val store = FavouritesStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
        return try { block(store) } finally { job.cancelAndJoin() }
    }

    @Test fun pinsPersistPerHostInPinOrder() = runTest {
        val file = File(tmp.root, "favs.preferences_pb")
        withStore(file) { s ->
            s.setFavourite("pc1", "halo", true)
            s.toggle("pc1", "doom")
            s.toggle("pc2", "halo")
            s.setFavourite("pc1", "halo", true) // already pinned: stays first
        }
        // A fresh store on the same file: what survives an app restart.
        withStore(file) { s ->
            assertEquals(listOf("halo", "doom"), s.observe("pc1").first())
            assertEquals(listOf("halo"), s.observe("pc2").first())
            assertEquals(emptyList<String>(), s.observe("pc3").first())
            s.toggle("pc1", "halo")
            assertEquals(listOf("doom"), s.observe("pc1").first())
            s.setFavourite("pc2", "halo", false)
            assertEquals(emptyList<String>(), s.observe("pc2").first())
        }
    }

    @Test fun encodingRoundTripsOddIds() {
        val ids = listOf("steam:570", "a b/c", "ümlaut")
        assertEquals(ids, FavouritesStore.decode(FavouritesStore.encode(ids)))
        assertEquals(emptyList<String>(), FavouritesStore.decode(null))
        assertEquals(emptyList<String>(), FavouritesStore.decode(""))
    }
}
