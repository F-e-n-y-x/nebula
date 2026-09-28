package io.github.f_e_n_y_x.nebula.framegen

import io.github.f_e_n_y_x.nebula.domain.model.FramegenState
import io.github.f_e_n_y_x.nebula.domain.model.PostProcessStats
import io.github.f_e_n_y_x.nebula.framegen.FramegenPanelState.Status
import io.github.f_e_n_y_x.nebula.framegen.FramegenPanelState.Toggle
import io.github.f_e_n_y_x.nebula.framegen.FramegenPanelState.Tone
import io.github.fenyx.nebula.engine.framegen.FramegenConfig
import io.github.fenyx.nebula.engine.framegen.SelfTestCheck
import io.github.fenyx.nebula.engine.framegen.SelfTestVerdict
import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.framegen.UpscalerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramegenPanelStateTest {
    private val on = FramegenConfig(enabled = true, dllPath = "/data/Lossless.dll")
    private val active = PostProcessStats(
        framegen = FramegenState.ACTIVE, presentedFps = 118f, inputFps = 60f, targetFps = 120, multiplier = 2, lsfgMs = 6,
        addedLatencyMs = 15.3f, addedLatencyFrames = 0.92f, model = "LSFG 3.1 · flow 100%",
    )

    private fun state(
        post: PostProcessStats? = active,
        received: Float? = 59.8f,
        config: FramegenConfig = on,
        up: UpscalerConfig = UpscalerConfig(),
        verdict: SelfTestVerdict = SelfTestVerdict.PASSED,
        failure: SelfTestCheck? = null,
        queued: Boolean = false,
    ) = framegenPanelState(post, received, config, up, verdict, failure, queued)

    @Test fun `running shows presented vs received and the live numbers`() {
        val s = state()
        assertEquals(Status.RUNNING, s.status)
        assertEquals("Running 2×", s.label)
        assertEquals(Tone.SUCCESS, s.tone)
        assertTrue(s.switchOn)
        assertEquals("Presented 118 fps from 60 received · LSFG 3.1 · flow 100%", s.detail)
        assertNotNull(s.stats)
        val st = s.stats!!
        assertEquals(118f, st.presentedFps)
        assertEquals(59.8f, st.receivedFps)
        assertEquals(15.3f, st.addedLatencyMs)
        assertEquals(6, st.lsfgMs)
    }

    @Test fun `received falls back to LSFG input fps`() {
        assertEquals(60f, state(received = null).stats!!.receivedFps)
    }

    @Test fun `paused and thermal auto-off keep the pipeline and switch off`() {
        val paused = state(post = active.copy(framegen = FramegenState.PAUSED, presentedFps = 0f))
        assertEquals(Status.PAUSED, paused.status)
        assertFalse(paused.switchOn)
        assertNull(paused.stats)
        assertEquals(Toggle.RESUME, paused.toggle(true))

        val hot = state(post = active.copy(framegen = FramegenState.AUTO_OFF, note = "thermal headroom 0.15 left"))
        assertEquals(Status.THERMAL_OFF, hot.status)
        assertEquals(Tone.WARNING, hot.tone)
        assertEquals("Thermal headroom 0.15 left. The stream carries on; turn on to try again.", hot.detail)
        assertEquals(Toggle.FORCE_RESUME, hot.toggle(true))
        assertEquals(Toggle.PAUSE, state().toggle(false))
    }

    @Test fun `unsupported shows the device-check reason and refuses`() {
        val fail = SelfTestCheck("robustness2", "Null descriptors (VK_EXT_robustness2 nullDescriptor)", false, "missing VK_EXT_robustness2 (robustness2=0 null_descriptor=0)")
        val s = state(post = null, verdict = SelfTestVerdict.UNSUPPORTED, failure = fail)
        assertEquals(Status.UNSUPPORTED, s.status)
        assertEquals(Tone.DANGER, s.tone)
        assertFalse(s.switchOn)
        assertTrue(s.detail, s.detail.contains("VK_EXT_robustness2"))
        assertEquals(Toggle.REFUSE_UNSUPPORTED, s.toggle(true))
        assertFalse(s.offerCheckAfterStream)
    }

    @Test fun `never checked mid-stream refuses politely and offer the check after the stream`() {
        val s = state(post = PostProcessStats(framegen = FramegenState.OFF), config = on.copy(enabled = false), verdict = SelfTestVerdict.NOT_RUN)
        assertEquals(Status.NEEDS_CHECK, s.status)
        assertEquals(FramegenPanelState.NEEDS_CHECK_MESSAGE, s.detail)
        assertEquals(Toggle.REFUSE_NEEDS_CHECK, s.toggle(true))
        assertTrue(s.offerCheckAfterStream)
        val queued = state(post = null, verdict = SelfTestVerdict.NOT_RUN, queued = true)
        assertEquals(FramegenPanelState.QUEUED_MESSAGE, queued.detail)
        assertFalse(queued.offerCheckAfterStream)
        assertFalse(queued.switchOn)
    }

    @Test fun `a failed check also refuses and shows why`() {
        val fail = SelfTestCheck("benchmark", "Generate frames", false, "vkCreateDevice failed")
        val s = state(post = null, verdict = SelfTestVerdict.FAILED, failure = fail)
        assertEquals(Status.CHECK_FAILED, s.status)
        assertTrue(s.detail, s.detail.contains("Generate frames (vkCreateDevice failed)"))
        assertEquals(Toggle.REFUSE_NEEDS_CHECK, s.toggle(true))
    }

    @Test fun `no engine, off and not running`() {
        assertEquals(Status.NO_ENGINE, state(post = null, config = FramegenConfig(enabled = true)).status)
        val off = state(post = null, config = on.copy(enabled = false))
        assertEquals(Status.OFF, off.status)
        assertEquals(Toggle.ENABLE, off.toggle(true))
        val fps = state(post = PostProcessStats(framegen = FramegenState.OFF, note = "Frame generation needs a stream of 60 fps or less (it doubles the rate)."))
        assertEquals(Status.NOT_RUNNING, fps.status)
        assertTrue(fps.switchOn)
        assertEquals(Toggle.DISABLE, fps.toggle(false))
        assertTrue(fps.detail.startsWith("Frame generation needs a stream of 60 fps"))
    }

    @Test fun `upscaler is locked while frame generation is set up, paused included`() {
        val up = UpscalerConfig(UpscalerMode.SGSR1, 50)
        val running = state(up = up)
        assertTrue(running.upscalerLocked)
        assertTrue(running.upscalerNote, running.upscalerNote.contains("Snapdragon GSR 1"))
        assertTrue(state(post = active.copy(framegen = FramegenState.PAUSED), up = up).upscalerLocked)
        val free = state(post = PostProcessStats(framegen = FramegenState.OFF, upscaler = "sgsr1", upscalerLabel = "Snapdragon GSR 1", upscaleMs = 1.1f), config = on.copy(enabled = false), up = up)
        assertFalse(free.upscalerLocked)
        assertEquals("Snapdragon GSR 1 · 1.1 ms", free.upscalerNote)
    }
}
