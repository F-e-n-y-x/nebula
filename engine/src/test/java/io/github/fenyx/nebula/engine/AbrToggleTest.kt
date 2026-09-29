package io.github.fenyx.nebula.engine

import com.limelight.nvstream.http.AdaptiveBitrateService
import org.junit.Assert.assertEquals
import org.junit.Test

class AbrToggleTest {
    @Test fun `off keeps the bounds and turns the mode off`() {
        val s = AbrSettings(AbrMode.CONSERVATIVE, minKbps = 4000)
        assertEquals(AbrSettings(AbrMode.OFF, minKbps = 4000), s.toggled(false, null))
    }

    @Test fun `on brings back the last mode, balanced when none`() {
        val off = AbrSettings(AbrMode.OFF)
        assertEquals(AbrMode.BALANCED, off.toggled(true, null).mode)
        assertEquals(AbrMode.CONSERVATIVE, off.toggled(true, AdaptiveBitrateService.MODE_LOW_LATENCY).mode)
        assertEquals(AbrMode.AGGRESSIVE, off.toggled(true, AdaptiveBitrateService.MODE_QUALITY).mode)
    }

    @Test fun `on while already on changes nothing`() {
        val s = AbrSettings(AbrMode.AGGRESSIVE)
        assertEquals(s, s.toggled(true, AdaptiveBitrateService.MODE_LOW_LATENCY))
    }
}
