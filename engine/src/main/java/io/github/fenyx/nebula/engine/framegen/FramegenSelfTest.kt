package io.github.fenyx.nebula.engine.framegen

import org.json.JSONArray
import org.json.JSONObject

/** Outcome of the frame generation device check. */
enum class SelfTestVerdict {
    /** Never run, or run for another device, DLL or engine build. */
    NOT_RUN,
    /** Everything works and one generated frame fits comfortably in a 60 fps frame. */
    PASSED,
    /** Works, but generated frames take most of the frame budget; expect fallbacks. */
    SLOW,
    /** The device lacks something frame generation needs (Android, ABI or Vulkan feature). */
    UNSUPPORTED,
    /** The device should work but the engine, the DLL or the benchmark failed. */
    FAILED,
}

data class SelfTestCheck(val id: String, val title: String, val passed: Boolean, val detail: String)

/** Frame-time numbers from the synthetic LSFG benchmark, in milliseconds. */
data class BenchmarkResult(
    val model: String,
    val width: Int,
    val height: Int,
    val frames: Int,
    val initMs: Long,
    val medianMs: Double,
    val p95Ms: Double,
    val maxMs: Double,
) {
    companion object {
        /** Parses the native "ok model=3.1 size=640x360 ... p95_ms=8.1" line; null on "error=...". */
        fun parse(line: String?): BenchmarkResult? {
            if (line == null || !line.startsWith("ok")) return null
            val kv = keyValues(line)
            val size = kv["size"]?.split('x')
            return BenchmarkResult(
                model = kv["model"] ?: "?",
                width = size?.getOrNull(0)?.toIntOrNull() ?: 0,
                height = size?.getOrNull(1)?.toIntOrNull() ?: 0,
                frames = kv["frames"]?.toIntOrNull() ?: 0,
                initMs = kv["init_ms"]?.toLongOrNull() ?: 0,
                medianMs = kv["median_ms"]?.toDoubleOrNull() ?: return null,
                p95Ms = kv["p95_ms"]?.toDoubleOrNull() ?: return null,
                maxMs = kv["max_ms"]?.toDoubleOrNull() ?: 0.0,
            )
        }
    }
}

/** Parses "a=1 b=two" into a map; tokens without '=' are ignored. */
internal fun keyValues(line: String): Map<String, String> =
    line.trim().split(Regex("\\s+")).mapNotNull { t -> t.indexOf('=').takeIf { it > 0 }?.let { t.substring(0, it) to t.substring(it + 1) } }.toMap()

/** What the self-test measured, with a verdict; persisted as JSON under [FramegenKeys.SELF_TEST_REPORT]. */
data class SelfTestReport(
    val verdict: SelfTestVerdict,
    val checks: List<SelfTestCheck>,
    val benchmark: BenchmarkResult?,
    val gpu: String,
    val fingerprint: String,
    val finishedAtMs: Long,
) {
    val summary: String
        get() = when (verdict) {
            SelfTestVerdict.PASSED -> "Supported" + (benchmark?.let { " · %.1f ms per generated frame".format(it.p95Ms) } ?: "")
            SelfTestVerdict.SLOW -> "Supported but slow" + (benchmark?.let { " · %.1f ms per generated frame".format(it.p95Ms) } ?: "")
            SelfTestVerdict.UNSUPPORTED -> "Not supported on this device" + (firstFailure?.let { ": ${it.title}" } ?: "")
            SelfTestVerdict.FAILED -> "Check failed" + (firstFailure?.let { ": ${it.detail}" } ?: "")
            SelfTestVerdict.NOT_RUN -> "Not checked yet"
        }

    val firstFailure: SelfTestCheck? get() = checks.firstOrNull { !it.passed }

    fun toJson(): String = JSONObject().apply {
        put("verdict", verdict.name)
        put("gpu", gpu)
        put("fingerprint", fingerprint)
        put("finishedAtMs", finishedAtMs)
        put("checks", JSONArray().apply {
            checks.forEach { c -> put(JSONObject().put("id", c.id).put("title", c.title).put("passed", c.passed).put("detail", c.detail)) }
        })
        benchmark?.let { b ->
            put("benchmark", JSONObject().put("model", b.model).put("width", b.width).put("height", b.height).put("frames", b.frames)
                .put("initMs", b.initMs).put("medianMs", b.medianMs).put("p95Ms", b.p95Ms).put("maxMs", b.maxMs))
        }
    }.toString()

    companion object {
        fun fromJson(json: String?): SelfTestReport? = runCatching {
            val o = JSONObject(json ?: return null)
            val checks = o.getJSONArray("checks").let { a ->
                (0 until a.length()).map { i -> a.getJSONObject(i).let { SelfTestCheck(it.getString("id"), it.getString("title"), it.getBoolean("passed"), it.getString("detail")) } }
            }
            val bench = o.optJSONObject("benchmark")?.let { b ->
                BenchmarkResult(b.getString("model"), b.getInt("width"), b.getInt("height"), b.getInt("frames"), b.getLong("initMs"),
                    b.getDouble("medianMs"), b.getDouble("p95Ms"), b.getDouble("maxMs"))
            }
            SelfTestReport(SelfTestVerdict.valueOf(o.getString("verdict")), checks, bench, o.optString("gpu"), o.getString("fingerprint"), o.optLong("finishedAtMs"))
        }.getOrNull()

        /** The verdict that applies now: a report for another device, DLL or engine counts as not run. */
        fun verdictFor(json: String?, currentFingerprint: String): SelfTestVerdict {
            val r = fromJson(json) ?: return SelfTestVerdict.NOT_RUN
            return if (r.fingerprint == currentFingerprint) r.verdict else SelfTestVerdict.NOT_RUN
        }
    }
}

