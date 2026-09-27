package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.Resolution

/** One size in the live resolution picker. */
data class ResolutionOption(val label: String, val resolution: Resolution, val kind: Kind) {
    enum class Kind { NATIVE, PRESET, CUSTOM }

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
