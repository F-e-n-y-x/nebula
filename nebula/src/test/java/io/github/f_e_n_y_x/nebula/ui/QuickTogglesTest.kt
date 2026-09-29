package io.github.f_e_n_y_x.nebula.ui

import io.github.f_e_n_y_x.nebula.controls.OutsideTouch
import io.github.f_e_n_y_x.nebula.domain.MicUi
import io.github.f_e_n_y_x.nebula.framegen.FramegenPanelState.Toggle
import io.github.f_e_n_y_x.nebula.framegen.FramegenPanelState
import io.github.f_e_n_y_x.nebula.framegen.performFramegenToggle
import io.github.f_e_n_y_x.nebula.settings.GyroMode
import io.github.f_e_n_y_x.nebula.ui.screens.KeyboardKind
import io.github.f_e_n_y_x.nebula.ui.screens.QuickTileState
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggle
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggle.CONTROLS
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggle.FRAMEGEN
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggle.GYRO
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggle.HAPTICS
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggle.MIC
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggle.STATS
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggles
import io.github.fenyx.nebula.engine.framegen.UpscalerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuickTogglesTest {
    @Test
    fun `never saved shows the defaults, saved empty shows none`() {
        assertEquals(QuickToggles.DEFAULT, QuickToggles.parse(null))
        assertEquals(QuickToggles.DEFAULT, QuickToggles.read(emptyMap<String, Any>()))
        assertEquals(emptyList<QuickToggle>(), QuickToggles.parse(""))
        assertTrue(QuickToggles.DEFAULT.size <= QuickToggles.MAX)
        assertEquals(QuickToggles.DEFAULT, QuickToggles.DEFAULT.distinct())
    }

    @Test
    fun `parse keeps order, drops unknown ids and duplicates, caps at MAX`() {
        assertEquals(listOf(GYRO, CONTROLS), QuickToggles.parse("gyro, nope,controls,gyro"))
        val all = QuickToggle.entries.joinToString(",") { it.id }
        assertEquals(QuickToggle.entries.take(QuickToggles.MAX), QuickToggles.parse(all))
    }

    @Test
    fun `encode round-trips`() {
        val list = listOf(MIC, STATS, FRAMEGEN)
        assertEquals(list, QuickToggles.parse(QuickToggles.encode(list)))
        QuickToggle.entries.forEach { assertEquals(it, QuickToggle.of(it.id)) }
        assertEquals("ids are unique", QuickToggle.entries.size, QuickToggle.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `show and hide, never past MAX`() {
        val some = listOf(CONTROLS, GYRO)
        assertEquals(listOf(CONTROLS), QuickToggles.toggleShown(some, GYRO))
        assertEquals(listOf(CONTROLS, GYRO, MIC), QuickToggles.toggleShown(some, MIC))
        val full = QuickToggle.entries.take(QuickToggles.MAX)
        val extra = QuickToggle.entries.first { it !in full }
        assertEquals(full, QuickToggles.toggleShown(full, extra))
        assertEquals(full - full[2], QuickToggles.toggleShown(full, full[2]))
    }

    @Test
    fun `move reorders and clamps at the ends`() {
        val l = listOf(CONTROLS, GYRO, MIC)
        assertEquals(listOf(GYRO, CONTROLS, MIC), QuickToggles.move(l, GYRO, -1))
        assertEquals(listOf(CONTROLS, MIC, GYRO), QuickToggles.move(l, GYRO, 1))
        assertEquals(l, QuickToggles.move(l, CONTROLS, -1))
        assertEquals(l, QuickToggles.move(l, MIC, 5))
        assertEquals(l, QuickToggles.move(l, HAPTICS, 1))
    }

    @Test
    fun `editor lists shown ones first then every other toggle once`() {
        val order = QuickToggles.editorOrder(listOf(MIC, CONTROLS))
        assertEquals(listOf(MIC, CONTROLS), order.take(2))
        assertEquals(QuickToggle.entries.toSet(), order.toSet())
        assertEquals(QuickToggle.entries.size, order.size)
    }

    @Test
    fun `touch outside cycles Mouse, Look, Off`() {
        assertEquals(OutsideTouch.LOOK, QuickToggles.nextOutside(OutsideTouch.TRACKPAD))
        assertEquals(OutsideTouch.OFF, QuickToggles.nextOutside(OutsideTouch.LOOK))
        assertEquals(OutsideTouch.TRACKPAD, QuickToggles.nextOutside(OutsideTouch.OFF))
        assertEquals(OutsideTouch.TRACKPAD, QuickToggles.nextOutside(OutsideTouch.TOUCH))
        assertFalse(QuickTileState.outside(null).enabled)
        assertNotNull(QuickTileState.outside(null).reason)
        assertFalse(QuickTileState.outside(OutsideTouch.OFF).on)
        assertEquals("Look", QuickTileState.outside(OutsideTouch.LOOK).value)
    }

    @Test
    fun `gyro turns off and back to the last mode`() {
        assertEquals(GyroMode.OFF, QuickToggles.gyroToggled(GyroMode.MOUSE, null))
        assertEquals(GyroMode.MOUSE, QuickToggles.gyroToggled(GyroMode.OFF, GyroMode.MOUSE))
        assertEquals(GyroMode.RIGHT_STICK, QuickToggles.gyroToggled(GyroMode.OFF, null))
        assertEquals(GyroMode.RIGHT_STICK, QuickToggles.gyroToggled(GyroMode.OFF, GyroMode.OFF))
        assertTrue(QuickTileState.gyro(GyroMode.PASSTHROUGH).on)
        assertFalse(QuickTileState.gyro(GyroMode.OFF).on)
    }

    @Test
    fun `upscaler tap follows the live rules`() {
        // Locked while frame generation owns the screen: nothing written, the reason shown.
        val locked = QuickToggles.upscalerTap(UpscalerMode.FSR1, null, locked = true, running = false)
        assertNull(locked.mode)
        assertNotNull(locked.notice)
        // Running: any mode applies live, no notice.
        assertEquals(QuickToggles.UpscalerTap(UpscalerMode.OFF, null), QuickToggles.upscalerTap(UpscalerMode.SGSR1, null, false, running = true))
        assertEquals(QuickToggles.UpscalerTap(UpscalerMode.SGSR1, null), QuickToggles.upscalerTap(UpscalerMode.OFF, UpscalerMode.SGSR1, false, running = true))
        // Not running: turning on waits for the next stream, and says so.
        val next = QuickToggles.upscalerTap(UpscalerMode.OFF, null, false, running = false)
        assertEquals(UpscalerMode.FSR1, next.mode)
        assertTrue(next.notice!!.contains("next stream"))
        // Turning off never needs a notice.
        assertNull(QuickToggles.upscalerTap(UpscalerMode.FSR1, null, false, running = false).notice)
        assertFalse(QuickTileState.upscaler(UpscalerMode.FSR1, locked = true).enabled)
        assertEquals("FSR 1", QuickTileState.upscaler(UpscalerMode.FSR1, locked = false).value)
    }

    @Test
    fun `mic tile is only live when the mic can be toggled`() {
        listOf(MicUi.HIDDEN, MicUi.CONNECTING, MicUi.UNSUPPORTED).forEach {
            val t = QuickTileState.mic(it)
            assertFalse("$it", t.enabled)
            assertNotNull("$it explains", t.reason)
        }
        assertTrue(QuickTileState.mic(MicUi.LIVE).on)
        assertFalse(QuickTileState.mic(MicUi.MUTED).on)
        assertTrue(QuickTileState.mic(MicUi.NEEDS_PERMISSION).enabled)
        assertTrue(QuickTileState.mic(MicUi.BLOCKED).enabled)
    }

    @Test
    fun `tiles speak their state`() {
        assertEquals("On", QuickTileState.switch(true).spoken)
        assertEquals("Off", QuickTileState.switch(false).spoken)
        assertEquals("On, Look", QuickTileState.outside(OutsideTouch.LOOK).spoken)
        assertEquals("Off, Muted", QuickTileState.mic(MicUi.MUTED).spoken)
        assertTrue(QuickTileState.mic(MicUi.HIDDEN).spoken.startsWith("Unavailable"))
        assertEquals("PC keys", QuickTileState.keyboard(KeyboardKind.PC).spoken)
        assertFalse(QuickTileState.portrait(null).enabled)
        assertTrue(QuickTileState.portrait(true).on)
        QuickToggle.entries.forEach { assertTrue(it.label.isNotBlank() && it.longLabel.isNotBlank()) }
    }

    @Test
    fun `frame generation toggle pauses live and never rebuilds when armed`() {
        val pauses = mutableListOf<Pair<Boolean, Boolean>>()
        val enabled = mutableListOf<Boolean>()
        val pause = { p: Boolean, f: Boolean -> pauses += p to f; true }
        assertNull(performFramegenToggle(Toggle.PAUSE, pause, { enabled += it }, false))
        assertNull(performFramegenToggle(Toggle.RESUME, pause, { enabled += it }, false))
        assertNull(performFramegenToggle(Toggle.FORCE_RESUME, pause, { enabled += it }, false))
        assertEquals(listOf(true to false, false to false, false to true), pauses)
        // Armed toggles only pause / resume: the saved setting (which would re-plan) is untouched.
        assertTrue(enabled.isEmpty())
        assertNotNull(performFramegenToggle(Toggle.PAUSE, { _, _ -> false }, { enabled += it }, false))
    }

    @Test
    fun `frame generation toggle off-stream changes the setting or explains`() {
        val enabled = mutableListOf<Boolean>()
        val noPause = { _: Boolean, _: Boolean -> error("must not pause") }
        assertTrue(performFramegenToggle(Toggle.ENABLE, noPause, { enabled += it }, false)!!.contains("next stream"))
        assertNull(performFramegenToggle(Toggle.DISABLE, noPause, { enabled += it }, false))
        assertEquals(listOf(true, false), enabled)
        assertNotNull(performFramegenToggle(Toggle.REFUSE_NO_ENGINE, noPause, { enabled += it }, false))
        assertNotNull(performFramegenToggle(Toggle.REFUSE_UNSUPPORTED, noPause, { enabled += it }, false))
        assertEquals(FramegenPanelState.QUEUED_MESSAGE, performFramegenToggle(Toggle.REFUSE_NEEDS_CHECK, noPause, { enabled += it }, true))
        assertEquals(FramegenPanelState.NEEDS_CHECK_MESSAGE, performFramegenToggle(Toggle.REFUSE_NEEDS_CHECK, noPause, { enabled += it }, false))
        assertEquals(listOf(true, false), enabled)
    }

    /** Unit tests run with the :nebula module as working directory. */
    private val src = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "nebula/src").isDirectory }
        .let { File(it, "nebula/src/main/java/io/github/f_e_n_y_x/nebula") }

    @Test
    fun `the strip is at the top of the menu, above the detailed sections`() {
        val menu = File(src, "ui/screens/StreamMenu.kt").readText()
        val strip = menu.indexOf("QuickToggleStrip(")
        assertTrue(strip > 0)
        listOf("StatsBlock(stats", "FramegenMenuSection(", "Controls(ui", "FeedbackSection(").forEach {
            assertTrue("$it should come after the quick toggles", menu.indexOf(it) > strip)
        }
    }

    @Test
    fun `quick toggles write the keys the detailed controls read`() {
        val strip = File(src, "ui/screens/QuickToggleStrip.kt").readText()
        val menu = File(src, "ui/screens/StreamMenu.kt").readText()
        // Same key as ControlsMenuSection's switch (via StreamMenu) and StreamUiPrefs.osc.
        assertTrue(strip.contains("\"checkbox_show_onscreen_controls\"") && menu.contains("\"checkbox_show_onscreen_controls\""))
        listOf("OutsideTouch.write(prefs, controlsProfileId", "StatsOverlaySettings.setEnabled(prefs", "MotionSettings.write(prefs",
            "HapticsSettings.write(prefs", "FramegenKeys.UPSCALER", "performFramegenToggle(", "StreamUiPrefs.MOUSE_BAR_KEY").forEach {
            assertTrue("strip doesn't use $it", strip.contains(it))
        }
        // The detailed "touch outside" control re-reads on every change instead of keeping its own copy.
        assertFalse(menu.contains("var outside by remember(controlsProfileId)"))
    }

    @Test
    fun `the layout tile steps a layout set and is off by default`() {
        assertFalse(QuickToggle.LAYOUT in QuickToggles.DEFAULT)
        val none = QuickTileState.layout(null)
        assertFalse(none.enabled)
        assertNotNull(none.reason)
        val on = QuickTileState.layout("Vehicle")
        assertTrue(on.enabled)
        assertEquals("Vehicle", on.value)
        assertEquals(QuickTileState.Kind.ACTION, on.kind)
        assertEquals("Vehicle", on.spoken)
        // It goes where a "next" switch element goes: the stream screen's set, the session's layout.
        val screen = File(src, "ui/screens/StreamScreen.kt").readText()
        assertTrue(screen.contains("onNextLayout = {"))
        assertTrue(screen.contains("target(controlsProfile.id, io.github.f_e_n_y_x.nebula.controls.SwitchTarget.Next)"))
    }

    @Test
    fun `adaptive bitrate is a choosable toggle, off by default, wired to the stream`() {
        assertEquals(QuickToggle.ABR, QuickToggle.of("abr"))
        assertEquals("Adaptive bitrate", QuickToggle.ABR.longLabel)
        assertFalse(QuickToggle.ABR in QuickToggles.DEFAULT)
        assertTrue(QuickToggle.ABR in QuickToggles.editorOrder(QuickToggles.DEFAULT))
        assertTrue(File(src, "ui/screens/StreamMenu.kt").readText().contains("abrOn = stats?.abr != null, onAbr = actions.onAbr"))
        assertTrue(File(src, "ui/screens/StreamScreen.kt").readText().contains("onAbr = vm::setAdaptiveBitrate"))
    }
}
