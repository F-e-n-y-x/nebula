package io.github.fenyx.nebula.engine.upscale

import android.content.Context
import android.view.SurfaceHolder
import com.limelight.LimeLog
import com.limelight.binding.video.MediaCodecDecoderRenderer
import io.github.fenyx.nebula.engine.framegen.FramegenDll
import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.framegen.UpscalerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Decoder-output upscaling for one stream: when a mode is chosen (and frame generation, which
 * upscales on its own, isn't running), the decoder's output goes into [UpscalerRenderer] through
 * the decoder's capture hook (the same delayed switch frame generation uses); otherwise the
 * zero-copy SurfaceView path is left untouched. Main thread only, except [onWindow].
 */
class UpscalerController(private val context: Context) {
    private var renderer: UpscalerRenderer? = null
    private var frames = 0
    private var drawMsSum = 0f
    @Volatile private var last: UpscaleFrameInfo? = null

    private val _status = MutableStateFlow(UpscalerStatus())
    val status: StateFlow<UpscalerStatus> = _status.asStateFlow()

    /** True while decoded frames go through the upscaler. */
    val isArmed: Boolean get() = renderer != null

    fun config(): UpscalerConfig = UpscalerConfig.from(FramegenDll.prefs(context).all)

    /** Arms the upscaler for a [width]x[height] SDR stream presenting on [target]; true when armed. */
    fun arm(decoder: MediaCodecDecoderRenderer, target: SurfaceHolder, width: Int, height: Int, hdr: Boolean = false): Boolean {
        release(decoder)
        val c = config()
        UpscalerPlan.offReason(c, hdr, framegenArmed = false)?.let {
            _status.value = UpscalerStatus(requested = c.mode, offReason = it)
            return false
        }
        val r = UpscalerRenderer.create(width, height, c) { info ->
            synchronized(this) {
                frames++
                drawMsSum += info.drawMs
                last = info
            }
        }
        if (r == null) {
            _status.value = UpscalerStatus(requested = c.mode, offReason = UpscalerPlan.OFF_NO_GLES)
            return false
        }
        renderer = r
        r.setOutput(target.surface)
        decoder.framegenSurface = r.inputSurface
        decoder.setFramegenCaptureSwitchReady(true)
        _status.value = UpscalerStatus(requested = c.mode, active = c.mode, inputWidth = width, inputHeight = height)
        LimeLog.info("Upscaler armed: ${c.mode.id} strength ${c.strengthPercent}% for ${width}x$height")
        return true
    }

    fun onOutputSurface(target: SurfaceHolder) {
        renderer?.setOutput(target.surface)
    }

    /** Live strength / mode changes from the stream menu (same pass set as armed, else re-arm). */
    fun applyConfig() {
        renderer?.setConfig(config())
    }

    fun release(decoder: MediaCodecDecoderRenderer?) {
        val r = renderer ?: return
        renderer = null
        decoder?.setFramegenCaptureSwitchReady(false)
        decoder?.framegenSurface = null
        r.setOutput(null)
        r.release()
        _status.value = _status.value.copy(active = UpscalerMode.OFF, renderMs = 0f)
    }

    /** Once per stats window: publishes the average draw time of the frames since the last one. */
    fun onWindow() {
        if (renderer == null) return
        val (n, sum, info) = synchronized(this) { Triple(frames, drawMsSum, last).also { frames = 0; drawMsSum = 0f } }
        info ?: return
        _status.value = _status.value.copy(
            active = info.mode,
            outputWidth = info.outW,
            outputHeight = info.outH,
            renderMs = if (n > 0) sum / n else 0f,
        )
    }
}
