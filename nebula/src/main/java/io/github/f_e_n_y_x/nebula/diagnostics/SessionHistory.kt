package io.github.f_e_n_y_x.nebula.diagnostics

import android.content.Context
import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What one stream looked like, for comparing the last few sessions. */
data class SessionSummary(
    val startedAtMs: Long,
    val durationS: Long,
    val game: String,
    val hostId: String,
    val mode: String,
    val resolution: String,
    val codec: String,
    val decoder: String,
    /** Start to first live frame stats, when it connected. */
    val connectMs: Long?,
    val avgFps: Float,
    val minFps: Float,
    val avgLatencyMs: Float,
    val p95LatencyMs: Float,
    val avgHostMs: Float,
    val avgNetworkMs: Float,
    val avgDecodeMs: Float,
    val avgBitrateMbps: Float,
    val avgLossPercent: Float,
    val maxLossPercent: Float,
    val samples: Int,
    val resolutionChanges: Int,
    /** Null for a normal quit, else why it ended or failed. */
    val endReason: String?,
    val failed: Boolean,
) {
    fun describe(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return buildString {
            append(fmt.format(Date(startedAtMs))).append("  ").append(game).append(" (").append(mode).append(")\n")
            append("  ").append(if (failed) "Failed" else "Played ${formatDuration(durationS)}")
            connectMs?.let { append(" · connected in $it ms") }
            append('\n')
            if (samples > 0) {
                append("  $resolution · $codec · ${decoder.ifEmpty { "decoder unknown" }}\n")
                append("  FPS avg %.1f, min %.0f · latency avg %.1f ms, p95 %.1f ms\n".format(Locale.US, avgFps, minFps, avgLatencyMs, p95LatencyMs))
                append("  host %.1f · network %.1f · decode %.1f ms · %.1f Mbps · loss avg %.2f %%, max %.1f %%\n".format(
                    Locale.US, avgHostMs, avgNetworkMs, avgDecodeMs, avgBitrateMbps, avgLossPercent, maxLossPercent,
                ))
                if (resolutionChanges > 0) append("  $resolutionChanges live resolution change(s)\n")
            }
            endReason?.let { append("  Ended: ").append(it).append('\n') }
        }.trimEnd()
    }
}

fun formatDuration(s: Long): String = when {
    s < 60 -> "${s} s"
    s < 3_600 -> "${s / 60} min ${s % 60} s"
    else -> "${s / 3_600} h ${(s % 3_600) / 60} min"
}

/**
 * Folds the once-a-second stream stats into a [SessionSummary]. Latency percentiles come from a
 * 0.5 ms histogram, so a long session costs a fixed amount of memory.
 */
class SessionAccumulator(
    private val startedAtMs: Long,
    private val startMono: Long,
    private val game: String,
    private val hostId: String,
    private val mode: String,
) {
    private var connectMs: Long? = null
    private var lastMono = startMono
    private var n = 0
    private var fpsSum = 0.0
    private var fpsMin = Float.MAX_VALUE
    private var latSum = 0.0
    private var hostSum = 0.0
    private var netSum = 0.0
    private var decSum = 0.0
    private var rateSum = 0.0
    private var lossSum = 0.0
    private var lossMax = 0f
    private val hist = IntArray(BINS)
    private var resolution = ""
    private var codec = ""
    private var decoder = ""
    private var changes = 0

    val samples: Int get() = n

    fun add(s: StreamStats, nowMono: Long) {
        if (connectMs == null) connectMs = nowMono - startMono
        lastMono = nowMono
        n++
        fpsSum += s.fps
        // The first second after connecting is often partial; don't let it set the minimum.
        if (n > 1 || s.fps > 0) fpsMin = min(fpsMin, s.fps.toFloat())
        latSum += s.latencyMs
        hostSum += s.hostMs
        netSum += s.networkMs
        decSum += s.decodeMs
        rateSum += s.bitrateMbps
        lossSum += s.lossPercent
        lossMax = max(lossMax, s.lossPercent)
        hist[(s.latencyMs / BIN_MS).toInt().coerceIn(0, BINS - 1)]++
        resolution = s.resolution
        codec = s.codec
        if (s.decoder.isNotEmpty()) decoder = s.decoder
    }

    fun resolutionChanged() { changes++ }

    fun finish(nowMono: Long, endReason: String?, failed: Boolean): SessionSummary {
        val end = max(nowMono, lastMono)
        fun avg(v: Double) = if (n == 0) 0f else (v / n).toFloat()
        return SessionSummary(
            startedAtMs = startedAtMs,
            durationS = if (connectMs == null) 0 else (end - startMono - connectMs!!).coerceAtLeast(0) / 1000,
            game = game, hostId = hostId, mode = mode,
            resolution = resolution, codec = codec, decoder = decoder,
            connectMs = connectMs,
            avgFps = avg(fpsSum), minFps = if (fpsMin == Float.MAX_VALUE) 0f else fpsMin,
            avgLatencyMs = avg(latSum), p95LatencyMs = percentile(0.95),
            avgHostMs = avg(hostSum), avgNetworkMs = avg(netSum), avgDecodeMs = avg(decSum),
            avgBitrateMbps = avg(rateSum), avgLossPercent = avg(lossSum), maxLossPercent = lossMax,
            samples = n, resolutionChanges = changes, endReason = endReason, failed = failed,
        )
    }

    /** Upper edge of the bin holding the [q] quantile. */
    private fun percentile(q: Double): Float {
        if (n == 0) return 0f
        val target = kotlin.math.ceil(q * n).toInt().coerceAtLeast(1)
        var seen = 0
        for (i in hist.indices) {
            seen += hist[i]
            if (seen >= target) return (i + 1) * BIN_MS
        }
        return BINS * BIN_MS
    }

    private companion object {
        const val BIN_MS = 0.5f
        const val BINS = 1_000 // up to 500 ms
    }
}

