package io.github.fenyx.nebula.engine.internal

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import com.limelight.LimeLog
import com.limelight.binding.video.MediaCodecHelper

/**
 * One-time decoder setup that V+ did in PcView/Game: MediaCodecHelper needs the GPU's GL renderer
 * string (decoder whitelists and errata depend on it) before any capability query. The string is
 * probed with a tiny offscreen EGL context and cached per OS build in the same "GlPreferences"
 * file V+ uses, so both apps share it.
 */
internal object DecoderSupport {
    private const val PREFS = "GlPreferences"
    private const val RENDERER = "Renderer"
    private const val FINGERPRINT = "Fingerprint"

    @Volatile
    private var ready = false

    /** Initializes MediaCodecHelper once; safe from any thread, cheap after the first call. */
    fun ensure(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            MediaCodecHelper.initialize(context.applicationContext, glRenderer(context))
            ready = true
        }
    }

    /** The cached GL renderer, probing the GPU when there's none for this OS build. Empty if probing fails. */
    fun glRenderer(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, 0)
        val cached = prefs.getString(RENDERER, "").orEmpty()
        if (cached.isNotEmpty() && prefs.getString(FINGERPRINT, "") == Build.FINGERPRINT) return cached
        val probed = runCatching { probe() }.onFailure { LimeLog.warning("GL renderer probe failed: ${it.message}") }.getOrNull()
        if (probed.isNullOrEmpty()) return cached
        prefs.edit().putString(RENDERER, probed).putString(FINGERPRINT, Build.FINGERPRINT).commit()
        LimeLog.info("Fetched GL Renderer: $probed")
        return probed
    }

    /** Makes a 1×1 pbuffer GLES2 context current on this thread just long enough to read GL_RENDERER. */
    private fun probe(): String? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        // No eglTerminate: the display is shared with the UI's renderer in this process.
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_NONE,
        )
        if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
        val config = configs[0]
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT) return null
        val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        try {
            if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) return null
            return GLES20.glGetString(GLES20.GL_RENDERER)
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }
}
