package io.github.f_e_n_y_x.nebula.framegen

import io.github.f_e_n_y_x.nebula.domain.model.FramegenState
import io.github.fenyx.nebula.engine.framegen.FramegenOffReason
import io.github.fenyx.nebula.engine.framegen.FramegenStatus
import io.github.fenyx.nebula.engine.framegen.UpscalerMode
import io.github.fenyx.nebula.engine.upscale.UpscalerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PostProcessStatsTest {
    private val active = FramegenStatus(
        state = FramegenStatus.State.ACTIVE, reason = null, presentedFps = 118f, inputFps = 60f, targetFps = 120,
        lsfgMs = 6, blitMs = 1, addedLatencyMs = 15.3f, model = "LSFG 3.1 · flow 100%",
    )

    @Test fun `active frame generation shows presented vs received`() {
        val p = postProcessStats(active, UpscalerStatus())
        assertEquals(FramegenState.ACTIVE, p.framegen)
        assertTrue(p.framegenArmed)
        assertEquals(118f, p.presentedFps)
        assertEquals(15.3f / (1000f / 60f), p.addedLatencyFrames, 1e-4f)
        assertNull(p.note)
        assertEquals("Presented 118 fps from 60 received · +0.9 frame (15.3 ms) · LSFG 3.1 · flow 100%", framegenLine(p, 60f))
    }

    @Test fun `off is not armed and carries no note when simply disabled`() {
        val p = postProcessStats(FramegenStatus(), UpscalerStatus())
        assertFalse(p.framegenArmed)
        assertNull(p.note)
        assertEquals(0f, p.presentedFps)
    }

    @Test fun `auto-off explains itself`() {
        val p = postProcessStats(
            FramegenStatus(state = FramegenStatus.State.AUTO_OFF, reason = FramegenOffReason.THERMAL, autoOffDetail = "thermal headroom 0.15 left"),
            UpscalerStatus(),
        )
        assertEquals(FramegenState.AUTO_OFF, p.framegen)
        assertEquals("thermal headroom 0.15 left", p.note)
        assertEquals(0f, p.presentedFps)
    }

    @Test fun `upscaler line`() {
        val p = postProcessStats(FramegenStatus(), UpscalerStatus(UpscalerMode.SGSR1, UpscalerMode.SGSR1, 1920, 1080, 2560, 1440, 1.2f))
        assertEquals("Upscaler: Snapdragon GSR 1 · 1920×1080 → 2560×1440 · 1.2 ms", upscaleLine(p))
    }

    /** The private DLL must never be committed: the guard files exist and git tracks no DLL. */
    @Test fun `no proprietary DLL is tracked`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "engine").isDirectory && File(it, "nebula").isDirectory }
        assertTrue(File(root, ".gitignore").readText().contains("*.dll"))
        assertTrue(File(root, "tools/check-private-assets.sh").isFile)
        val p = ProcessBuilder("git", "ls-files", "--", "*.dll", "*.DLL").directory(root).redirectErrorStream(true).start()
        val tracked = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() == 0) assertEquals("tracked DLLs", "", tracked)
    }
}
