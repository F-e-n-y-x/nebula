package io.github.f_e_n_y_x.nebula.settings

import io.github.f_e_n_y_x.nebula.domain.model.StreamStats

/**
 * One value the stats overlay can show. [id]s reuse V+'s perf_overlay_display_items values where
 * the two apps share a metric, so a V+ selection carries over.
 */
enum class StatMetric(val id: String, val label: String, val short: String) {
    FPS("render_fps", "Frame rate", "FPS"),
    HOST_FPS("host_fps", "Host frame rate", "Host"),
    ONE_PERCENT_LOW("one_percent_low", "1% low frame rate", "1% low"),
    TOTAL_LATENCY("total_latency", "Total latency", "Latency"),
    HOST_LATENCY("host_latency", "Host latency (capture + encode)", "Host"),
    NETWORK_LATENCY("network_latency", "Network round trip", "Net"),
    JITTER("jitter", "Network jitter", "Jitter"),
    DECODE_LATENCY("decode_latency", "Decode time", "Decode"),
    RENDER_LATENCY("render_latency", "Render time", "Render"),
    BITRATE("bitrate", "Bitrate", "Bitrate"),
    TARGET_BITRATE("target_bitrate", "Target bitrate (adaptive)", "Target"),
    PACKET_LOSS("packet_loss", "Frame loss", "Loss"),
    RESOLUTION("resolution", "Resolution", "Res"),
    CODEC("codec", "Codec and HDR", "Codec"),
    DECODER("decoder", "Decoder", "Decoder"),
    BATTERY("battery", "Battery", "Battery");

    companion object {
        fun fromId(id: String): StatMetric? = entries.firstOrNull { it.id == id }
    }
}

/** Ready-made metric sets. */
enum class StatPreset(val label: String, val metrics: List<StatMetric>) {
    MINIMAL("Minimal", listOf(StatMetric.FPS, StatMetric.TOTAL_LATENCY)),
    GAMER("Gamer", listOf(StatMetric.FPS, StatMetric.ONE_PERCENT_LOW, StatMetric.TOTAL_LATENCY, StatMetric.NETWORK_LATENCY, StatMetric.PACKET_LOSS, StatMetric.BITRATE)),
    NERD("Nerd", StatMetric.entries.toList()),
}

/** Where the overlay sits: the four corners and the four edge midpoints (V+'s values where they exist). */
enum class StatPosition(val id: String, val label: String, val col: Int, val row: Int) {
    TOP_LEFT("top_left", "Top left", 0, 0),
    TOP("top", "Top", 1, 0),
    TOP_RIGHT("top_right", "Top right", 2, 0),
    LEFT("left", "Left", 0, 1),
    RIGHT("right", "Right", 2, 1),
    BOTTOM_LEFT("bottom_left", "Bottom left", 0, 2),
    BOTTOM("bottom", "Bottom", 1, 2),
    BOTTOM_RIGHT("bottom_right", "Bottom right", 2, 2);

    companion object {
        fun fromId(id: String?): StatPosition = entries.firstOrNull { it.id == id } ?: TOP_LEFT

        fun at(col: Int, row: Int): StatPosition? = entries.firstOrNull { it.col == col && it.row == row }

        /**
         * Where a dragged overlay lands: the nearest corner or edge to its centre ([fx], [fy] as
         * 0..1 of the screen). The middle of the screen isn't a slot, so it snaps to the nearer edge.
         */
        fun snap(fx: Float, fy: Float): StatPosition {
            val col = when { fx < 1f / 3 -> 0; fx > 2f / 3 -> 2; else -> 1 }
            val row = when { fy < 1f / 3 -> 0; fy > 2f / 3 -> 2; else -> 1 }
            at(col, row)?.let { return it }
            // Centre cell: go to whichever edge is closest.
            val dx = minOf(fx, 1 - fx)
            val dy = minOf(fy, 1 - fy)
            return if (dy <= dx) (if (fy < 0.5f) TOP else BOTTOM) else (if (fx < 0.5f) LEFT else RIGHT)
        }
    }
}

enum class StatLayout(val id: String, val label: String) {
    /** One compact line (V+'s horizontal). */
    LINE("horizontal", "Line"),
    /** Label/value rows (V+'s vertical). */
    CARD("vertical", "Card"),
    /** Frame-rate and latency sparklines with the chosen values under them. */
    GRAPH("graph", "Graph");

    companion object {
        fun fromId(id: String?): StatLayout = entries.firstOrNull { it.id == id } ?: LINE
    }
}

enum class StatSize(val id: String, val label: String, val scale: Float) {
    SMALL("small", "Small", 0.85f),
    MEDIUM("medium", "Medium", 1f),
    LARGE("large", "Large", 1.3f);

