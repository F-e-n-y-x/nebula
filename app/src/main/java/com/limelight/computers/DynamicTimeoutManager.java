package com.limelight.computers;

import com.limelight.LimeLog;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态超时管理器 - 根据网络类型和历史连接结果动态调整超时时间
 */
public class DynamicTimeoutManager {
    
    /**
     * 基础超时配置
     */
    public static class TimeoutConfig {
        public int connectTimeout;
        public int readTimeout;
        public int stunTimeout;
        
        public TimeoutConfig(int connectTimeout, int readTimeout, int stunTimeout) {
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
            this.stunTimeout = stunTimeout;
        }
        
        @Override
        public String toString() {
            return String.format("TimeoutConfig{connect=%dms, read=%dms, stun=%dms}",
                    connectTimeout, readTimeout, stunTimeout);
        }
    }
    
    // 默认超时配置
    private static final TimeoutConfig DEFAULT_LAN_CONFIG = new TimeoutConfig(3000, 7000, 2000);
    private static final TimeoutConfig DEFAULT_WAN_CONFIG = new TimeoutConfig(8000, 12000, 5000);
    private static final TimeoutConfig DEFAULT_MOBILE_CONFIG = new TimeoutConfig(10000, 15000, 8000);
    private static final TimeoutConfig DEFAULT_UNSTABLE_CONFIG = new TimeoutConfig(15000, 20000, 10000);
    
    // LAN环境中的最快超时 - 用于快速失败
    private static final TimeoutConfig FAST_FAIL_LAN_CONFIG = new TimeoutConfig(1000, 2000, 500);
    // WAN环境中的快速超时
    private static final TimeoutConfig FAST_FAIL_WAN_CONFIG = new TimeoutConfig(3000, 5000, 1500);
    
    private final NetworkDiagnostics networkDiagnostics;
    private final ConcurrentHashMap<String, ConnectionStats> addressStats = new ConcurrentHashMap<>();
    
    private volatile boolean isNetworkUnstable = false;
    private volatile long lastUnstableTime = 0;
    private static final long UNSTABLE_RECOVERY_TIME_MS = 30000; // 30秒后恢复
    
    /**
     * 单个地址的连接统计
     */
    private static class ConnectionStats {
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile long lastSuccessTime = 0;
        private volatile long lastFailureTime = 0;
        private volatile int consecutiveFailures = 0;
        private volatile long averageResponseTimeMs = 0;
        
        public void recordSuccess(long responseTime) {
            successCount.incrementAndGet();
            lastSuccessTime = System.currentTimeMillis();
            consecutiveFailures = 0;
            
            // 更新平均响应时间
            long total = (averageResponseTimeMs * (successCount.get() - 1)) + responseTime;
            averageResponseTimeMs = total / successCount.get();
        }
        
        public void recordFailure() {
            failureCount.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
            consecutiveFailures++;
        }
        
        public double getSuccessRate() {
            int total = successCount.get() + failureCount.get();
            if (total == 0) return 1.0; // 假设新地址成功率为100%
            return (double) successCount.get() / total;
        }
        
        public boolean isHealthy() {
            return getSuccessRate() >= 0.5 && consecutiveFailures < 3;
        }
        
        @Override
        public String toString() {
            return String.format("Stats{success=%d, failure=%d, rate=%.1f%%, consecutive_failures=%d, avg_response=%dms}",
                    successCount.get(), failureCount.get(), getSuccessRate() * 100, consecutiveFailures, averageResponseTimeMs);
        }
    }
    
    public DynamicTimeoutManager(NetworkDiagnostics networkDiagnostics) {
        this.networkDiagnostics = networkDiagnostics;
    }
    
    /**
     * 获取动态超时配置
     */
    public TimeoutConfig getDynamicTimeoutConfig(String address, boolean isLikelyOnline) {
        NetworkDiagnostics.NetworkDiagnosticsSnapshot diagnostics = networkDiagnostics.getLastDiagnostics();
        
        // 如果网络不稳定，使用更长的超时
        if (isNetworkUnstable) {
            if (isNetworkUnstableRecovered()) {
                isNetworkUnstable = false;
                LimeLog.info("Network recovered from unstable state");
            } else {
                LimeLog.info("Network is unstable, using extended timeouts");
                return DEFAULT_UNSTABLE_CONFIG;
            }
        }
        
        // 获取地址的历史统计
        ConnectionStats stats = addressStats.get(address);
        
        // 如果是新地址或健康地址，使用标准配置
        TimeoutConfig baseConfig = getBaseConfig(diagnostics);
        
        // 如果地址不健康，增加超时时间
        if (stats != null && !stats.isHealthy()) {
            LimeLog.warning("Address " + address + " is unhealthy: " + stats);
            return increaseTimeouts(baseConfig, 1.5);
        }
        

        // 对于新地址或未知状态，保持标准配置
        return baseConfig;
    }
    
