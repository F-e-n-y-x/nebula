package io.github.fenyx.nebula.engine.upscale

import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.framegen.UpscalerMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpscalerPlanTest {
    @Test fun `passes per mode when enlarging 1080p to 1440p`() {
        assertEquals(emptyList<UpscalePass>(), UpscalerPlan.passes(UpscalerMode.OFF, 1920, 1080, 2560, 1440))
        assertEquals(listOf(UpscalePass.BILINEAR, UpscalePass.RCAS), UpscalerPlan.passes(UpscalerMode.SHARPEN, 1920, 1080, 2560, 1440))
        assertEquals(listOf(UpscalePass.COPY, UpscalePass.EASU, UpscalePass.RCAS), UpscalerPlan.passes(UpscalerMode.FSR1, 1920, 1080, 2560, 1440))
        assertEquals(listOf(UpscalePass.COPY, UpscalePass.SGSR), UpscalerPlan.passes(UpscalerMode.SGSR1, 1920, 1080, 2560, 1440))
    }

    @Test fun `upscalers fall back to sharpening when nothing is enlarged`() {
        assertEquals(listOf(UpscalePass.BILINEAR, UpscalePass.RCAS), UpscalerPlan.passes(UpscalerMode.FSR1, 2560, 1440, 2560, 1440))
        assertEquals(UpscalerMode.SHARPEN, UpscalerPlan.effectiveMode(UpscalerMode.SGSR1, 2560, 1440, 1920, 1080))
        assertEquals(UpscalerMode.SGSR1, UpscalerPlan.effectiveMode(UpscalerMode.SGSR1, 1920, 1080, 2560, 1440))
        assertEquals(UpscalerMode.FSR1, UpscalerPlan.effectiveMode(UpscalerMode.FSR1, 1920, 1080, 2560, 1440))
    }

    @Test fun `off for HDR and while frame generation runs`() {
        val on = UpscalerConfig(UpscalerMode.SGSR1)
        assertNull(UpscalerPlan.offReason(on, hdr = false, framegenArmed = false))
        assertEquals(UpscalerPlan.OFF_HDR, UpscalerPlan.offReason(on, hdr = true, framegenArmed = false))
        assertEquals(UpscalerPlan.OFF_FRAMEGEN, UpscalerPlan.offReason(on, hdr = false, framegenArmed = true))
    }

    @Test fun `strength maps to RCAS stops and SGSR sharpness`() {
        assertEquals(1f, UpscalerPlan.rcasSharpness(1f), 1e-6f)
        assertEquals(0.7071f, UpscalerPlan.rcasSharpness(0.5f), 1e-3f)
        assertEquals(0.25f, UpscalerPlan.rcasSharpness(0f), 1e-6f)
        assertEquals(2f, UpscalerPlan.sgsrEdgeSharpness(0.5f), 1e-6f)
        assertEquals(1f, UpscalerPlan.sgsrEdgeSharpness(-1f), 1e-6f)
    }

    @Test fun `EASU constants match AMD's FsrEasuCon`() {
        val c = UpscalerPlan.easuConstants(1920, 1080, 2560, 1440)
        assertArrayEquals(floatArrayOf(0.75f, 0.75f, 0.5f * 0.75f - 0.5f, 0.5f * 0.75f - 0.5f), c[0], 1e-6f)
        assertArrayEquals(floatArrayOf(1f / 1920, 1f / 1080, 1f / 1920, -1f / 1080), c[1], 1e-9f)
        assertArrayEquals(floatArrayOf(-1f / 1920, 2f / 1080, 1f / 1920, 2f / 1080), c[2], 1e-9f)
        assertArrayEquals(floatArrayOf(0f, 4f / 1080, 0f, 0f), c[3], 1e-9f)
        assertArrayEquals(floatArrayOf(1f / 1920, 1f / 1080, 1920f, 1080f), UpscalerPlan.sgsrViewport(1920, 1080), 1e-9f)
    }
}