    companion object {
        fun fromId(id: String?): StatSize = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

/**
 * The stats overlay: on/off, metrics, position, layout and size. Background opacity is the shared
 * overlay transparency ([OVERLAY_OPACITY_KEY]), the same one the stream menu sets for every overlay.
 */
data class StatsOverlaySettings(
    val enabled: Boolean = false,
    val metrics: List<StatMetric> = StatPreset.GAMER.metrics,
    val position: StatPosition = StatPosition.TOP_LEFT,
    val layout: StatLayout = StatLayout.LINE,
    val size: StatSize = StatSize.MEDIUM,
    val opacity: Int = DEFAULT_OVERLAY_OPACITY,
) {
    /** The preset these metrics match exactly, if any. */
    val preset: StatPreset? get() = StatPreset.entries.firstOrNull { it.metrics.toSet() == metrics.toSet() }

    companion object {
        const val ENABLED_KEY = "checkbox_enable_perf_overlay"
        const val METRICS_KEY = "perf_overlay_display_items"
        const val POSITION_KEY = "list_perf_overlay_position"
        const val LAYOUT_KEY = "list_perf_overlay_orientation"
        const val SIZE_KEY = "nebula_perf_overlay_size"
        /** dev4's Simple / Full choice; read once when no metric list was saved yet. */
        const val LEGACY_DETAIL_KEY = "nebula_perf_overlay_detail"

        fun read(all: Map<String, *>): StatsOverlaySettings {
            @Suppress("UNCHECKED_CAST")
            val saved = (all[METRICS_KEY] as? Set<String>)?.mapNotNull(StatMetric::fromId)
            val metrics = saved?.takeIf { it.isNotEmpty() }
                ?: if (all[LEGACY_DETAIL_KEY] == "full") StatPreset.NERD.metrics
                else listOf(StatMetric.FPS, StatMetric.BITRATE, StatMetric.TOTAL_LATENCY)
            return StatsOverlaySettings(
                enabled = all[ENABLED_KEY] as? Boolean ?: false,
                // Keep the menu's order, not the set's.
                metrics = StatMetric.entries.filter { it in metrics },
                position = StatPosition.fromId(all[POSITION_KEY] as? String),
                layout = StatLayout.fromId(all[LAYOUT_KEY] as? String),
                size = StatSize.fromId(all[SIZE_KEY] as? String),
                opacity = (all[OVERLAY_OPACITY_KEY] as? Int ?: DEFAULT_OVERLAY_OPACITY).coerceIn(10, 100),
            )
        }

        fun read(p: LegacyPrefs) = read(p.prefs.all)

        fun setEnabled(p: LegacyPrefs, on: Boolean) = p.put(ENABLED_KEY, on)
        fun setPosition(p: LegacyPrefs, v: StatPosition) = p.put(POSITION_KEY, v.id)
        fun setLayout(p: LegacyPrefs, v: StatLayout) = p.put(LAYOUT_KEY, v.id)
        fun setSize(p: LegacyPrefs, v: StatSize) = p.put(SIZE_KEY, v.id)
        fun setMetrics(p: LegacyPrefs, v: Collection<StatMetric>) {
            p.prefs.edit().putStringSet(METRICS_KEY, v.map { it.id }.toSet()).apply()
        }
    }
}

/** One formatted value for the overlay; [battery] is 0–100 or null when unknown. */
fun StatMetric.format(x: StreamStats, battery: Int? = null): String = when (this) {
    StatMetric.FPS -> "${x.fps}"
    StatMetric.HOST_FPS -> "%.0f".format(x.hostFps)
    StatMetric.ONE_PERCENT_LOW -> "%.0f".format(x.onePercentLowFps)
    StatMetric.TOTAL_LATENCY -> "%.1f ms".format(x.latencyMs)
    StatMetric.HOST_LATENCY -> "%.1f ms".format(x.hostMs)
    StatMetric.NETWORK_LATENCY -> "%.0f ms".format(x.networkMs)
    StatMetric.JITTER -> "%.0f ms".format(x.jitterMs)
    StatMetric.DECODE_LATENCY -> "%.1f ms".format(x.decodeMs)
    StatMetric.RENDER_LATENCY -> "%.1f ms".format(x.renderMs)
    StatMetric.BITRATE -> "%.0f Mbps".format(x.bitrateMbps)
    StatMetric.TARGET_BITRATE -> if (x.targetBitrateKbps <= 0) "—" else "%.0f Mbps".format(x.targetBitrateKbps / 1000f) + when (x.abr?.source) {
        null -> ""
        io.github.f_e_n_y_x.nebula.domain.model.AbrSource.HOST -> " ABR"
        io.github.f_e_n_y_x.nebula.domain.model.AbrSource.LOCAL -> " ABR·local"
        io.github.f_e_n_y_x.nebula.domain.model.AbrSource.CONNECTING -> " ABR…"
    }
    StatMetric.PACKET_LOSS -> "%.1f%%".format(x.lossPercent)
    StatMetric.RESOLUTION -> x.resolution
    StatMetric.CODEC -> x.codec
    StatMetric.DECODER -> shortDecoder(x.decoder)
    StatMetric.BATTERY -> battery?.let { "$it%" } ?: "—"
}

/** Label for [format] in the compact line: FPS reads as "120 fps", the rest as "Net 4 ms". */
fun StatMetric.inline(x: StreamStats, battery: Int? = null): String = when (this) {
    StatMetric.FPS -> "${format(x, battery)} fps"
    StatMetric.RESOLUTION, StatMetric.CODEC, StatMetric.BITRATE, StatMetric.TOTAL_LATENCY -> format(x, battery)
    StatMetric.HOST_FPS -> "host ${format(x, battery)} fps"
    StatMetric.ONE_PERCENT_LOW -> "1% ${format(x, battery)}"
    else -> "${short.lowercase()} ${format(x, battery)}"
}

/** "c2.qti.hevc.decoder.low_latency" → "qti.hevc.low_latency": vendor and codec, without the boilerplate. */
fun shortDecoder(name: String): String {
    if (name.isBlank()) return "—"
    val parts = name.removePrefix("c2.").removePrefix("OMX.").split('.').filter { it.lowercase() != "decoder" }
    val s = parts.joinToString(".")
    return if (s.length > 22) s.take(21) + "…" else s
}
