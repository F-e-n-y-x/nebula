package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortraitTest {
    private val land = VideoMode(2340, 1080, 120)
    private val port = VideoMode(1080, 2340, 120)

    @Test fun `orientation of a mode`() {
        assertEquals(Orientation.LANDSCAPE, land.orientation)
        assertEquals(Orientation.PORTRAIT, port.orientation)
        assertEquals(Orientation.LANDSCAPE, VideoMode(1440, 1440, 60).orientation)
    }

    @Test fun `rotated swaps the size and keeps the frame rate`() {
        assertEquals(port, Portrait.rotated(land))
        assertEquals(land, Portrait.rotated(port))
        assertEquals(land, Portrait.inOrientation(land, Orientation.LANDSCAPE))
        assertEquals(port, Portrait.inOrientation(land, Orientation.PORTRAIT))
        val square = VideoMode(1440, 1440, 60)
        assertEquals(square, Portrait.inOrientation(square, Orientation.PORTRAIT))
    }

    @Test fun `start mode follows the device only when following rotation`() {
        assertEquals(land, Portrait.startMode(land, PortraitStreaming.OFF, Orientation.PORTRAIT))
        assertEquals(port, Portrait.startMode(land, PortraitStreaming.FOLLOW_ROTATION, Orientation.PORTRAIT))
        assertEquals(land, Portrait.startMode(land, PortraitStreaming.FOLLOW_ROTATION, Orientation.LANDSCAPE))
        assertEquals(land, Portrait.startMode(port, PortraitStreaming.FOLLOW_ROTATION, Orientation.LANDSCAPE))
    }

    @Test fun `orientation follows the start mode when off and the current mode when following`() {
        assertEquals(land, Portrait.orientationMode(PortraitStreaming.OFF, land, port, rotatedFromMenu = false))
        assertEquals(port, Portrait.orientationMode(PortraitStreaming.OFF, land, port, rotatedFromMenu = true))
        assertEquals(port, Portrait.orientationMode(PortraitStreaming.FOLLOW_ROTATION, land, port, rotatedFromMenu = false))
        assertEquals(land, Portrait.orientationMode(PortraitStreaming.FOLLOW_ROTATION, land, null, rotatedFromMenu = false))
    }

    @Test fun `labels`() {
        assertEquals("Rotate to portrait", Portrait.rotateLabel(land))
        assertEquals("Rotate to landscape", Portrait.rotateLabel(port))
        assertEquals("Rotating to portrait (1080×2340@120)…", Portrait.rotatingLabel(port))
    }
}

class RotationFollowerTest {
    private val land = VideoMode(2340, 1080, 120)
    private val port = VideoMode(1080, 2340, 120)

    @Test fun `a turn held for the debounce asks for the rotated mode once`() {
        val f = RotationFollower(600)
        f.observe(Orientation.LANDSCAPE, 0)
        assertNull(f.decide(1_000, land, busy = false))  // already matches
        f.observe(Orientation.PORTRAIT, 2_000)
        assertEquals(2_600L, f.dueAt())
        assertNull(f.decide(2_300, land, busy = false))  // still settling
        assertEquals(port, f.decide(2_600, land, busy = false))
        assertNull(f.decide(3_000, land, busy = false))  // only once per turn, even if it failed
        assertNull(f.dueAt())
    }

    @Test fun `turning back before the debounce cancels the turn`() {
        val f = RotationFollower(600)
        f.observe(Orientation.LANDSCAPE, 0)
        f.decide(700, land, busy = false)
        f.observe(Orientation.PORTRAIT, 1_000)
        f.observe(Orientation.LANDSCAPE, 1_300)
        assertNull(f.decide(1_600, land, busy = false))
        assertNull(f.decide(2_000, land, busy = false))
    }

    @Test fun `flat or face up never counts and keeps the held orientation`() {
        val f = RotationFollower(600)
        f.observe(Orientation.PORTRAIT, 0)
        f.observe(null, 300)  // laid flat mid-turn
        assertEquals(port, f.decide(700, land, busy = false))
        f.observe(null, 1_000)
        assertNull(f.decide(5_000, port, busy = false))
    }

    @Test fun `waits while a switch runs and then decides`() {
        val f = RotationFollower(600)
        f.observe(Orientation.PORTRAIT, 0)
        assertNull(f.decide(700, land, busy = true))
        assertNull(f.decide(800, null, busy = false))  // not live yet
        assertEquals(600L, f.dueAt())
        assertEquals(port, f.decide(900, land, busy = false))
    }

    @Test fun `square modes and matching streams are left alone`() {
        val f = RotationFollower(600)
        f.observe(Orientation.PORTRAIT, 0)
        assertNull(f.decide(700, VideoMode(1440, 1440, 60), busy = false))
        val g = RotationFollower(600)
        g.observe(Orientation.PORTRAIT, 0)
        assertNull(g.decide(700, port, busy = false))
    }

    @Test fun `each new turn asks again and reset forgets`() {
        val f = RotationFollower(600)
        f.observe(Orientation.PORTRAIT, 0)
        assertEquals(port, f.decide(600, land, busy = false))
        f.observe(Orientation.LANDSCAPE, 5_000)
        assertEquals(land, f.decide(5_600, port, busy = false))
        f.observe(Orientation.PORTRAIT, 9_000)
        f.reset()
        assertNull(f.decide(10_000, land, busy = false))
    }
}