/** What the self-test process could see before touching Vulkan. */
data class SelfTestEnvironment(val sdk: Int, val abis: List<String>, val nativeLoaded: Boolean)

/**
 * Turns the raw probe outputs into checks and a verdict. Pure, so the rules are unit-tested:
 * the Android / ABI / Vulkan requirements come from the roadmap (Vulkan 1.1, AHB import, YCbCr
 * sampling, robustness2 null descriptors), the DLL must parse and translate, and one generated
 * frame must fit a 60 fps input frame ([FRAME_BUDGET_MS]).
 */
object SelfTestEvaluator {
    /** One input frame at 60 fps: LSFG must finish a generated frame within it. */
    const val FRAME_BUDGET_MS = 1000.0 / 60.0
    /** Above this share of the budget there is little headroom left: verdict SLOW. */
    const val SLOW_SHARE = 0.75
    /** Above this share LSFG cannot keep up at all: verdict FAILED. */
    const val FAIL_SHARE = 1.25

    fun evaluate(
        env: SelfTestEnvironment,
        caps: String?,
        dllProbe: String?,
        benchmark: String?,
        fingerprint: String,
        nowMs: Long,
        crashedAt: String? = null,
    ): SelfTestReport {
        val checks = mutableListOf<SelfTestCheck>()
        fun add(id: String, title: String, ok: Boolean, detail: String) { checks += SelfTestCheck(id, title, ok, detail) }

        add("android", "Android 10 or newer", env.sdk >= 29, "API ${env.sdk}")
        add("abi", "64-bit ARM (arm64-v8a)", "arm64-v8a" in env.abis, env.abis.joinToString())
        add("native", "Frame generation engine library", env.nativeLoaded, if (env.nativeLoaded) "loaded" else "libmoonlight-framegen.so did not load")
        val deviceOk = checks.all { it.passed }

        val kv = caps?.let(::keyValues).orEmpty()
        val gpu = kv["gpu"]?.replace('_', ' ') ?: ""
        if (deviceOk) {
            if (crashedAt == "caps") {
                add("vulkan", "Vulkan 1.1", false, "the Vulkan driver crashed the test process")
            } else {
                val api = kv["api"]?.split('.')?.mapNotNull { it.toIntOrNull() }.orEmpty()
                val vk11 = kv["vulkan"] == "1" && api.size >= 2 && (api[0] > 1 || api[1] >= 1)
                add("vulkan", "Vulkan 1.1", vk11, if (kv["vulkan"] == "1") "Vulkan ${kv["api"]} on ${gpu.ifEmpty { "unknown GPU" }}" else (kv["reason"] ?: "no Vulkan"))
                if (vk11) {
                    val ahb = kv["ahb"] == "1" && kv["external_memory"] == "1" && kv["dedicated"] == "1"
                    add("ahb", "Hardware-buffer import (VK_ANDROID_external_memory_android_hardware_buffer)", ahb, flags(kv, "ahb", "external_memory", "dedicated"))
                    add("ycbcr", "YCbCr video sampling", kv["ycbcr"] == "1", flags(kv, "ycbcr"))
                    add("robustness2", "Null descriptors (VK_EXT_robustness2)", kv["null_descriptor"] == "1", flags(kv, "robustness2", "null_descriptor"))
                }
            }
        }
        val unsupported = checks.any { !it.passed }
        if (unsupported) {
            return SelfTestReport(SelfTestVerdict.UNSUPPORTED, checks, null, gpu, fingerprint, nowMs)
        }

        val dllOk = dllProbe?.startsWith("lossless-dll-ok") == true
        add("dll", "Lossless.dll shaders translate", dllOk, when {
            dllProbe == null -> "no Lossless.dll imported"
            crashedAt == "dll" -> "translating the DLL crashed the test process"
            else -> dllProbe
        })
        if (!dllOk) return SelfTestReport(SelfTestVerdict.FAILED, checks, null, gpu, fingerprint, nowMs)

        val bench = BenchmarkResult.parse(benchmark)
        if (bench == null) {
            add("benchmark", "Generate frames", false, when {
                crashedAt == "benchmark" -> "the benchmark crashed the test process"
                benchmark == null -> "benchmark did not run"
                else -> benchmark.removePrefix("error=")
            })
            return SelfTestReport(SelfTestVerdict.FAILED, checks, null, gpu, fingerprint, nowMs)
        }
        val share = bench.p95Ms / FRAME_BUDGET_MS
        val detail = "%.1f ms median, %.1f ms p95 per generated frame at %dx%d (budget %.1f ms)".format(bench.medianMs, bench.p95Ms, bench.width, bench.height, FRAME_BUDGET_MS)
        add("benchmark", "Generated frame fits a 60 fps frame", share <= FAIL_SHARE, detail)
        val verdict = when {
            share > FAIL_SHARE -> SelfTestVerdict.FAILED
            share > SLOW_SHARE -> SelfTestVerdict.SLOW
            else -> SelfTestVerdict.PASSED
        }
        return SelfTestReport(verdict, checks, bench, gpu, fingerprint, nowMs)
    }

    private fun flags(kv: Map<String, String>, vararg keys: String) = keys.joinToString(" ") { "$it=${kv[it] ?: "?"}" }
}
