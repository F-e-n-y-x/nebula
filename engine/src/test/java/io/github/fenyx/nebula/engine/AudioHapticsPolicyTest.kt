package io.github.fenyx.nebula.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioHapticsPolicyTest {
    @Test
    fun `routes match V+`() {
        assertTrue(AudioHapticsPolicy.routesToDevice(AudioHapticsRoute.AUTO, hasRumblePad = false))
        assertFalse(AudioHapticsPolicy.routesToDevice(AudioHapticsRoute.AUTO, hasRumblePad = true))
        assertTrue(AudioHapticsPolicy.routesToGamepad(AudioHapticsRoute.AUTO, hasRumblePad = true))
        assertFalse(AudioHapticsPolicy.routesToDevice(AudioHapticsRoute.GAMEPAD, false))
        assertTrue(AudioHapticsPolicy.routesToDevice(AudioHapticsRoute.BOTH, true))
        assertTrue(AudioHapticsPolicy.routesToGamepad(AudioHapticsRoute.BOTH, true))
        assertFalse(AudioHapticsPolicy.routesToGamepad(AudioHapticsRoute.DEVICE, true))
        assertFalse(AudioHapticsPolicy.routesToGamepad(AudioHapticsRoute.GAMEPAD, hasRumblePad = false))
    }

    @Test
    fun `audio-coupled generator only for music on a fixed device route`() {
        val on = AudioHapticsConfig(enabled = true, scene = AudioHapticsScene.MUSIC, route = AudioHapticsRoute.DEVICE)
        assertTrue(AudioHapticsPolicy.wantsSystemCoupled(on))
        assertFalse(AudioHapticsPolicy.wantsSystemCoupled(on.copy(enabled = false)))
        assertFalse(AudioHapticsPolicy.wantsSystemCoupled(on.copy(route = AudioHapticsRoute.AUTO)))
        assertFalse(AudioHapticsPolicy.wantsSystemCoupled(on.copy(scene = AudioHapticsScene.GAME)))
    }

    @Test
    fun `intensity scales and clamps`() {
        assertEquals(0.5f, AudioHapticsPolicy.scaled(0.5f, 100), 1e-6f)
        assertEquals(1f, AudioHapticsPolicy.scaled(0.8f, 200), 1e-6f)
        assertEquals(0f, AudioHapticsPolicy.scaled(0.8f, 0), 1e-6f)
    }

    @Test
    fun `controller split favours the heavy motor for bass`() {
        val (low, high) = AudioHapticsPolicy.gamepadLevels(1f, 0f, lowBandRatio = 1f, sharpness = 0f, hasTransient = false)
        assertEquals(1f, low, 1e-6f)
        assertEquals(0f, high, 1e-6f)
        val (_, sharpHigh) = AudioHapticsPolicy.gamepadLevels(0f, 1f, lowBandRatio = 0f, sharpness = 1f, hasTransient = true)
        assertEquals(1f, sharpHigh, 1e-6f)
    }

    @Test
    fun `wire values`() {
        assertEquals(AudioHapticsRoute.BOTH, AudioHapticsRoute.fromWire("both"))
        assertEquals(AudioHapticsRoute.AUTO, AudioHapticsRoute.fromWire("nonsense"))
        assertEquals(AudioHapticsScene.MUSIC, AudioHapticsScene.fromWire(1))
        assertEquals(MotionType.GYRO, MotionType.fromWire(2))
        assertEquals(null, MotionType.fromWire(9))
    }
}
