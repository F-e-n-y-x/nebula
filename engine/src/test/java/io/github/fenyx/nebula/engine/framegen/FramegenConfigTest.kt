package io.github.fenyx.nebula.engine.framegen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramegenConfigTest {
    private val present: (String) -> Boolean = { it == "/data/fg/Lossless.dll" }

    @Test fun `defaults match V+`() {
        val c = FramegenConfig.from(emptyMap<String, Any>(), present)
        assertFalse(c.enabled)
        assertEquals(2, c.multiplier)
        assertEquals(1f, c.flowScale)
        assertEquals(QualityPreset.PERFORMANCE, c.quality)
        assertEquals(18, c.slowThresholdMs)
        assertTrue("thermal guard defaults on", c.thermalGuard)
        assertNull(c.dllPath)
    }

    @Test fun `reads V+ keys and Nebula keys, tolerating strings`() {
        val c = FramegenConfig.from(
            mapOf(
                FramegenKeys.ENABLED to true,
                FramegenKeys.ADAPTIVE to "true",
                FramegenKeys.QUALITY_PRESET to "custom",
                FramegenKeys.CUSTOM_SCALE to "40",
                FramegenKeys.SLOW_THRESHOLD_MS to 99,
                FramegenKeys.FLOW_SCALE to 10,
                FramegenKeys.PERFORMANCE_MODE to true,
                FramegenKeys.THERMAL_GUARD to false,
                FramegenKeys.MULTIPLIER to "2",
                FramegenKeys.DLL_STAGED_PATH to "/data/fg/Lossless.dll",
            ),
            present,
        )
        assertTrue(c.enabled && c.adaptive && c.performanceMode)
        assertFalse(c.thermalGuard)
        assertEquals(QualityPreset.CUSTOM, c.quality)
        assertEquals(40, c.customScalePercent)
        assertEquals("slow threshold clamps to V+'s slider range", 30, c.slowThresholdMs)
        assertEquals("flow scale clamps to 25%", 25, c.flowScalePercent)
        assertEquals("/data/fg/Lossless.dll", c.dllPath)
    }

    @Test fun `unsupported multipliers fall back to 2x`() {
        assertEquals(2, FramegenConfig.from(mapOf(FramegenKeys.MULTIPLIER to "3"), present).multiplier)
        assertEquals(2, FramegenConfig.from(mapOf(FramegenKeys.MULTIPLIER to "junk"), present).multiplier)
    }

    @Test fun `a staged path that is gone counts as no DLL`() {
        assertNull(FramegenConfig.from(mapOf(FramegenKeys.DLL_STAGED_PATH to "/gone.dll"), present).dllPath)
    }

    @Test fun `internal width follows V+ presets`() {
        assertEquals(640, FramegenConfig(quality = QualityPreset.PERFORMANCE).internalWidth(2560))
        assertEquals(1280, FramegenConfig(quality = QualityPreset.BALANCED).internalWidth(2560))
        assertEquals(1920, FramegenConfig(quality = QualityPreset.CLARITY).internalWidth(2560))
        assertEquals("clamped to 320", 320, FramegenConfig(quality = QualityPreset.PERFORMANCE).internalWidth(1000))
        assertEquals("rounded down to 16", 768, FramegenConfig(quality = QualityPreset.CUSTOM, customScalePercent = 40).internalWidth(1920))
        assertEquals(864, FramegenConfig().internalWidth(0))
    }

    @Test fun `legacy absolute custom width is treated as the default share`() {
        assertEquals(50, FramegenConfig.from(mapOf(FramegenKeys.CUSTOM_SCALE to 1280), present).customScalePercent)
    }

    @Test fun `upscaler config`() {
        assertEquals(UpscalerConfig(), UpscalerConfig.from(emptyMap<String, Any>()))
        val u = UpscalerConfig.from(mapOf(FramegenKeys.UPSCALER to "sgsr1", FramegenKeys.UPSCALER_STRENGTH to 180))
        assertEquals(UpscalerMode.SGSR1, u.mode)
        assertEquals(100, u.strengthPercent)
        assertEquals(UpscalerMode.OFF, UpscalerConfig.from(mapOf(FramegenKeys.UPSCALER to "fsr2")).mode)
    }
}
