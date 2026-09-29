package io.github.f_e_n_y_x.nebula.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.f_e_n_y_x.nebula.domain.GamePreset
import io.github.f_e_n_y_x.nebula.domain.PresetResolution
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
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

class GamePresetStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private suspend fun <T> withStore(file: File, block: suspend (GamePresetStore) -> T): T {
        val job = SupervisorJob()
        val store = GamePresetStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
        return try { block(store) } finally { job.cancelAndJoin() }
    }

    @Test fun presetsPersistPerGameAndEmptyOnesAreRemoved() = runTest {
        val file = File(tmp.root, "presets.preferences_pb")
        val gta = GamePreset(resolution = PresetResolution.Screen(75), fps = 120, codec = VideoCodec.HEVC, frameGen = true)
        withStore(file) { s ->
            s.set("atom", "gta5", gta)
            s.update("atom", "wukong") { it.copy(bitrateKbps = 60_000) }
            s.update("deck", "gta5") { it.copy(upscaler = "sgsr1") }
        }
        withStore(file) { s ->
            assertEquals(gta, s.observe("atom", "gta5").first())
            assertEquals(GamePreset(bitrateKbps = 60_000), s.observe("atom", "wukong").first())
            assertEquals(GamePreset.NONE, s.observe("atom", "farcry5").first())
            assertEquals(setOf("atom:gta5", "atom:wukong", "deck:gta5"), s.observeAll().first().keys)
            // Clearing every field removes the game's entry.
            s.update("atom", "wukong") { it.copy(bitrateKbps = null) }
            assertEquals(setOf("atom:gta5", "deck:gta5"), s.observeAll().first().keys)
            s.set("atom", "gta5", GamePreset.NONE)
            assertEquals(GamePreset.NONE, s.observe("atom", "gta5").first())
        }
    }
}
