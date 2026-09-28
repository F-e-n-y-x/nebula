package io.github.f_e_n_y_x.nebula

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Why a live resolution switch needs a new video surface. Frame generation presents with
 * ANativeWindow_lock, which connects the window as a CPU producer until the Surface itself goes
 * away; the next connection's decoder then can't attach to that Surface and its video stream fails
 * to start. A decoder that was simply released leaves the Surface usable.
 */
@RunWith(AndroidJUnit4::class)
class VideoSurfaceProducerTest {
    /** Configures and starts an H.264 decoder on [surface]; the failure, or null when it worked. */
    private fun startDecoder(surface: Surface): Throwable? {
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        return try {
            codec.configure(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 360), surface, null, 0)
            codec.start()
            null
        } catch (t: Throwable) {
            t
        } finally {
            codec.release()
        }
    }

    private fun newSurface() = Surface(SurfaceTexture(false))

    @Test fun aSurfaceACpuProducerDrewIntoRefusesTheNextDecoder() {
        val used = newSurface()
        used.unlockCanvasAndPost(used.lockCanvas(null)) // what frame generation's presenter leaves behind
        assertNotNull("a decoder attached to a surface still connected to a CPU producer", startDecoder(used))
        assertNull("a fresh surface takes the decoder", startDecoder(newSurface()))
    }

    @Test fun aSurfaceADecoderReleasedCanBeReused() {
        val surface = newSurface()
        assertNull(startDecoder(surface))
        assertNull("released decoders disconnect, so the plain path may reuse the surface", startDecoder(surface))
    }
}
