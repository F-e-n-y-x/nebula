package io.github.fenyx.nebula.engine

import android.content.SharedPreferences
import com.limelight.nvstream.http.AdaptiveBitrateService

/**
 * Adaptive bitrate modes. The wire names are Foundation's (`/api/abr` `mode`), which Nova and
 * Foundation hosts understand; V+ stores the same value under `list_abr_mode`.
 */
enum class AbrMode(val wire: String?) {
    OFF(null),

    /** Drops early on loss and climbs slowly: steadier picture, lower latency ("lowLatency"). */
    CONSERVATIVE(AdaptiveBitrateService.MODE_LOW_LATENCY),
    BALANCED(AdaptiveBitrateService.MODE_BALANCED),

    /** Climbs quickly and tolerates more latency: sharpest picture ("quality"). */
    AGGRESSIVE(AdaptiveBitrateService.MODE_QUALITY),
    ;

    companion object {
        /** The mode for V+'s pair of settings. */
        fun from(enabled: Boolean, wire: String?): AbrMode = when {
            !enabled -> OFF
            wire == AdaptiveBitrateService.MODE_LOW_LATENCY -> CONSERVATIVE
            wire == AdaptiveBitrateService.MODE_QUALITY -> AGGRESSIVE
            else -> BALANCED
        }
    }
}

/**
 * The user's ABR choice. [minKbps] / [maxKbps] of 0 use the mode's range around the starting
 * bitrate (Foundation presets); the host's own `max_bitrate` always caps it.
 */
data class AbrSettings(val mode: AbrMode = AbrMode.OFF, val minKbps: Int = 0, val maxKbps: Int = 0) {
    /** Writes the settings where V+ and the engine read them. */
    fun write(editor: SharedPreferences.Editor): SharedPreferences.Editor = editor
        .putBoolean(ENABLED_KEY, mode != AbrMode.OFF)
        .putString(MODE_KEY, (mode.takeIf { it != AbrMode.OFF } ?: AbrMode.BALANCED).wire)
        .putInt(MIN_KEY, minKbps.coerceAtLeast(0))
        .putInt(MAX_KEY, maxKbps.coerceAtLeast(0))

    /** Bounds with a user minimum above the maximum folded onto it. */
    fun normalized(): AbrSettings {
        val lo = minKbps.coerceAtLeast(0)
        val hi = maxKbps.coerceAtLeast(0)
        return copy(minKbps = if (hi in 1 until lo) hi else lo, maxKbps = hi)
    }

    companion object {
        /** V+ key (Boolean). */
        const val ENABLED_KEY = "checkbox_adaptive_bitrate"

        /** V+ key (String: quality | balanced | lowLatency). */
        const val MODE_KEY = "list_abr_mode"

        /** Nebula keys (Int kbps, 0 = the mode's own bound). */
        const val MIN_KEY = "nebula_abr_min_kbps"
        const val MAX_KEY = "nebula_abr_max_kbps"

        /** Reads from a preference map (values may be stored as strings by older screens). */
        fun from(values: Map<String, *>): AbrSettings {
            val enabled = when (val v = values[ENABLED_KEY]) {
                is Boolean -> v
                is String -> v.toBooleanStrictOrNull() ?: false
                else -> false
            }
            fun int(key: String) = when (val v = values[key]) {
                is Int -> v
                is Long -> v.toInt()
                is String -> v.toIntOrNull() ?: 0
                else -> 0
            }.coerceAtLeast(0)
            return AbrSettings(AbrMode.from(enabled, values[MODE_KEY] as? String), int(MIN_KEY), int(MAX_KEY)).normalized()
        }

        fun read(prefs: SharedPreferences): AbrSettings = from(prefs.all)
    }
}

/** Who is steering the bitrate of a stream with ABR on. */
enum class AbrSource {
    /** The host decides (Nova or Foundation `/api/abr`). */
    HOST,

    /** Still enabling ABR on the host; the client controller runs meanwhile. */
    CONNECTING,

    /** The host has no ABR; this device's controller decides. */
    LOCAL,
}

/** ABR state of a running stream, for the stats overlay. */
data class AbrState(val mode: AbrMode, val source: AbrSource, val minKbps: Int, val maxKbps: Int, val lastReason: String?)

internal fun sourceOf(name: String): AbrSource = when (name) {
    "server" -> AbrSource.HOST
    "connecting" -> AbrSource.CONNECTING
    else -> AbrSource.LOCAL
}
