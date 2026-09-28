package io.github.fenyx.nebula.engine.upscale

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Surface
import com.limelight.LimeLog
import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.framegen.UpscalerMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Decoder → SurfaceTexture → GLES 3.0 passes → window. Owns a GL thread. The decoder writes into
 * [inputSurface]; every new frame is drawn through the passes [UpscalerPlan.passes] picks for the
 * current window size and presented on the output window.
 *
 * The output EGL surface is created on the first decoded frame, not before: the decoder starts on
 * the SurfaceView itself and only disconnects from it when it switches to [inputSurface] (a window
 * takes one producer at a time).
 */
class UpscalerRenderer private constructor(
    private val inW: Int,
    private val inH: Int,
    @Volatile private var config: UpscalerConfig,
    private val onFrame: (UpscaleFrameInfo) -> Unit,
) {
    private val thread = HandlerThread("NebulaUpscaler", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val gl = Handler(thread.looper)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE
    private var outputSurface: Surface? = null
    private var outputChanged = false

    private var oesTex = 0
    private lateinit var surfaceTexture: SurfaceTexture
    lateinit var inputSurface: Surface
        private set

    private val programs = HashMap<UpscalePass, Program>()
    private val targets = HashMap<String, Target>()
    private val texMatrix = FloatArray(16)
    private var vao = 0
    @Volatile private var released = false

    fun setConfig(c: UpscalerConfig) { config = c }

    /** Where to present; null detaches (the window can then take another producer). */
    fun setOutput(surface: Surface?) = gl.post {
        outputSurface = surface
        outputChanged = true
        if (surface == null) destroyWindow()
    }

    fun release() {
        if (released) return
        released = true
        val done = CountDownLatch(1)
        gl.post {
            runCatching {
                surfaceTexture.setOnFrameAvailableListener(null)
                destroyWindow()
                programs.values.forEach { GLES30.glDeleteProgram(it.id) }
                targets.values.forEach { it.delete() }
                if (oesTex != 0) GLES30.glDeleteTextures(1, intArrayOf(oesTex), 0)
                if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
                inputSurface.release()
                surfaceTexture.release()
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
            done.countDown()
        }
        done.await(500, TimeUnit.MILLISECONDS)
        thread.quitSafely()
    }

    private fun setupGl(): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2)
        if (!EGL14.eglInitialize(display, v, 0, v, 1)) return false
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, n, 0) || n[0] == 0) return false
        eglConfig = configs[0]
        context = EGL14.eglCreateContext(display, eglConfig, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) return false
        pbuffer = EGL14.eglCreatePbufferSurface(display, eglConfig, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        if (!EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)) return false

        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        oesTex = t[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        val a = IntArray(1)
        GLES30.glGenVertexArrays(1, a, 0)
        vao = a[0]

        surfaceTexture = SurfaceTexture(oesTex).apply {
            setDefaultBufferSize(inW, inH)
            setOnFrameAvailableListener({ drawFrame() }, gl)
        }
        inputSurface = Surface(surfaceTexture)
        return true
    }

    private fun ensureWindow(): Boolean {
        if (outputChanged) {
            destroyWindow()
            outputChanged = false
        }
        if (window != EGL14.EGL_NO_SURFACE) return true
        val s = outputSurface?.takeIf { it.isValid } ?: return false
        window = runCatching { EGL14.eglCreateWindowSurface(display, eglConfig, s, intArrayOf(EGL14.EGL_NONE), 0) }.getOrDefault(EGL14.EGL_NO_SURFACE)
        if (window == EGL14.EGL_NO_SURFACE) {
            LimeLog.warning("Upscaler: eglCreateWindowSurface failed (0x${Integer.toHexString(EGL14.eglGetError())}); will retry")
            return false
        }
        return true
    }

    private fun destroyWindow() {
        if (window == EGL14.EGL_NO_SURFACE) return
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        EGL14.eglDestroySurface(display, window)
        window = EGL14.EGL_NO_SURFACE
    }

    private fun drawFrame() {
        if (released) return
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(texMatrix)
        if (!ensureWindow()) return
        EGL14.eglMakeCurrent(display, window, window, context)
        val wh = IntArray(2)
        EGL14.eglQuerySurface(display, window, EGL14.EGL_WIDTH, wh, 0)
        EGL14.eglQuerySurface(display, window, EGL14.EGL_HEIGHT, wh, 1)
        val outW = wh[0].coerceAtLeast(1)
        val outH = wh[1].coerceAtLeast(1)
        val c = config
        val passes = UpscalerPlan.passes(c.mode, inW, inH, outW, outH).ifEmpty { listOf(UpscalePass.BILINEAR) }

        val start = SystemClock.elapsedRealtimeNanos()
        GLES30.glBindVertexArray(vao)
        var srcTex = oesTex
        passes.forEachIndexed { idx, pass ->
            val last = idx == passes.lastIndex
            // COPY stays at stream size; everything after it runs at window size.
            val (w, h) = if (pass == UpscalePass.COPY) inW to inH else outW to outH
            val target = if (last) null else target("$idx", w, h).also { GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0) }
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target?.fbo ?: 0)
            GLES30.glViewport(0, 0, w, h)
            val p = program(pass)
            GLES30.glUseProgram(p.id)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            // Only the sampled texture stays bound, so a render target is never also bound
            // (some drivers, SwiftShader among them, treat that as a feedback loop and skip the draw).
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            if (pass == UpscalePass.COPY || pass == UpscalePass.BILINEAR) {
                GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, srcTex)
                GLES30.glUniformMatrix4fv(p.loc("uTexMatrix"), 1, false, texMatrix, 0)
            } else {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
                // The source is the previous pass: stream size after COPY, window size otherwise.
                val prev = passes[idx - 1]
                if (prev == UpscalePass.COPY) GLES30.glUniform2i(p.loc("uSrcSize"), inW, inH)
                else GLES30.glUniform2i(p.loc("uSrcSize"), outW, outH)
            }
            GLES30.glUniform1i(p.loc("uSrc"), 0)
            when (pass) {
                UpscalePass.EASU -> UpscalerPlan.easuConstants(inW, inH, outW, outH).forEachIndexed { i, v -> GLES30.glUniform4fv(p.loc("uCon$i"), 1, v, 0) }
                UpscalePass.RCAS -> GLES30.glUniform1f(p.loc("uSharpness"), UpscalerPlan.rcasSharpness(c.strength))
                UpscalePass.SGSR -> {
                    GLES30.glUniform4fv(p.loc("uViewportInfo"), 1, UpscalerPlan.sgsrViewport(inW, inH), 0)
                    GLES30.glUniform1f(p.loc("uEdgeSharpness"), UpscalerPlan.sgsrEdgeSharpness(c.strength))
                }
                else -> Unit
            }
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            if (target != null) srcTex = target.tex
        }
        val drawNs = SystemClock.elapsedRealtimeNanos() - start
        EGLExt.eglPresentationTimeANDROID(display, window, surfaceTexture.timestamp)
        if (!EGL14.eglSwapBuffers(display, window)) {
            LimeLog.warning("Upscaler: eglSwapBuffers failed (0x${Integer.toHexString(EGL14.eglGetError())}); recreating the window")
            destroyWindow()
        }
        onFrame(UpscaleFrameInfo(UpscalerPlan.effectiveMode(c.mode, inW, inH, outW, outH), outW, outH, drawNs / 1_000_000f))
    }

    private fun target(key: String, w: Int, h: Int): Target {
        targets[key]?.let { if (it.w == w && it.h == h) return it; it.delete() }
        return Target.create(w, h).also { targets[key] = it }
    }

    private fun program(pass: UpscalePass): Program = programs.getOrPut(pass) {
        Program.build(
            UpscalerShaders.VERTEX,
            when (pass) {
                UpscalePass.COPY -> UpscalerShaders.COPY_OES
                UpscalePass.BILINEAR -> UpscalerShaders.BILINEAR_OES
                UpscalePass.EASU -> UpscalerShaders.EASU
                UpscalePass.RCAS -> UpscalerShaders.RCAS
                UpscalePass.SGSR -> UpscalerShaders.SGSR
            },
        )
    }

    private class Target(val fbo: Int, val tex: Int, val w: Int, val h: Int) {
        fun delete() {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
        }

        companion object {
            fun create(w: Int, h: Int): Target {
                val t = IntArray(1)
                GLES30.glGenTextures(1, t, 0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                val f = IntArray(1)
                GLES30.glGenFramebuffers(1, f, 0)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f[0])
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t[0], 0)
                return Target(f[0], t[0], w, h)
            }
        }
    }

    private class Program(val id: Int) {
        private val locs = HashMap<String, Int>()
        fun loc(name: String) = locs.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

        companion object {
            fun build(vs: String, fs: String): Program {
                fun compile(type: Int, src: String): Int {
                    val s = GLES30.glCreateShader(type)
                    GLES30.glShaderSource(s, src)
                    GLES30.glCompileShader(s)
                    val ok = IntArray(1)
                    GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
                    if (ok[0] == 0) error("shader compile failed: ${GLES30.glGetShaderInfoLog(s)}")
                    return s
                }
                val p = GLES30.glCreateProgram()
                GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
                GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
                GLES30.glLinkProgram(p)
                val ok = IntArray(1)
                GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
                if (ok[0] == 0) error("program link failed: ${GLES30.glGetProgramInfoLog(p)}")
                return Program(p)
            }
        }
    }

    companion object {
        /**
         * Starts a renderer for [inW]x[inH] decoded frames, or null when GLES 3 isn't available
         * or a shader doesn't compile (every program is built up front so this fails early).
         */
        fun create(inW: Int, inH: Int, config: UpscalerConfig, onFrame: (UpscaleFrameInfo) -> Unit): UpscalerRenderer? {
            val r = UpscalerRenderer(inW, inH, config, onFrame)
            val ok = CountDownLatch(1)
            var success = false
            r.gl.post {
                success = runCatching {
                    r.setupGl() && UpscalePass.entries.all { r.program(it); true }
                }.onFailure { LimeLog.warning("Upscaler: GL setup failed: ${it.message}") }.getOrDefault(false)
                ok.countDown()
            }
            if (!ok.await(2, TimeUnit.SECONDS) || !success) {
                r.release()
                return null
            }
            return r
        }
    }
}

data class UpscaleFrameInfo(val mode: UpscalerMode, val outW: Int, val outH: Int, val drawMs: Float)
