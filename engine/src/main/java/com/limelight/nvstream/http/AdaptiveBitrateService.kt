/*
 * Moonlight for Android - Adaptive Bitrate Service
 *
 * 智能码率调节，移植自 HarmonyOS 版本：
 *   1. 优先让 Sunshine 服务端做码率决策（ABR API feedback 模式）
 *   2. 服务端不支持时回退到客户端本地控制器（PID 风格）
 *
 * 客户端每秒上报网络指标 → 服务端返回码率调整指令 → 客户端执行
 */
package com.limelight.nvstream.http

import com.limelight.LimeLog
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class AdaptiveBitrateService(
    private val nvHttpFactory: () -> NvHTTP,
    private val statsProvider: () -> AbrStats?,
    /** 码率成功调整后的回调（已在 service 线程发到服务端）。仅用于更新本地 prefConfig / UI。*/
    private val onBitrateChanged: (bitrateKbps: Int, reason: String) -> Unit
) {
    data class AbrStats(
        val packetLoss: Float,    // %
        val rttMs: Int,           // 网络 RTT
        val decodeFps: Float,     // 解码 FPS
        val droppedFrames: Int    // 累计丢帧
    )

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "AdaptiveBitrateService").apply { isDaemon = true }
    }
    private var future: ScheduledFuture<*>? = null

    @Volatile private var nvHttp: NvHTTP? = null
    @Volatile var enabled: Boolean = false
        private set
    @Volatile var serverSupported: Boolean = false
        private set
    @Volatile var currentBitrate: Int = 0
        private set

    /** UI 可订阅码率变更事件（在 service 线程上回调，召者需自行 post 到主线程）。*/
    @Volatile var bitrateListener: ((bitrateKbps: Int, reason: String) -> Unit)? = null

    private var initialBitrate: Int = 0
    private var mode: String = MODE_BALANCED
    private var minBitrate: Int = 3000
    private var maxBitrate: Int = 100_000
    private var requestedMin: Int = 0
    private var requestedMax: Int = 0

    /** Reason of the last change ("server", "loss=6.0% emergency", …), null before the first. */
    @Volatile var lastReason: String? = null
        private set

    // 本地 fallback 控制器状态
    private var local = LocalAbrController(MODE_BALANCED, minBitrate, maxBitrate)

    // 服务端启用重试
    private var serverEnableRetries = 0
    private var serverRetryTickCounter = 0

    /**
     * 启动 ABR。会在后台线程探测服务端能力。
     * @param initialBitrate 当前码率（kbps），ABR 围绕此值上下浮动
     */
    fun start(initialBitrate: Int, mode: String, minBitrateOverride: Int = 0, maxBitrateOverride: Int = 0) {
        if (enabled) return
        this.initialBitrate = initialBitrate
        this.currentBitrate = initialBitrate
        this.mode = mode
        val range = resolveRange(mode, initialBitrate, minBitrateOverride, maxBitrateOverride)
        minBitrate = range.first
        maxBitrate = range.second
        // The server applies its own preset for bounds sent as 0; send what the user chose.
        requestedMin = minBitrateOverride.coerceAtLeast(0)
        requestedMax = maxBitrateOverride.coerceAtLeast(0)
        local = LocalAbrController(mode, minBitrate, maxBitrate)
        resetState()
        enabled = true

        executor.execute {
            try {
                val http = nvHttpFactory()
                nvHttp = http
                val caps = http.getAbrCapabilities()
                serverSupported = caps.supported
                if (caps.supported) serverEnableRetries = 1
                LimeLog.info("[ABR] 启动: bitrate=${initialBitrate}kbps, mode=$mode, server=${caps.supported} (v${caps.version})")
            } catch (e: Exception) {
                LimeLog.warning("[ABR] 服务端能力探测失败: ${e.message}")
            }
        }

        // 使用 scheduleWithFixedDelay 而非 scheduleAtFixedRate：
        // Android 进程被 cached 后唤醒时，fixedRate 会"补跑"积压的几百上千次 tick，
        // 而 fixedDelay 只在每次执行完成后再等待 1 秒，避免突发风暴。
        future = executor.scheduleWithFixedDelay({
            try {
                tick()
            } catch (e: Exception) {
                LimeLog.warning("[ABR] tick 异常: ${e.message}")
            }
        }, START_DELAY_SECONDS, 1, TimeUnit.SECONDS)
    }

    /** 用户手动调了码率（如游戏菜单滑块），ABR 同步基准并重置探测状态。*/
    fun notifyManualOverride(kbps: Int) {
        if (!enabled) return
        currentBitrate = kbps
        local.reset(System.currentTimeMillis())
        LimeLog.info("[ABR] 手动覆盖码率 -> ${kbps}kbps")
    }

    fun stop() {
        if (!enabled) return
        enabled = false
        future?.cancel(false)
        future = null

        // 通知服务端关闭并恢复初始码率
        executor.execute {
            try {
                if (serverSupported) {
                    nvHttp?.setAbrMode(AbrConfig(false, 0, 0, MODE_BALANCED))
                }
                if (currentBitrate != initialBitrate) {
                    applyBitrateInternal(initialBitrate, "restore")
                }
            } catch (e: Exception) {
                LimeLog.warning("[ABR] stop 异常: ${e.message}")
            }
            LimeLog.info("[ABR] 停止，恢复码率: ${initialBitrate}kbps")
        }
        executor.shutdown()
    }

    /** Who decides right now: "server", "connecting" (enabling on the host) or "local". */
    val source: String
        get() = when {
            serverSupported && serverEnableRetries == 0 -> "server"
            serverSupported && serverEnableRetries > 0 -> "connecting"
            else -> "local"
        }

    /** 用于性能面板显示当前 ABR 状态。*/
    fun getStatusText(): String {
        if (!enabled) return ""
        return "ABR:$source ${currentBitrate / 1000}M"
    }

    // -----------------------------------------------------------------------
    // 内部
    // -----------------------------------------------------------------------

    private fun resetState() {
        local.reset(0L)
        lastReason = null
        serverEnableRetries = 0
        serverRetryTickCounter = 0
    }

    private fun tick() {
        val http = nvHttp ?: return
        val stats = statsProvider() ?: return

        // 服务端启用惰性重试
        if (serverSupported && serverEnableRetries > 0) {
            if (++serverRetryTickCounter >= SERVER_RETRY_INTERVAL_TICKS) {
                serverRetryTickCounter = 0
                val ok = http.setAbrMode(AbrConfig(true, requestedMin, requestedMax, mode))
                if (ok) {
                    LimeLog.info("[ABR] 服务端 ABR 启用成功（第 $serverEnableRetries 次）")
                    serverEnableRetries = 0
                } else if (++serverEnableRetries > MAX_SERVER_ENABLE_RETRIES) {
                    serverSupported = false
                    LimeLog.warning("[ABR] 服务端重试 $MAX_SERVER_ENABLE_RETRIES 次失败，降级到本地控制器")
                }
            }
        }

        if (serverSupported && serverEnableRetries == 0) {
            tickServer(http, stats)
        } else {
            tickLocal(stats)
        }
    }

    private fun tickServer(http: NvHTTP, stats: AbrStats) {
        val feedback = NetworkFeedback(
            packetLoss = stats.packetLoss,
            rttMs = stats.rttMs,
            decodeFps = stats.decodeFps,
            droppedFrames = stats.droppedFrames,
            currentBitrate = currentBitrate
        )
        val action = http.reportNetworkFeedback(feedback) ?: return
        val newBitrate = action.newBitrate ?: return
        if (newBitrate == currentBitrate) return
        val clamped = newBitrate.coerceIn(minBitrate, maxBitrate)
        applyBitrateInternal(clamped, action.reason ?: "server", source = "server")
    }

    /** 内部统一码率应用：复用缓存的 nvHttp 实例，避免每次新建 OkHttpClient + TLS。*/
    private fun applyBitrateInternal(kbps: Int, reason: String, source: String = "local"): Boolean {
        val http = nvHttp ?: return false
        val from = currentBitrate
        return try {
            if (http.setBitrate(kbps)) {
                currentBitrate = kbps
                lastReason = reason
                LimeLog.info("[ABR][$source] ${from}kbps -> ${kbps}kbps ($reason)")
                onBitrateChanged(kbps, reason)
                try { bitrateListener?.invoke(kbps, reason) } catch (_: Exception) {}
                true
            } else false
        } catch (e: Exception) {
            LimeLog.warning("[ABR] setBitrate 失败: ${e.message}")
            false
        }
    }

    private fun tickLocal(stats: AbrStats) {
        val now = System.currentTimeMillis()
        val step = local.tick(stats, currentBitrate, now) ?: return
        if (applyBitrateInternal(step.bitrateKbps, step.reason, source = "local")) {
            local.applied(step, now)
        }
    }

    companion object {
        const val MODE_QUALITY = "quality"
        const val MODE_BALANCED = "balanced"
        const val MODE_LOW_LATENCY = "lowLatency"

        private const val START_DELAY_SECONDS = 3L
        private const val SERVER_RETRY_INTERVAL_TICKS = 5
        private const val MAX_SERVER_ENABLE_RETRIES = 10

        /**
         * The range ABR moves in: the mode preset (Foundation's numbers) with any bound the user set
         * (above 0) taking its place. Never inverted.
         */
        @JvmStatic
        fun resolveRange(mode: String, initialBitrate: Int, minOverride: Int, maxOverride: Int): Pair<Int, Int> {
            val initial = initialBitrate.coerceAtLeast(500)
            val (presetMin, presetMax) = when (mode) {
                MODE_QUALITY -> maxOf(5000, (initial * 0.5).toInt()) to minOf(150_000, (initial * 1.5).toInt())
                MODE_LOW_LATENCY -> 2000 to (initial * 1.2).toInt()
                else -> maxOf(3000, (initial * 0.3).toInt()) to minOf(150_000, initial * 2)
            }
            val hi = (if (maxOverride > 0) maxOverride else presetMax).coerceAtLeast(500)
            val lo = (if (minOverride > 0) minOverride else presetMin).coerceIn(500, hi)
            return lo to hi
        }
    }
}

