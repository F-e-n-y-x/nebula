package io.github.f_e_n_y_x.nebula.presets

import io.github.f_e_n_y_x.nebula.domain.GamePreset
import io.github.fenyx.nebula.engine.framegen.FramegenKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostProcessingOverlayTest {
    private class Values(val map: MutableMap<String, Any?> = mutableMapOf()) : SettingValues {
        override fun get(key: String) = map[key]
        override fun contains(key: String) = map.containsKey(key)
        override fun put(key: String, value: Any?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private class Journal : OverlayJournal {
        var text: String? = null
        override fun read() = text
        override fun write(value: String?) { text = value }
    }

    @Test fun thePresetAppliesForTheStreamAndSettingsComeBackAfter() {
        val values = Values(mutableMapOf(FramegenKeys.ENABLED to false, FramegenKeys.UPSCALER_STRENGTH to 40))
        val journal = Journal()
        val overlay = PostProcessingOverlay(values, journal)
        val token = overlay.apply("atom", "gta5", PostProcessingOverlay.overridesFor(GamePreset(frameGen = true, upscaler = "fsr1")))
        assertNotNull(token)
        assertEquals(true, values.map[FramegenKeys.ENABLED])
        assertEquals("fsr1", values.map[FramegenKeys.UPSCALER])

        val done = overlay.finish(token)!!
        assertEquals("atom" to "gta5", done.hostId to done.gameId)
        assertTrue(done.changedDuringStream.isEmpty())
        assertEquals(false, values.map[FramegenKeys.ENABLED])
        // The upscaler was never set globally, so it is removed again; unrelated keys are untouched.
        assertFalse(values.contains(FramegenKeys.UPSCALER))
        assertEquals(40, values.map[FramegenKeys.UPSCALER_STRENGTH])
        assertNull(journal.text)
        assertNull(overlay.finish(token))
    }

    @Test fun changesDuringTheStreamAreReportedForTheGame() {
        val values = Values(mutableMapOf(FramegenKeys.ENABLED to false))
        val overlay = PostProcessingOverlay(values, Journal())
        val token = overlay.apply("atom", "gta5", mapOf(FramegenKeys.ENABLED to true))
        values.put(FramegenKeys.ENABLED, false) // turned off from the stream menu
        val done = overlay.finish(token)!!
        assertEquals(mapOf<String, Any?>(FramegenKeys.ENABLED to false), done.changedDuringStream)
        assertEquals(GamePreset(frameGen = false), PostProcessingOverlay.keepChanges(GamePreset(frameGen = true), done.changedDuringStream))
        // A key the preset doesn't set stays out of it.
        assertEquals(GamePreset(fps = 60), PostProcessingOverlay.keepChanges(GamePreset(fps = 60), mapOf(FramegenKeys.UPSCALER to "sharpen")))
    }

    @Test fun anOlderStreamDoesNotUndoANewerOne() {
        val values = Values()
        val overlay = PostProcessingOverlay(values, Journal())
        val first = overlay.apply("atom", "gta5", mapOf(FramegenKeys.ENABLED to true))
        val second = overlay.apply("atom", "wukong", mapOf(FramegenKeys.UPSCALER to "sgsr1"))
        // The first stream's overlay was undone when the second began.
        assertFalse(values.contains(FramegenKeys.ENABLED))
        assertNull(overlay.finish(first))
        assertEquals("sgsr1", values.map[FramegenKeys.UPSCALER])
        assertEquals("wukong", overlay.finish(second)!!.gameId)
    }

    @Test fun aCrashIsUndoneAtTheNextStartAndEmptyPresetsDoNothing() {
        val values = Values(mutableMapOf(FramegenKeys.UPSCALER to "off"))
        val journal = Journal()
        PostProcessingOverlay(values, journal).apply("atom", "gta5", mapOf(FramegenKeys.UPSCALER to "fsr1"))
        // A new process: finish() without a token restores what the old one left.
        val restarted = PostProcessingOverlay(values, journal)
        assertNotNull(restarted.finish())
        assertEquals("off", values.map[FramegenKeys.UPSCALER])
        assertNull(restarted.apply("atom", "gta5", emptyMap()))
        assertNull(journal.text)
    }
}
