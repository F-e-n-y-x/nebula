package io.github.fenyx.nebula.engine.framegen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramegenPlannerTest {
    private val on = FramegenConfig(enabled = true, dllPath = "/dll")

    private fun plan(c: FramegenConfig = on, w: Int = 2560, h: Int = 1440, fps: Int = 60, native: Boolean = true, v: SelfTestVerdict = SelfTestVerdict.PASSED) =
        FramegenPlanner.plan(c, w, h, fps, native, v)

    private fun off(p: FramegenPlan) = (p as FramegenPlan.Off).reason

    @Test fun `60 to 120 at 1440p`() {
        val rt = (plan() as FramegenPlan.On).runtime
        assertEquals(60, rt.inputFps)
        assertEquals(120, rt.presentationFps)
        assertEquals(640, rt.internalWidth)
        assertEquals(false, rt.adaptiveOnly)
        assertEquals(120, FramegenPlanner.presentationFps(plan(), 60))
    }

    @Test fun `off reasons in order`() {
        assertEquals(FramegenOffReason.DISABLED, off(plan(FramegenConfig())))
        assertEquals(FramegenOffReason.UNSUPPORTED, off(plan(native = false)))
        assertEquals(FramegenOffReason.NO_ENGINE, off(plan(on.copy(dllPath = null))))
        assertEquals(FramegenOffReason.NOT_TESTED, off(plan(v = SelfTestVerdict.NOT_RUN)))
        assertEquals(FramegenOffReason.UNSUPPORTED, off(plan(v = SelfTestVerdict.UNSUPPORTED)))
        assertEquals(FramegenOffReason.SELF_TEST_FAILED, off(plan(v = SelfTestVerdict.FAILED)))
        assertEquals(FramegenOffReason.RESOLUTION_TOO_LARGE, off(plan(w = 3840, h = 2160)))
        assertEquals(FramegenOffReason.FPS_TOO_HIGH, off(plan(fps = 120)))
    }

    @Test fun `slow devices may still run`() {
        assertTrue(plan(v = SelfTestVerdict.SLOW) is FramegenPlan.On)
    }

    @Test fun `weak-network fill runs above 60 fps without doubling`() {
        val rt = (plan(FramegenConfig(adaptive = true, dllPath = "/dll"), fps = 120) as FramegenPlan.On).runtime
        assertEquals(120, rt.presentationFps)
        assertTrue(rt.adaptiveOnly)
        val both = (plan(on.copy(adaptive = true), fps = 90) as FramegenPlan.On).runtime
        assertEquals("doubling needs <= 60 fps", 90, both.presentationFps)
    }

    @Test fun `model settings reach the runtime`() {
        val rt = (plan(on.copy(flowScalePercent = 50, performanceMode = true, presentRealFirst = true)) as FramegenPlan.On).runtime
        assertEquals(0.5f, rt.flowScale)
        assertTrue(rt.performanceMode)
        assertEquals(1, rt.presentMode)
    }

    @Test fun `display rate stays at the stream rate when off`() {
        assertEquals(60, FramegenPlanner.presentationFps(plan(FramegenConfig()), 60))
    }
}
