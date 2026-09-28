package io.github.f_e_n_y_x.nebula.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Swipe-to-stick and mouse look (osc-touch-research §3). */
class CameraStickTest {
    /** Drives [cam] with a finger moving at [dpPerS] for [ms], sampled at [hz], ticked every 16 ms; returns the push per tick. */
    private fun run(cam: CameraStick, dpPerS: Float, ms: Long, hz: Int, thenStillMs: Long = 0L, dirY: Float = 0f): List<Pair<Float, Float>> {
        val out = mutableListOf<Pair<Float, Float>>()
        val sampleMs = 1000.0 / hz
        var nextSample = sampleMs
        var tick = 0L
        var lastSampleT = 0.0
        while (tick < ms + thenStillMs) {
            tick += CameraStick.TICK_MS
            while (nextSample <= tick) {
                if (nextSample <= ms) {
                    val dt = nextSample - lastSampleT
                    val d = (dpPerS * dt / 1000.0).toFloat()
                    cam.add(if (dirY == 0f) d else 0f, if (dirY != 0f) d * dirY else 0f, nextSample.toLong())
                    lastSampleT = nextSample
                }
                nextSample += sampleMs
            }
            out += cam.tick(tick)
        }
        return out
    }

    @Test
    fun `a slow drag still clears the game's stick deadzone`() {
        val p = run(CameraStick(), 60f, 300, 60)
        val settled = p.drop(5).map { it.first }
        assertTrue("60 dp/s gave $settled", settled.all { it >= ControlElement.DEFAULT_ANTI_DEADZONE - 0.01f })
        assertTrue(settled.all { it < 0.4f })
    }

    @Test
    fun `the same swipe gives the same push at 60, 120 and 240 Hz`() {
        val a = run(CameraStick(), 350f, 400, 60).drop(6).map { it.first }.average()
        val b = run(CameraStick(), 350f, 400, 120).drop(6).map { it.first }.average()
        val c = run(CameraStick(), 350f, 400, 240).drop(6).map { it.first }.average()
        assertTrue("60/120/240 Hz: $a $b $c", abs(a - b) / b < 0.05 && abs(c - b) / b < 0.05)
        // 350 dp/s is half of full speed: anti + (1 - anti) × 0.5.
        assertEquals(0.22 + 0.78 * 0.5, b, 0.03)
    }

    @Test
    fun `a still finger with repeated zero-delta samples is back at zero within 120 ms`() {
        val cam = CameraStick()
        run(cam, 700f, 200, 120)
        assertTrue(cam.x > 0.9f)
        var t = 200L
        var zeroAt = -1L
        while (t < 400L) {
            t += CameraStick.TICK_MS
            cam.add(0f, 0f, t) // the panel repeats the same position
            if (cam.tick(t).first == 0f && zeroAt < 0) zeroAt = t - 200L
        }
        assertTrue("zero after $zeroAt ms", zeroAt in 1..120)
    }

    @Test
    fun `lifting is zero at once, and invert and curve behave`() {
        val cam = CameraStick()
        run(cam, 700f, 100, 60)
        cam.release()
        assertEquals(0f, cam.x); assertEquals(0f, cam.y)
        // Dragging up is stick-up; invertY flips it.
        assertTrue(run(CameraStick(), 400f, 200, 60, dirY = -1f).last().second > 0.3f)
        assertTrue(run(CameraStick(invertY = true), 400f, 200, 60, dirY = -1f).last().second < -0.3f)
        // A steeper curve makes the same half-speed swipe finer.
        val lin = run(CameraStick(antiDeadzone = 0f), 350f, 300, 120).drop(6).map { it.first }.average().toFloat()
        val fine = run(CameraStick(curve = 2f, antiDeadzone = 0f), 350f, 300, 120).drop(6).map { it.first }.average().toFloat()
        assertEquals(0.5f, lin, 0.03f)
        assertEquals(0.25f, fine, 0.03f)
        // Jitter under 20 dp/s is ignored.
        assertEquals(0f, run(CameraStick(), 10f, 300, 120).last().first)
    }

    @Test
    fun `mouse look is per dp, carries sub-pixels and caps its acceleration`() {
        val m = CameraMouse(sensitivity = 2f, acceleration = 0f)
        var x = 0
        // 0.25 dp per sample at density 3 (0.75 px): 0.5 mickey each, none lost.
        repeat(8) { x += m.move(0.75f, 0f, 8, 3f).first }
        assertEquals(4, x)
        // Same finger distance on a denser phone: same mickeys.
        assertEquals(CameraMouse(acceleration = 0f).move(30f, 0f, 16, 3f).first, CameraMouse(acceleration = 0f).move(40f, 0f, 16, 4f).first)
        // A very fast flick gets at most 1 + 2 = 3× the gain.
        val flick = CameraMouse(sensitivity = 1f, acceleration = 5f).move(100f, 0f, 4, 1f).first
        assertTrue("flick $flick", flick in 290..300)
    }
}
