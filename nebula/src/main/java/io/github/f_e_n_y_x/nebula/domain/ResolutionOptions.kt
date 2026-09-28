package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.Resolution

/** One size in the live resolution picker. */
data class ResolutionOption(val label: String, val resolution: Resolution, val kind: Kind) {
    enum class Kind { NATIVE, SCREEN, PRESET, CUSTOM }

    val size: String get() = "${resolution.width}×${resolution.height}"
}

/** What the live resolution picker offers: this device's size, Settings' presets, custom sizes, frame rates. */
object ResolutionOptions {
    /** The presets Settings → Stream offers, plus the low-bandwidth ones behind "Show low-bandwidth resolutions". */
    val PRESETS = listOf("720p" to Resolution(1280, 720), "1080p" to Resolution(1920, 1080), "1440p" to Resolution(2560, 1440), "4K" to Resolution(3840, 2160))
    val LOW_PRESETS = listOf("360p" to Resolution(640, 360), "480p" to Resolution(854, 480))
    val FPS = listOf(30, 60, 90, 120, 144)

    const val MIN_WIDTH = 256
    const val MAX_WIDTH = 7680
    const val MIN_HEIGHT = 256
    const val MAX_HEIGHT = 4320

    /**
     * Native first, then presets from smallest to largest, then custom sizes; each size once.
     * [current] is added as a custom entry when it isn't any of those (e.g. set on another device).
     */
    fun sizes(device: Resolution, custom: List<Resolution>, showLow: Boolean, current: Resolution? = null): List<ResolutionOption> {
        val out = mutableListOf(ResolutionOption("This device", device, ResolutionOption.Kind.NATIVE))
        fun has(r: Resolution) = out.any { it.resolution == r }
        (if (showLow) LOW_PRESETS + PRESETS else PRESETS).forEach { (label, r) ->
            if (!has(r)) out += ResolutionOption(label, r, ResolutionOption.Kind.PRESET)
        }
        custom.sortedWith(compareBy({ it.width }, { it.height })).forEach { r ->
            if (r.width > 0 && r.height > 0 && !has(r)) out += ResolutionOption("Custom", r, ResolutionOption.Kind.CUSTOM)
        }
        if (current != null && current.width > 0 && current.height > 0 && !has(current)) {
            out += ResolutionOption("Current", current, ResolutionOption.Kind.CUSTOM)
        }
        return out
    }

    /** "For this screen" steps: native, then scaled copies of it. 67% is exactly two thirds. */
    val SCREEN_SCALES = listOf(100 to 1.0, 90 to 0.9, 80 to 0.8, 75 to 0.75, 67 to 2.0 / 3.0, 50 to 0.5)

    /** Desktop UI scaling steps the host may apply without changing the resolution. */
    val DESKTOP_SCALES = listOf(100, 125, 150, 175, 200)

    /**
     * This screen's size ([screen], in its current orientation) and scaled copies with exactly
     * its aspect ratio, labelled "Native" and "75% · 2340×1080"-style. Sizes too small to stream
     * or repeated are left out.
     */
    fun forScreen(screen: Resolution): List<ResolutionOption> {
        if (screen.width <= 0 || screen.height <= 0) return emptyList()
        val out = mutableListOf<ResolutionOption>()
        SCREEN_SCALES.forEach { (pct, f) ->
            val r = if (pct == 100) screen else scaled(screen, f)
            if (minOf(r.width, r.height) < MIN_HEIGHT) return@forEach
            if (out.any { it.resolution == r }) return@forEach
            out += ResolutionOption(if (pct == 100) "Native" else "$pct%", r, if (pct == 100) ResolutionOption.Kind.NATIVE else ResolutionOption.Kind.SCREEN)
        }
        return out
    }

    /**
     * [screen] scaled by [factor] keeping its exact aspect ratio: a whole multiple of its reduced
     * ratio (3120×1440 is 13:6 × 240) with even sides. Within 1% of the asked scale, a size whose
     * sides are both multiples of 8 wins (encoders like them); otherwise the closest even one.
     * Screens whose ratio has no even exact multiple near the target (odd, unusual sizes) fall
     * back to the nearest even width and height.
     */
    fun scaled(screen: Resolution, factor: Double): Resolution {
        val g = gcd(screen.width, screen.height)
        val uw = screen.width / g
        val uh = screen.height / g
        val target = g * factor
        val tolerance = maxOf(g * 0.01, 0.5)
        val ks = (kotlin.math.floor(target - tolerance).toInt()..kotlin.math.ceil(target + tolerance).toInt())
            .filter { it > 0 && kotlin.math.abs(it - target) <= tolerance && (it * uw) % 2 == 0 && (it * uh) % 2 == 0 }
        val by8 = ks.filter { (it * uw) % 8 == 0 && (it * uh) % 8 == 0 }
        val k = (by8.ifEmpty { ks }).minByOrNull { kotlin.math.abs(it - target) }
        if (k != null) return Resolution(k * uw, k * uh)
        fun even(v: Double) = (kotlin.math.round(v / 2) * 2).toInt().coerceAtLeast(2)
        return Resolution(even(screen.width * factor), even(screen.height * factor))
    }

    /**
     * The "Standard" section under [forScreen]: Settings' presets and custom sizes (and [current]
     * when it's none of them), without anything the screen section already lists.
     */
    fun standard(screenSizes: List<ResolutionOption>, device: Resolution, custom: List<Resolution>, showLow: Boolean, current: Resolution?): List<ResolutionOption> {
        val shown = screenSizes.map { it.resolution }.toSet()
        return sizes(device, custom, showLow, current.takeUnless { it in shown })
            .filter { it.kind != ResolutionOption.Kind.NATIVE && it.resolution !in shown }
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /** The standard rates plus the current one and this screen's refresh rate, ascending. */
    fun frameRates(current: Int, displayHz: Int): List<Int> =
        (FPS + current + displayHz).filter { it in 10..240 }.distinct().sorted()

    /** Null when [width] × [height] can be requested, else why not. */
    fun validateCustom(width: Int?, height: Int?): String? = when {
        width == null || height == null -> "Enter a width and a height."
        width !in MIN_WIDTH..MAX_WIDTH -> "Width must be $MIN_WIDTH–$MAX_WIDTH."
        height !in MIN_HEIGHT..MAX_HEIGHT -> "Height must be $MIN_HEIGHT–$MAX_HEIGHT."
        width % 2 != 0 || height % 2 != 0 -> "Use even numbers; video encoders need them."
        else -> null
    }
}
