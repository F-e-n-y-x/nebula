package io.github.f_e_n_y_x.nebula.ui.screens

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import kotlin.math.max
import kotlin.math.min

/** This device's panel size in landscape (what "match this device" streams at). */
fun deviceResolution(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val (w, h) = if (Build.VERSION.SDK_INT >= 30) {
        val b = wm.maximumWindowMetrics.bounds
        b.width() to b.height()
    } else {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        m.widthPixels to m.heightPixels
    }
    return max(w, h) to min(w, h)
}

/** This screen's full size in its current orientation (portrait sizes while the phone is upright). */
fun screenResolution(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= 30) {
        val b = wm.maximumWindowMetrics.bounds
        b.width() to b.height()
    } else {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        m.widthPixels to m.heightPixels
    }
}

fun modeLabel(context: Context, mode: DisplayMode, s: StreamSettings): String = when (mode) {
    DisplayMode.VIRTUAL -> {
        val (w, h) = if (s.resolution.width > 0) s.resolution.width to s.resolution.height else deviceResolution(context)
        "Virtual display · ${w}×$h · ${s.fps} Hz"
    }
    DisplayMode.MIRROR -> "Desktop · as it is on the PC"
}

fun modeShort(mode: DisplayMode) = if (mode == DisplayMode.VIRTUAL) "Virtual display" else "Desktop (Mirror)"

fun relativeAgo(epochS: Long?): String? {
    epochS ?: return null
    val d = System.currentTimeMillis() / 1000 - epochS
    return when {
        d < 3_600 -> "just now"
        d < 86_400 -> "${d / 3_600} h ago"
        d < 2 * 86_400 -> "yesterday"
        d < 30 * 86_400 -> "${d / 86_400} days ago"
        else -> "${d / (30 * 86_400)} months ago"
    }
}

fun playtime(s: Long): String? = when {
    s <= 0 -> null
    s < 3_600 -> "${s / 60} min played"
    else -> "${s / 3_600} h played"
}

fun Game.metaLine(): String = listOfNotNull(genres.take(2).joinToString(" · ").ifBlank { null }, developer, releaseYear).joinToString(" · ")
