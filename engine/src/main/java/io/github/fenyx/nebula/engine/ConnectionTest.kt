package io.github.fenyx.nebula.engine

import com.limelight.nvstream.http.NvHTTP
import com.limelight.preferences.PreferenceConfiguration
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How good the link looks for streaming. */
enum class ConnectionQuality { EXCELLENT, GOOD, FAIR, POOR }

/** Settings the test suggests for this device and link. */
data class ConnectionSuggestion(val width: Int, val height: Int, val fps: Int, val bitrateKbps: Int, val nativeResolution: Boolean)

/** This device's screen, landscape or portrait. */
data class DisplaySpec(val width: Int, val height: Int, val maxFps: Int) {
    internal val landscapeWidth = max(width, height)
    internal val landscapeHeight = min(width, height)
    internal val normalizedFps = when {
        maxFps >= 115 -> 120
        maxFps >= 85 -> 90
        maxFps >= 58 -> 60
        maxFps >= 45 -> 50
        else -> maxFps.coerceAtLeast(30)
    }
}

/**
 * Result of "Test connection". Before a stream, [throughputMbps] comes from a short HTTPS download
 * and [lossPercent] from the TCP retransmissions Nova saw while sending it (null on hosts that
 * don't report it). During a stream only RTT and jitter are measured again; [lossPercent] is the
 * stream's own recent frame loss and [throughputMbps] is null.
 */
data class ConnectionTestResult(
    val rttMs: Double,
    val jitterMs: Double,
    val lossPercent: Double?,
    val throughputMbps: Double?,
    val quality: ConnectionQuality,
    val suggestion: ConnectionSuggestion?,
    val duringStream: Boolean,
)

/** Why a connection test couldn't run. */
enum class ConnectionTestFailure {
    /** The host refuses the throughput test while any device streams. */
    STREAM_ACTIVE,

    /** Tested too recently; see [ConnectionTestException.retryAfterMs]. */
    RATE_LIMITED,

    /** The host has no network probe (not Nova or Foundation, or turned off). */
    UNSUPPORTED,
    NOT_PAIRED,
    OFFLINE,
    FAILED,
}

class ConnectionTestException(val failure: ConnectionTestFailure, val retryAfterMs: Long = 0, cause: Throwable? = null) :
    IOException(failure.name, cause)

/** Turns measurements into a quality rating and suggested stream settings. Pure. */
object ConnectionAdvisor {
    /** Share of the measured HTTPS throughput a stream may use (video + FEC + audio fit under it). */
    const val SAFE_FRACTION = 0.65
    private const val MIN_KBPS = 500
    private const val MAX_KBPS = 150_000
    private const val STEP_KBPS = 500

    fun quality(throughputMbps: Double?, rttMs: Double, jitterMs: Double, lossPercent: Double?): ConnectionQuality {
        var q = when {
            throughputMbps == null -> when {
                rttMs < 20 -> ConnectionQuality.EXCELLENT
                rttMs < 50 -> ConnectionQuality.GOOD
                rttMs < 100 -> ConnectionQuality.FAIR
                else -> ConnectionQuality.POOR
            }
            throughputMbps >= 80 && rttMs < 20 -> ConnectionQuality.EXCELLENT
            throughputMbps >= 35 && rttMs < 50 -> ConnectionQuality.GOOD
            throughputMbps >= 15 && rttMs < 100 -> ConnectionQuality.FAIR
            else -> ConnectionQuality.POOR
        }
        val loss = lossPercent ?: 0.0
        q = when {
            loss > 5 -> ConnectionQuality.POOR
            loss > 2 -> worst(q, ConnectionQuality.FAIR)
            loss > 0.5 -> worst(q, ConnectionQuality.GOOD)
            else -> q
        }
        if (jitterMs > 15) q = worst(q, next(q))
        return q
    }

    /**
     * The bitrate to use: [SAFE_FRACTION] of [throughputMbps] when measured, else [currentKbps]
     * (a test during a stream), reduced for loss and jitter, in 500 kbps steps. Null when nothing
     * usable is left.
     */
    fun bitrateKbps(throughputMbps: Double?, currentKbps: Int?, jitterMs: Double, lossPercent: Double?): Int? {
        val base = when {
            throughputMbps != null && throughputMbps > 0 -> throughputMbps * 1000 * SAFE_FRACTION
            currentKbps != null && currentKbps > 0 -> currentKbps.toDouble()
            else -> return null
        }
        val loss = lossPercent ?: 0.0
        var kbps = base * when {
            loss > 5 -> 0.5
            loss > 2 -> 0.7
            loss > 0.5 -> 0.85
            else -> 1.0
        }
        if (jitterMs > 20) kbps *= 0.85
        val stepped = floor(kbps.coerceAtMost(MAX_KBPS.toDouble())).toInt() / STEP_KBPS * STEP_KBPS
        return stepped.takeIf { it >= MIN_KBPS }
    }