/** The last [KEEP] session summaries, newest first, kept on this device. */
class SessionHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nebula_diagnostics", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val sessions: StateFlow<List<SessionSummary>> = state.asStateFlow()

    @Synchronized
    fun add(s: SessionSummary) {
        val list = (listOf(s) + state.value).take(KEEP)
        state.value = list
        prefs.edit().putString(KEY, JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun clear() {
        state.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    fun describe(): String = state.value.joinToString("\n\n") { it.describe() }.ifEmpty { "No sessions recorded yet." }

    private fun load(): List<SessionSummary> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { runCatching { arr.getJSONObject(it).toSummary() }.getOrNull() }
    }.getOrDefault(emptyList())

    companion object {
        const val KEEP = 5
        private const val KEY = "sessions"
    }
}

private fun SessionSummary.toJson() = JSONObject().apply {
    put("startedAt", startedAtMs); put("duration", durationS); put("game", game); put("host", hostId); put("mode", mode)
    put("resolution", resolution); put("codec", codec); put("decoder", decoder); connectMs?.let { put("connectMs", it) }
    put("avgFps", avgFps.toDouble()); put("minFps", minFps.toDouble()); put("avgLat", avgLatencyMs.toDouble()); put("p95Lat", p95LatencyMs.toDouble())
    put("host_ms", avgHostMs.toDouble()); put("net_ms", avgNetworkMs.toDouble()); put("dec_ms", avgDecodeMs.toDouble())
    put("mbps", avgBitrateMbps.toDouble()); put("lossAvg", avgLossPercent.toDouble()); put("lossMax", maxLossPercent.toDouble())
    put("samples", samples); put("changes", resolutionChanges); endReason?.let { put("end", it) }; put("failed", failed)
}

private fun JSONObject.toSummary() = SessionSummary(
    startedAtMs = getLong("startedAt"), durationS = optLong("duration"), game = optString("game"), hostId = optString("host"),
    mode = optString("mode"), resolution = optString("resolution"), codec = optString("codec"), decoder = optString("decoder"),
    connectMs = if (has("connectMs")) getLong("connectMs") else null,
    avgFps = optDouble("avgFps").toFloat(), minFps = optDouble("minFps").toFloat(),
    avgLatencyMs = optDouble("avgLat").toFloat(), p95LatencyMs = optDouble("p95Lat").toFloat(),
    avgHostMs = optDouble("host_ms").toFloat(), avgNetworkMs = optDouble("net_ms").toFloat(), avgDecodeMs = optDouble("dec_ms").toFloat(),
    avgBitrateMbps = optDouble("mbps").toFloat(), avgLossPercent = optDouble("lossAvg").toFloat(), maxLossPercent = optDouble("lossMax").toFloat(),
    samples = optInt("samples"), resolutionChanges = optInt("changes"),
    endReason = if (has("end")) getString("end") else null, failed = optBoolean("failed"),
)