/**
 * The client-side fallback used when the host has no ABR: steps down on packet loss (at once above
 * 5 %, after 2 reports above 2 %, after 4 above 0.5 %) and probes up after a run of clean reports,
 * more gently right after a drop. Pure; the caller supplies the clock.
 */
class LocalAbrController(private val mode: String, private val minBitrate: Int, private val maxBitrate: Int) {
    /** A step the controller wants; call [applied] once the host accepted it. */
    data class Step(val bitrateKbps: Int, val reason: String, internal val up: Boolean)

    private var stableSeconds = 0
    private var lossStreak = 0
    private var lastAdjust = 0L
    private var lastUp: Boolean? = null

    /** Forget the stable and loss runs (manual change or restart); [now] counts as the last change. */
    fun reset(now: Long) {
        stableSeconds = 0
        lossStreak = 0
        lastAdjust = now
    }

    /** One report per second. Returns the step to take, or null to hold. */
    fun tick(stats: AdaptiveBitrateService.AbrStats, current: Int, now: Long): Step? {
        val cooldown = if (mode == AdaptiveBitrateService.MODE_LOW_LATENCY) 1500 else 2000
        if (now - lastAdjust < cooldown) return null

        var target = current.toDouble()
        var reason = ""
        val loss = stats.packetLoss.takeIf { it.isFinite() } ?: 0f
        when {
            loss > 5f -> {
                target = current * 0.7
                reason = "loss=%.1f%% emergency".format(loss)
                stableSeconds = 0
                lossStreak++
            }
            loss > 2f -> {
                lossStreak++
                if (lossStreak >= 2) {
                    target = current * 0.9
                    reason = "loss=%.1f%% sustained".format(loss)
                    stableSeconds = 0
                }
            }
            loss > 0.5f -> {
                lossStreak++
                stableSeconds = 0
                if (lossStreak >= 4) {
                    target = current * 0.95
                    reason = "loss=%.1f%% mild".format(loss)
                }
            }
            else -> {
                lossStreak = 0
                stableSeconds++
                val probeThreshold = if (mode == AdaptiveBitrateService.MODE_QUALITY) 3 else 5
                if (stableSeconds >= probeThreshold && current < maxBitrate) {
                    target = current * if (lastUp == false) 1.02 else 1.05
                    reason = "stable ${stableSeconds}s probe"
                    stableSeconds = 0
                }
            }
        }
        val next = target.toInt().coerceIn(minBitrate, maxBitrate)
        return if (next != current) Step(next, reason, next > current) else null
    }

    /** The host accepted [step] at [now]. */
    fun applied(step: Step, now: Long) {
        lastUp = step.up
        lastAdjust = now
    }
}
