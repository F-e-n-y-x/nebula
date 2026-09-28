package io.github.fenyx.nebula.engine.upscale

import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.framegen.UpscalerMode
import kotlin.math.pow

/** What the upscaler is doing in the current stream. */
data class UpscalerStatus(
    val requested: UpscalerMode = UpscalerMode.OFF,
    /** The mode actually running; SHARPEN when an upscaler was asked for but nothing is enlarged. */
    val active: UpscalerMode = UpscalerMode.OFF,
    val inputWidth: Int = 0,
    val inputHeight: Int = 0,
    val outputWidth: Int = 0,
    val outputHeight: Int = 0,
    /** CPU-side time to draw one frame (all passes, excluding the swap), ms. */
    val renderMs: Float = 0f,
    /** Why it's off, when it is. */
    val offReason: String? = null,
) {
    val running: Boolean get() = active != UpscalerMode.OFF
}

/** The passes one frame goes through, in order. */
enum class UpscalePass { COPY, BILINEAR, EASU, RCAS, SGSR }

/**
 * Pure planning and shader-constant math for the decoder-output upscaler, kept free of GL so it
 * is unit-tested. Every mode is spatial (single frame): SGSR2 / FSR2+ need motion vectors and
 * depth, which a video stream doesn't have.
 */
object UpscalerPlan {
    /** Off reasons shown in the overlay and Settings. */
    const val OFF_HDR = "Upscaling is off for HDR streams (it works in 8-bit SDR)."
    const val OFF_FRAMEGEN = "Frame generation is running; it upscales on its own."
    const val OFF_NO_GLES = "This device lacks OpenGL ES 3.0."

    /**
     * Passes for [mode] from a [inW]x[inH] decoded frame to an [outW]x[outH] window. EASU and
     * SGSR only enlarge; when the window isn't larger they fall back to sharpening at output size.
     */
    fun passes(mode: UpscalerMode, inW: Int, inH: Int, outW: Int, outH: Int): List<UpscalePass> {
        val enlarges = outW > inW || outH > inH
        return when (mode) {
            UpscalerMode.OFF -> emptyList()
            UpscalerMode.SHARPEN -> listOf(UpscalePass.BILINEAR, UpscalePass.RCAS)
            UpscalerMode.FSR1 -> if (enlarges) listOf(UpscalePass.COPY, UpscalePass.EASU, UpscalePass.RCAS) else listOf(UpscalePass.BILINEAR, UpscalePass.RCAS)
            UpscalerMode.SGSR1 -> if (enlarges) listOf(UpscalePass.COPY, UpscalePass.SGSR) else listOf(UpscalePass.BILINEAR, UpscalePass.RCAS)
        }
    }

    /** The mode that [passes] effectively runs. */
    fun effectiveMode(mode: UpscalerMode, inW: Int, inH: Int, outW: Int, outH: Int): UpscalerMode =
        if (mode != UpscalerMode.OFF && UpscalePass.RCAS in passes(mode, inW, inH, outW, outH) && UpscalePass.EASU !in passes(mode, inW, inH, outW, outH)) UpscalerMode.SHARPEN else mode

    /** Null when the upscaler may run; otherwise why not. */
    fun offReason(config: UpscalerConfig, hdr: Boolean, framegenArmed: Boolean): String? = when {
        config.mode == UpscalerMode.OFF -> "Upscaling is off."
        framegenArmed -> OFF_FRAMEGEN
        hdr -> OFF_HDR
        else -> null
    }

    /**
     * RCAS sharpness in "stops" (0 = strongest, AMD's 0.2 default is strong-ish): strength 100%
     * gives 0, 50% gives 0.5, 0% gives 2 (barely sharpened). Returned as the linear factor the
     * shader multiplies its lobe by, AMD's FsrRcasCon: 2^-stops.
     */
    fun rcasSharpness(strength: Float): Float {
        val s = strength.coerceIn(0f, 1f)
        val stops = if (s >= 0.5f) (1f - s) else 0.5f + (0.5f - s) * 3f
        return 2.0.pow(-stops.toDouble()).toFloat()
    }

    /** SGSR1's EdgeSharpness: 1.0 at 0%, 2.0 (Qualcomm's default) at 50%, 3.0 at 100%. */
    fun sgsrEdgeSharpness(strength: Float): Float = 1f + 2f * strength.coerceIn(0f, 1f)

    /** SGSR1's ViewportInfo: (1/inW, 1/inH, inW, inH). */
    fun sgsrViewport(inW: Int, inH: Int): FloatArray = floatArrayOf(1f / inW, 1f / inH, inW.toFloat(), inH.toFloat())

    /**
     * AMD FsrEasuCon for a full-frame [inW]x[inH] → [outW]x[outH] upscale, as the four vec4
     * uniforms the EASU shader takes (floats, not the uint bit patterns the HLSL version passes).
     */
    fun easuConstants(inW: Int, inH: Int, outW: Int, outH: Int): Array<FloatArray> {
        val iw = inW.toFloat()
        val ih = inH.toFloat()
        val ow = outW.toFloat()
        val oh = outH.toFloat()
        return arrayOf(
            floatArrayOf(iw / ow, ih / oh, 0.5f * iw / ow - 0.5f, 0.5f * ih / oh - 0.5f),
            floatArrayOf(1f / iw, 1f / ih, 1f / iw, -1f / ih),
            floatArrayOf(-1f / iw, 2f / ih, 1f / iw, 2f / ih),
            floatArrayOf(0f, 4f / ih, 0f, 0f),
        )
    }
}
