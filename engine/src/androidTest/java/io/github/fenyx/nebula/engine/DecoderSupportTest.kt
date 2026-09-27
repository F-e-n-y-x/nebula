package io.github.fenyx.nebula.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The stream path needs MediaCodecHelper initialized with the GL renderer, which V+ did in its own
 * activities. The engine must do it alone ("MediaCodecHelper must be initialized before use").
 */
@RunWith(AndroidJUnit4::class)
class DecoderSupportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun decoderQueriesWorkWithoutTheVPlusApp() {
        val engine = NebulaEngine.create(context)
        val avc = engine.prepareDecoders("video/avc")
        assertNotNull("every Android device has an H.264 decoder", avc)
        val renderer = context.getSharedPreferences("GlPreferences", 0).getString("Renderer", "")
        assertTrue("GL renderer was probed and cached", !renderer.isNullOrEmpty())
    }
}