    /**
     * The largest resolution and frame rate (native aspect, 100 / 75 / 50 %) whose usual bitrate
     * fits [bitrateKbps]; the bitrate is capped at twice what that mode needs.
     */
    fun suggest(bitrateKbps: Int?, display: DisplaySpec): ConnectionSuggestion? {
        val kbps = bitrateKbps ?: return null
        val high = display.normalizedFps
        val standard = min(high, 60)
        val candidates = listOf(1.0 to high, 1.0 to standard, 0.75 to high, 0.75 to standard, 0.5 to high, 0.5 to standard, 0.5 to min(standard, 30)).distinct()
        val evaluated = candidates.map { (scale, fps) ->
            val w = even(display.landscapeWidth, scale)
            val h = even(display.landscapeHeight, scale)
            Evaluated(w, h, fps, PreferenceConfiguration.getDefaultBitrate("${w}x$h", fps.toString()), scale == 1.0)
        }
        val pick = evaluated.firstOrNull { it.idealKbps <= kbps } ?: evaluated.last()
        val ceiling = ceil(pick.idealKbps * 2 / 1000.0).toInt() * 1000
        return ConnectionSuggestion(pick.width, pick.height, pick.fps, min(kbps, ceiling), pick.native)
    }

    /** Average and jitter (mean change between consecutive samples) of round trips, dropping the first (connection setup). */
    fun latency(samplesMs: List<Double>): Pair<Double, Double> {
        val steady = samplesMs.drop(1).ifEmpty { samplesMs }
        if (steady.isEmpty()) return 0.0 to 0.0
        val jitter = if (steady.size < 2) 0.0 else steady.zipWithNext { a, b -> abs(b - a) }.average()
        return steady.average() to jitter
    }

    fun result(
        rttMs: Double,
        jitterMs: Double,
        lossPercent: Double?,
        throughputMbps: Double?,
        currentKbps: Int?,
        display: DisplaySpec,
        duringStream: Boolean,
    ) = ConnectionTestResult(
        rttMs = rttMs,
        jitterMs = jitterMs,
        lossPercent = lossPercent,
        throughputMbps = throughputMbps,
        quality = quality(throughputMbps, rttMs, jitterMs, lossPercent),
        suggestion = suggest(bitrateKbps(throughputMbps, currentKbps, jitterMs, lossPercent), display),
        duringStream = duringStream,
    )

    private data class Evaluated(val width: Int, val height: Int, val fps: Int, val idealKbps: Int, val native: Boolean)

    private fun even(value: Int, scale: Double) = (value * scale).roundToInt().coerceAtLeast(2) and 1.inv()
    private fun worst(a: ConnectionQuality, b: ConnectionQuality) = if (a.ordinal >= b.ordinal) a else b
    private fun next(q: ConnectionQuality) = ConnectionQuality.entries[min(q.ordinal + 1, ConnectionQuality.entries.lastIndex)]
}

/** Parsing of the network probe's Nova additions. */
object ProbeParsing {
    /** `lossPct` of a `/api/network/probe/result` body, or null when absent or malformed. */
    fun lossPercent(body: String?): Double? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            if (!json.has("lossPct")) return null
            json.getDouble("lossPct").takeIf { it.isFinite() && it >= 0 }?.coerceAtMost(100.0)
        }.getOrNull()
    }

    /** Maps a probe error to a failure the UI can explain. */
    fun failureOf(error: Throwable): ConnectionTestException = when (error) {
        is ConnectionTestException -> error
        is NvHTTP.NetworkProbeException -> ConnectionTestException(
            when (error.reason) {
                "stream_active" -> ConnectionTestFailure.STREAM_ACTIVE
                "rate_limited", "busy", "cooldown" -> ConnectionTestFailure.RATE_LIMITED
                "not_paired" -> ConnectionTestFailure.NOT_PAIRED
                "unsupported_version", "invalid_capabilities", "disabled", "http_404", "http_503" -> ConnectionTestFailure.UNSUPPORTED
                else -> ConnectionTestFailure.FAILED
            },
            error.retryAfterMs,
            error,
        )
        is FileNotFoundException -> ConnectionTestException(ConnectionTestFailure.UNSUPPORTED, cause = error)
        is java.net.ConnectException, is java.net.SocketTimeoutException, is java.net.UnknownHostException ->
            ConnectionTestException(ConnectionTestFailure.OFFLINE, cause = error)
        else -> ConnectionTestException(ConnectionTestFailure.FAILED, cause = error)
    }
}
