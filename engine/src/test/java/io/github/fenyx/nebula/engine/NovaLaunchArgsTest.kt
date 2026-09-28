package io.github.fenyx.nebula.engine

import com.limelight.nvstream.http.NvHTTP
import org.junit.Assert.assertEquals
import org.junit.Test

class NovaLaunchArgsTest {
    @Test fun `portrait request streams in portrait`() {
        assertEquals(StreamOrientation.PORTRAIT, StreamRequest(1080, 2340, 60, 20_000).orientation)
    }

    @Test fun `landscape and square requests stream in landscape`() {
        assertEquals(StreamOrientation.LANDSCAPE, StreamRequest(2340, 1080, 60, 20_000).orientation)
        assertEquals(StreamOrientation.LANDSCAPE, StreamRequest(1440, 1440, 60, 20_000).orientation)
    }

    @Test fun `launch query carries display and orientation`() {
        assertEquals("&nova_display=virtual&nova_orientation=portrait", NvHTTP.novaLaunchQuery("virtual", "portrait"))
        assertEquals("&nova_display=mirror&nova_orientation=landscape", NvHTTP.novaLaunchQuery("mirror", "landscape"))
    }

    @Test fun `unknown or missing values are left out`() {
        assertEquals("&nova_orientation=landscape", NvHTTP.novaLaunchQuery(null, "landscape"))
        assertEquals("", NvHTTP.novaLaunchQuery("bogus", "sideways"))
        assertEquals("&nova_display=virtual", NvHTTP.novaLaunchQuery("virtual", null))
    }
}
