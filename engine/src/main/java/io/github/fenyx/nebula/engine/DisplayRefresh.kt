package io.github.fenyx.nebula.engine

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import kotlin.math.roundToInt

/**
 * The screen's native frame rate: the highest refresh rate its display modes offer at the current
 * physical resolution. Display.getRefreshRate() is the current rate, and under adaptive refresh
 * (Samsung, Pixel) it often says 60 while the phone is idle on a 120 Hz panel. That made a fresh
 * install default to 60 fps on an S25 Ultra.
 */
object DisplayRefresh {
    data class Mode(val width: Int, val height: Int, val refreshHz: Float)

    const val FALLBACK_HZ = 60

    /**
     * Highest refresh rate among [modes] of the current physical size [width]x[height], in either
     * orientation. With no mode of that size it falls back to [currentHz], then [FALLBACK_HZ]. Rounded
     * (119.88 → 120) and kept to 30..240.
     */
    fun maxHz(modes: List<Mode>, width: Int, height: Int, currentHz: Float?): Int =
        maxRefresh(modes, width, height, currentHz).roundToInt().coerceIn(30, 240)

    /** [maxHz] unrounded (119.88), for the client refresh rate the host is told. */
    fun maxRefresh(modes: List<Mode>, width: Int, height: Int, currentHz: Float?): Float {
        val same = modes.filter { (it.width == width && it.height == height) || (it.width == height && it.height == width) }
        return same.maxOfOrNull { it.refreshHz } ?: currentHz?.takeIf { it > 1f } ?: FALLBACK_HZ.toFloat()
    }

    /**
     * Settings › Stream › Frame rate choices: "Native (120 Hz)" first, then the standard rates
     * except the one native already covers.
     */
    fun frameRateChoices(nativeHz: Int, standard: List<Int> = listOf(30, 60, 90, 120, 144)): List<Pair<String, Int>> =
        listOf("Native ($nativeHz Hz)" to nativeHz) + standard.filter { it != nativeHz }.map { "$it" to it }

    /** This device's native frame rate (the default display), rounded. */
    fun nativeHz(ctx: Context): Int = nativeRefresh(ctx).roundToInt().coerceIn(30, 240)

    /** The default display's highest refresh rate at its current size, unrounded (119.88). */
    fun nativeRefresh(ctx: Context): Float {
        val display = runCatching {
            (ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull() ?: return FALLBACK_HZ.toFloat()
        val current = display.refreshRate
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return maxRefresh(emptyList(), 0, 0, current)
        return runCatching {
            val mode = display.mode
            maxRefresh(display.supportedModes.map { Mode(it.physicalWidth, it.physicalHeight, it.refreshRate) }, mode.physicalWidth, mode.physicalHeight, current)
        }.getOrElse { maxRefresh(emptyList(), 0, 0, current) }
    }
}
