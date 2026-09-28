package io.github.fenyx.nebula.engine.framegen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalGuardTest {
    private fun sample(headroom: Float? = 0.3f, status: Int = 0, fps: Float = 119f, lsfg: Int = 6) =
        GuardSample(headroom, status, fps, targetFps = 120, lsfgMs = lsfg, budgetMs = 16.7f)

    private fun ThermalGuard.feed(n: Int, s: GuardSample): GuardDecision { var d: GuardDecision = GuardDecision.Keep; repeat(n) { d = onSample(s) }; return d }

    @Test fun `a cool, fast session keeps going`() {
        assertEquals(GuardDecision.Keep, ThermalGuard().feed(600, sample()))
    }

    @Test fun `severe thermal status trips at once`() {
        val d = ThermalGuard().onSample(sample(status = 3))
        assertTrue(d is GuardDecision.TurnOff && d.cause == GuardDecision.Cause.THERMAL)
        assertTrue((d as GuardDecision.TurnOff).detail.contains("severe"))
    }

    @Test fun `headroom below 0_2 must persist for three windows`() {
        val g = ThermalGuard()
        assertEquals(GuardDecision.Keep, g.feed(2, sample(headroom = 0.85f)))
        assertEquals(GuardDecision.Keep, g.onSample(sample(headroom = 0.5f)))
        assertEquals("a cool window resets the count", GuardDecision.Keep, g.feed(2, sample(headroom = 0.85f)))
        val d = g.onSample(sample(headroom = 0.9f))
        assertTrue(d is GuardDecision.TurnOff && d.cause == GuardDecision.Cause.THERMAL)
        assertTrue((d as GuardDecision.TurnOff).detail, d.detail.contains("0.10"))
    }

    @Test fun `unknown headroom never trips on its own`() {
        assertEquals(GuardDecision.Keep, ThermalGuard().feed(50, sample(headroom = Float.NaN)))
        assertEquals(GuardDecision.Keep, ThermalGuard().feed(50, sample(headroom = null, status = -1)))
    }

    @Test fun `slow frames trip after warm-up and eight windows`() {
        val g = ThermalGuard()
        assertEquals("warm-up is ignored", GuardDecision.Keep, g.feed(5, sample(fps = 60f)))
        assertEquals(GuardDecision.Keep, g.feed(7, sample(fps = 90f)))
        val d = g.onSample(sample(fps = 90f))
        assertTrue(d is GuardDecision.TurnOff && d.cause == GuardDecision.Cause.SLOW)
    }

    @Test fun `LSFG over budget counts as slow`() {
        val g = ThermalGuard()
        g.feed(5, sample())
        val d = g.feed(8, sample(fps = 119f, lsfg = 22))
        assertTrue(d is GuardDecision.TurnOff && (d as GuardDecision.TurnOff).detail.contains("22 ms"))
    }

    @Test fun `stays tripped until reset; disabled guard never trips`() {
        val g = ThermalGuard()
        val d = g.onSample(sample(status = 4))
        assertEquals(d, g.onSample(sample()))
        g.reset()
        assertEquals(GuardDecision.Keep, g.onSample(sample()))
        assertEquals(GuardDecision.Keep, ThermalGuard(enabled = false).feed(20, sample(status = 5, fps = 10f)))
    }

    @Test fun `soak recorder scores the 115 fps target`() {
        val r = SoakRecorder.forTarget(120)
        repeat(590) { r.onWindow(118f, 1000) }
        repeat(10) { r.onWindow(110f, 1000) }
        r.onWindow(0f, 1000) // no data window is ignored
        assertEquals(600, r.windows)
        assertEquals(590f / 600f, r.shareAtTarget, 1e-4f)
        assertEquals(110f, r.minFps)
        assertEquals("10:00 · 98% ≥115 fps · min 110", r.summary())
    }
}
