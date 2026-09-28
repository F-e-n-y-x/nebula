package io.github.fenyx.nebula.engine.framegen

import java.io.File

/**
 * Preference keys for frame generation and upscaling. The V+ keys are unchanged so a V+ settings
 * backup (and V+'s own planner) keep working; the `nebula_` keys are Nebula additions.
 */
object FramegenKeys {
    // V+ keys (app/.../preferences/FramegenSettings.kt).
    const val ENABLED = "checkbox_framegen_enabled"
    const val ADAPTIVE = "checkbox_framegen_adaptive_enabled"
    const val QUALITY_PRESET = "list_framegen_quality_preset"
    const val CUSTOM_SCALE = "seekbar_framegen_internal_width"
    const val SLOW_THRESHOLD_MS = "seekbar_framegen_slow_threshold_ms"
    const val PRESENT_REAL_FIRST = "checkbox_framegen_present_real_first"
    const val DLL_STAGED_PATH = "pref_framegen_lossless_dll_staged_path"
    const val DLL_SOURCE = "pref_framegen_lossless_dll_source"
    const val DLL_BUILTIN_SHA = "pref_framegen_lossless_dll_builtin_sha"
    /** SHA-256 of whichever DLL is staged (bundled or picked); part of the self-test fingerprint. */
    const val DLL_SHA = "nebula_framegen_dll_sha"
    /** V+ settings actions (not stored values). */
    const val ACTION_PICK_DLL = "pref_framegen_pick_lossless_dll"
    const val ACTION_SELF_TEST = "pref_framegen_selftest"

    // Nebula keys.
    /** "2" (the only multiplier V+'s pipeline has). Stored as a string like V+ list prefs. */
    const val MULTIPLIER = "nebula_framegen_multiplier"
    /** LSFG flow scale in percent, 25..100 (Int). */
    const val FLOW_SCALE = "nebula_framegen_flow_scale"
    /** LSFG 3.1P, the lighter "performance" model (Boolean). */
    const val PERFORMANCE_MODE = "nebula_framegen_performance_mode"
    /** Turn frame generation off when the phone runs hot or frames get slow (Boolean, default on). */
    const val THERMAL_GUARD = "nebula_framegen_thermal_guard"
    /** Last self-test report, JSON (String). */
    const val SELF_TEST_REPORT = "nebula_framegen_selftest_report"

    /** Decoder-output upscaler: "off", "sharpen", "sgsr1" or "fsr1" (String). */
    const val UPSCALER = "nebula_upscaler"
    /** Upscaler sharpening strength in percent, 0..100 (Int). */
    const val UPSCALER_STRENGTH = "nebula_upscaler_strength"
}

/** V+'s "Frame generation quality" choices: the LSFG internal width as a share of the stream width. */
enum class QualityPreset(val id: String, val scale: Float?) {
    PERFORMANCE("performance", 0.25f),
    BALANCED("balanced", 0.50f),
    CLARITY("clarity", 0.75f),
    CUSTOM("custom", null);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: PERFORMANCE
    }
}

