package io.github.f_e_n_y_x.nebula.domain.model

/** A concrete stream size and frame rate, as the host is asked for it. */
data class VideoMode(val width: Int, val height: Int, val fps: Int) {
    init {
        require(width > 0 && height > 0 && fps > 0) { "VideoMode needs a positive size and frame rate: ${width}x$height@$fps" }
    }

    val resolution: Resolution get() = Resolution(width, height)

    /** "2560×1440@120", as the switching overlay shows it. */
    val label: String get() = "${width}×$height@$fps"

    /** Storage form, "2560x1440x120". */
    fun encode(): String = "${width}x${height}x$fps"

    companion object {
        /** Parses [encode]'s form; null for anything else. */
        fun decode(s: String?): VideoMode? {
            val p = s?.split('x')?.mapNotNull { it.toIntOrNull() } ?: return null
            if (p.size != 3 || p.any { it <= 0 }) return null
            return VideoMode(p[0], p[1], p[2])
        }
    }
}
