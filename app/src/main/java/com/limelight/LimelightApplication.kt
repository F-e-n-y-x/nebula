package com.limelight

import android.app.Application
import android.util.Log

import com.limelight.utils.AppTheme
import com.limelight.binding.crypto.AndroidCryptoProvider
import com.limelight.crash.CrashReporter
import com.limelight.utils.ConfigurationSyncScheduler

/**
 * Custom Application that wires up local crash diagnostics as early as possible.
 * Nebula ships no Firebase or other telemetry.
 */
class LimelightApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppTheme.applyStoredAppTheme(this)
        CrashReporter.install(this)
        ConfigurationSyncScheduler.runNow(this)
        warmUpClientCertificate()
    }

    /**
     * 异步预热客户端证书。
     *
     * 冷启动首次进入 PcView → ComputerManagerService.poll → NvHTTP 时会同步调用
     * `cryptoProvider.getClientCertificate()`，首次使用要么从磁盘加载（数十毫秒
     * 的 IO），要么生成 RSA 2048 密钥对（数百毫秒甚至秒级 CPU）。这段耗时正好卡
     * 在用户最关心的 "PC 卡片何时亮起绿点" 路径上。
     *
     * 把这次访问提前到 Application.onCreate 里、用最低优先级线程做，等用户走到
     * PcView 时证书已经在内存里，`getClientCertificate()` 直接返回缓存。
     *
     * 用低优先级 Thread 而不是协程是为了：
     * - 避免拉起 CoroutineScope / Dispatchers 体系，减少冷启动模块加载
     * - 显式 `MIN_PRIORITY` 让出 CPU 给 UI 线程的首帧绘制
     */
    private fun warmUpClientCertificate() {
        Thread({
            try {
                AndroidCryptoProvider(this).getClientCertificate()
            } catch (t: Throwable) {
                // 预热失败不影响后续真实路径——它会自己重试。
                t.printStackTrace()
            }
        }, "cert-warmup").apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
            start()
        }
    }

    companion object {
        private const val TAG = "LimelightApp"
    }
}