    /**
     * 获取用于快速查询的超时配置 - 如果失败要快速放弃
     */
    public TimeoutConfig getFastFailConfig() {
        NetworkDiagnostics.NetworkDiagnosticsSnapshot diagnostics = networkDiagnostics.getLastDiagnostics();
        
        switch (diagnostics.networkType) {
            case LAN:
                return FAST_FAIL_LAN_CONFIG;
            case WAN:
            case MOBILE:
                return FAST_FAIL_WAN_CONFIG;
            case VPN:
                // VPN可能有额外延迟，使用WAN配置
                return FAST_FAIL_WAN_CONFIG;
            default:
                return FAST_FAIL_WAN_CONFIG;
        }
    }
    
    /**
     * 获取STUN超时配置
     */
    public int getStunTimeout() {
        TimeoutConfig config = getDynamicTimeoutConfig(null, false);
        return config.stunTimeout;
    }
    
    /**
     * 获取基础超时配置
     */
    private TimeoutConfig getBaseConfig(NetworkDiagnostics.NetworkDiagnosticsSnapshot diagnostics) {
        switch (diagnostics.networkType) {
            case LAN:
                return DEFAULT_LAN_CONFIG;
            case MOBILE:
                return DEFAULT_MOBILE_CONFIG;
            case WAN:
                // 根据质量进一步调整WAN配置
                if (diagnostics.networkQuality == NetworkDiagnostics.NetworkQuality.POOR) {
                    return increaseTimeouts(DEFAULT_WAN_CONFIG, 1.5);
                } else if (diagnostics.networkQuality == NetworkDiagnostics.NetworkQuality.EXCELLENT) {
                    return DEFAULT_LAN_CONFIG; // 优秀的WAN连接可以接近LAN速度
                }
                return DEFAULT_WAN_CONFIG;
            case VPN:
                // VPN通常有额外延迟
                return increaseTimeouts(DEFAULT_WAN_CONFIG, 1.2);
            default:
                return DEFAULT_WAN_CONFIG;
        }
    }
    
    /**
     * 增加超时时间
     */
    private TimeoutConfig increaseTimeouts(TimeoutConfig original, double factor) {
        return new TimeoutConfig(
                (int) (original.connectTimeout * factor),
                (int) (original.readTimeout * factor),
                (int) (original.stunTimeout * factor)
        );
    }
    
    /**
     * 记录连接成功
     */
    public void recordSuccess(String address, long responseTimeMs) {
        ConnectionStats stats = addressStats.computeIfAbsent(address, 
                k -> new ConnectionStats());
        stats.recordSuccess(responseTimeMs);
        
        // 如果成功，清除网络不稳定标记
        isNetworkUnstable = false;
        lastUnstableTime = 0;
        
        LimeLog.info("Connection success for " + address + ": " + stats);
    }
    
    /**
     * 记录连接失败
     */
    public void recordFailure(String address) {
        ConnectionStats stats = addressStats.computeIfAbsent(address, 
                k -> new ConnectionStats());
        stats.recordFailure();
        
        // 如果连续失败多次，标记网络不稳定
        if (stats.consecutiveFailures >= 3) {
            markNetworkUnstable();
        }
        
        LimeLog.warning("Connection failure for " + address + ": " + stats);
    }
    
    /**
     * 标记网络不稳定
     */
    private void markNetworkUnstable() {
        if (!isNetworkUnstable) {
            isNetworkUnstable = true;
            lastUnstableTime = System.currentTimeMillis();
            LimeLog.warning("Network marked as unstable, will extend timeouts");
        }
    }
    
    /**
     * 检查网络是否已从不稳定状态恢复
     */
    private boolean isNetworkUnstableRecovered() {
        return System.currentTimeMillis() - lastUnstableTime >= UNSTABLE_RECOVERY_TIME_MS;
    }
    
    /**
     * 重置所有统计信息
     */
    public void resetStatistics() {
        addressStats.clear();
        isNetworkUnstable = false;
        lastUnstableTime = 0;
        LimeLog.info("Connection statistics reset");
    }
    
    /**
     * 获取地址统计信息（调试用）
     */
    public String getStatisticsDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("DynamicTimeoutManager Statistics:\n");
        for (java.util.Map.Entry<String, ConnectionStats> entry : addressStats.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("  Network unstable: ").append(isNetworkUnstable).append("\n");
        return sb.toString();
    }
}
