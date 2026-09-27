package com.limelight.utils

import android.content.Context
import android.os.Bundle

/**
 * Nebula collects no analytics. This keeps the old call sites compiling while
 * sending nothing anywhere; it only tracks the local session start for callers
 * that read the current session duration.
 */
class AnalyticsManager private constructor() {

    private var sessionStartTime: Long = 0
    private var isSessionActive = false

    fun startUsageTracking() {
        if (isSessionActive) return
        sessionStartTime = System.currentTimeMillis()
        isSessionActive = true
    }

    fun stopUsageTracking() {
        isSessionActive = false
    }

    fun logGameStreamStart(computerName: String, appName: String?) = Unit

    fun logGameStreamEnd(computerName: String, appName: String?, durationMs: Long) = Unit

    fun logGameStreamEnd(
        computerName: String, appName: String?, effectiveDurationMs: Long,
        decoderMessage: String?, resolutionWidth: Int, resolutionHeight: Int,
        averageEndToEndLatency: Int, averageDecoderLatency: Int
    ) = Unit

    fun logAppLaunch() = Unit

    fun logCustomEvent(eventName: String, parameters: Bundle?) = Unit

    fun setUserProperty(propertyName: String, propertyValue: String) = Unit

    fun isSessionActive(): Boolean = isSessionActive

    fun getCurrentSessionDuration(): Long {
        if (!isSessionActive) return 0
        return System.currentTimeMillis() - sessionStartTime
    }

    fun cleanup() = Unit

    companion object {
        @Volatile
        private var instance: AnalyticsManager? = null

        @Synchronized
        fun getInstance(context: Context): AnalyticsManager {
            return instance ?: AnalyticsManager().also { instance = it }
        }
    }
}