/** Frame generation settings as stored. Built from a preferences snapshot with [from]. */
data class FramegenConfig(
    val enabled: Boolean = false,
    val adaptive: Boolean = false,
    val multiplier: Int = 2,
    val flowScalePercent: Int = DEFAULT_FLOW_SCALE,
    val performanceMode: Boolean = false,
    val quality: QualityPreset = QualityPreset.PERFORMANCE,
    val customScalePercent: Int = DEFAULT_CUSTOM_SCALE,
    val slowThresholdMs: Int = DEFAULT_SLOW_THRESHOLD_MS,
    val presentRealFirst: Boolean = false,
    val thermalGuard: Boolean = true,
    val dllPath: String? = null,
) {
    val flowScale: Float get() = flowScalePercent.coerceIn(MIN_FLOW_SCALE, 100) / 100f

    /**
     * LSFG's internal width for a stream [streamWidth] pixels wide, the V+ rule: preset share,
     * rounded down to 16, clamped to 320..1920.
     */
    fun internalWidth(streamWidth: Int): Int {
        val scale = quality.scale ?: (customScalePercent.coerceIn(MIN_CUSTOM_SCALE, 100) / 100f)
        val w = if (streamWidth <= 0) DEFAULT_INTERNAL_WIDTH else ((streamWidth * scale).toInt() / 16) * 16
        return w.coerceIn(MIN_INTERNAL_WIDTH, MAX_INTERNAL_WIDTH)
    }

    companion object {
        const val DEFAULT_FLOW_SCALE = 100
        const val MIN_FLOW_SCALE = 25
        const val DEFAULT_CUSTOM_SCALE = 50
        const val MIN_CUSTOM_SCALE = 20
        const val DEFAULT_SLOW_THRESHOLD_MS = 18
        const val DEFAULT_INTERNAL_WIDTH = 864
        const val MIN_INTERNAL_WIDTH = 320
        const val MAX_INTERNAL_WIDTH = 1920
        /** Multipliers the V+ pipeline can do: it has one generated-frame slot, so 2x only. */
        val SUPPORTED_MULTIPLIERS = listOf(2)

        /**
         * Reads a SharedPreferences.getAll() snapshot, tolerating values stored under another type
         * (V+ backups store some ints as strings). [isFile] checks the staged DLL path.
         */
        fun from(values: Map<String, *>, isFile: (String) -> Boolean = { File(it).let { f -> f.isFile && f.length() > 0 } }): FramegenConfig {
            fun bool(k: String, d: Boolean) = when (val v = values[k]) {
                is Boolean -> v
                is String -> v.toBooleanStrictOrNull() ?: d
                else -> d
            }
            fun int(k: String, d: Int) = when (val v = values[k]) {
                is Int -> v
                is Long -> v.toInt()
                is Float -> v.toInt()
                is String -> v.toIntOrNull() ?: d
                else -> d
            }
            val multiplier = int(FramegenKeys.MULTIPLIER, 2).takeIf { it in SUPPORTED_MULTIPLIERS } ?: 2
            // V+ once stored the custom scale as an absolute width; anything over 100 means that.
            val custom = int(FramegenKeys.CUSTOM_SCALE, DEFAULT_CUSTOM_SCALE).let { if (it > 100) DEFAULT_CUSTOM_SCALE else it }
            return FramegenConfig(
                enabled = bool(FramegenKeys.ENABLED, false),
                adaptive = bool(FramegenKeys.ADAPTIVE, false),
                multiplier = multiplier,
                flowScalePercent = int(FramegenKeys.FLOW_SCALE, DEFAULT_FLOW_SCALE).coerceIn(MIN_FLOW_SCALE, 100),
                performanceMode = bool(FramegenKeys.PERFORMANCE_MODE, false),
                quality = QualityPreset.of(values[FramegenKeys.QUALITY_PRESET] as? String),
                customScalePercent = custom.coerceIn(MIN_CUSTOM_SCALE, 100),
                slowThresholdMs = int(FramegenKeys.SLOW_THRESHOLD_MS, DEFAULT_SLOW_THRESHOLD_MS).coerceIn(8, 30),
                presentRealFirst = bool(FramegenKeys.PRESENT_REAL_FIRST, false),
                thermalGuard = bool(FramegenKeys.THERMAL_GUARD, true),
                dllPath = (values[FramegenKeys.DLL_STAGED_PATH] as? String)?.takeIf { it.isNotBlank() && isFile(it) },
            )
        }
    }
}

/** Decoder-output post-processing modes (spatial only: a video stream has no motion vectors). */
enum class UpscalerMode(val id: String, val label: String) {
    OFF("off", "Off"),
    SHARPEN("sharpen", "Sharpen only (RCAS)"),
    SGSR1("sgsr1", "Snapdragon GSR 1"),
    FSR1("fsr1", "AMD FSR 1 (EASU + RCAS)");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: OFF
    }
}

data class UpscalerConfig(val mode: UpscalerMode = UpscalerMode.OFF, val strengthPercent: Int = DEFAULT_STRENGTH) {
    val strength: Float get() = strengthPercent.coerceIn(0, 100) / 100f

    companion object {
        const val DEFAULT_STRENGTH = 50

        fun from(values: Map<String, *>): UpscalerConfig {
            val strength = when (val v = values[FramegenKeys.UPSCALER_STRENGTH]) {
                is Int -> v
                is String -> v.toIntOrNull()
                else -> null
            } ?: DEFAULT_STRENGTH
            return UpscalerConfig(UpscalerMode.of(values[FramegenKeys.UPSCALER] as? String), strength.coerceIn(0, 100))
        }
    }
}
