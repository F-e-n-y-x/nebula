package com.limelight;

import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.utils.UiHelper;

/**
 * 显示模式管理器
 * 负责从可用的显示模式中选择最佳的刷新率和分辨率模式。
 * 纯计算逻辑，不持有 Activity 引用。
 */
public class DisplayModeManager {

    /**
     * 显示模式选择结果
     */
    public static class DisplayModeResult {
        /** 选中的最佳刷新率 */
        public final float refreshRate;
        /** 应使用的 preferredDisplayModeId，-1 表示不需要切换 */
        public final int preferredModeId;
        /** 是否应该使用 setFrameRate() 替代 preferredDisplayModeId */
        public final boolean useSetFrameRate;
        /** 屏幕宽高比是否与流匹配（仅 Android M 以下使用） */
        public final boolean aspectRatioMatch;

        public DisplayModeResult(float refreshRate, int preferredModeId,
                                 boolean useSetFrameRate, boolean aspectRatioMatch) {
            this.refreshRate = refreshRate;
            this.preferredModeId = preferredModeId;
            this.useSetFrameRate = useSetFrameRate;
            this.aspectRatioMatch = aspectRatioMatch;
        }
    }

    /**
     * 判断刷新率是否与目标帧率精确匹配（在 +3 范围内）
     */
    public static boolean isRefreshRateEqualMatch(float refreshRate, int targetFps) {
        return refreshRate >= targetFps &&
                refreshRate <= targetFps + 3;
    }

    /**
     * 判断刷新率是否为目标帧率的良好匹配（整数倍关系，余数 ≤ 3）
     */
    public static boolean isRefreshRateGoodMatch(float refreshRate, int targetFps) {
        return refreshRate >= targetFps &&
                Math.round(refreshRate) % targetFps <= 3;
    }

    /**
     * 判断是否应降低刷新率以匹配帧率
     */
    public static boolean mayReduceRefreshRate(PreferenceConfiguration prefConfig) {
        return prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS ||
                prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
                (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_BALANCED && prefConfig.reduceRefreshRate);
    }

    /**
     * 判断给定分辨率是否应忽略系统 Insets（刘海/导航栏）
     */
    public static boolean shouldIgnoreInsetsForResolution(Display display, int width, int height) {
        // Never ignore insets for non-native resolutions
        if (!PreferenceConfiguration.isNativeResolution(width, height)) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (Display.Mode candidate : display.getSupportedModes()) {
                // Ignore insets if this is an exact match for the display resolution
                if ((width == candidate.getPhysicalWidth() && height == candidate.getPhysicalHeight()) ||
                        (height == candidate.getPhysicalWidth() && width == candidate.getPhysicalHeight())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 从给定 Display 的所有可用模式中，选择最适合当前流配置的显示模式。
     *
     * @param display    目标显示器
     * @param prefConfig 偏好配置
     * @return DisplayModeResult 包含选中的刷新率和模式信息
     */
    public static DisplayModeResult selectBestDisplayMode(Display display, PreferenceConfiguration prefConfig) {
        float displayRefreshRate;
        int preferredModeId = -1;
        boolean useSetFrameRate = false;
        boolean aspectRatioMatch = false;

        // On M, we can explicitly set the optimal display mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode bestMode = display.getMode();
            boolean isNativeResolutionStream = PreferenceConfiguration.isNativeResolution(prefConfig.width, prefConfig.height);
            boolean refreshRateIsGood = isRefreshRateGoodMatch(bestMode.getRefreshRate(), prefConfig.fps);
            boolean refreshRateIsEqual = isRefreshRateEqualMatch(bestMode.getRefreshRate(), prefConfig.fps);

            LimeLog.info("Current display mode: " + bestMode.getPhysicalWidth() + "x" +
                    bestMode.getPhysicalHeight() + "x" + bestMode.getRefreshRate());

            for (Display.Mode candidate : display.getSupportedModes()) {
                boolean refreshRateReduced = candidate.getRefreshRate() < bestMode.getRefreshRate();
                boolean resolutionReduced = candidate.getPhysicalWidth() < bestMode.getPhysicalWidth() ||
                        candidate.getPhysicalHeight() < bestMode.getPhysicalHeight();
                boolean resolutionFitsStream = candidate.getPhysicalWidth() >= prefConfig.width &&
                        candidate.getPhysicalHeight() >= prefConfig.height;

                LimeLog.info("Examining display mode: " + candidate.getPhysicalWidth() + "x" +
                        candidate.getPhysicalHeight() + "x" + candidate.getRefreshRate());

                if (candidate.getPhysicalWidth() > 4096 && prefConfig.width <= 4096) {
                    // Avoid resolutions options above 4K to be safe
                    continue;
                }

                // On non-4K streams, we force the resolution to never change unless it's above
                // 60 FPS, which may require a resolution reduction due to HDMI bandwidth limitations,
                // or it's a native resolution stream.
                if (prefConfig.width < 3840 && prefConfig.fps <= 60 && !isNativeResolutionStream) {
                    if (display.getMode().getPhysicalWidth() != candidate.getPhysicalWidth() ||
                            display.getMode().getPhysicalHeight() != candidate.getPhysicalHeight()) {
                        continue;
                    }
                }

                // Make sure the resolution doesn't regress unless if it's over 60 FPS
                // where we may need to reduce resolution to achieve the desired refresh rate.
                if (resolutionReduced && !(prefConfig.fps > 60 && resolutionFitsStream)) {
                    continue;
                }

                if (mayReduceRefreshRate(prefConfig) && refreshRateIsEqual && !isRefreshRateEqualMatch(candidate.getRefreshRate(), prefConfig.fps)) {
                    // If we had an equal refresh rate and this one is not, skip it. In min latency
                    // mode, we want to always prefer the highest frame rate even though it may cause
                    // microstuttering.
                    continue;
                } else if (refreshRateIsGood) {
                    // We've already got a good match, so if this one isn't also good, it's not
                    // worth considering at all.
                    if (!isRefreshRateGoodMatch(candidate.getRefreshRate(), prefConfig.fps)) {
                        continue;
                    }

                    if (mayReduceRefreshRate(prefConfig)) {
                        // User asked for the lowest possible refresh rate, so don't raise it if we
                        // have a good match already
                        if (candidate.getRefreshRate() > bestMode.getRefreshRate()) {
                            continue;
                        }
                    } else {
                        // User asked for the highest possible refresh rate, so don't reduce it if we
                        // have a good match already
                        if (refreshRateReduced) {
                            continue;
                        }
                    }
                } else if (!isRefreshRateGoodMatch(candidate.getRefreshRate(), prefConfig.fps)) {
                    // We didn't have a good match and this match isn't good either, so just don't
                    // reduce the refresh rate.
                    if (refreshRateReduced) {
                        continue;
                    }
                } else {
                    // We didn't have a good match and this match is good. Prefer this refresh rate
                    // even if it reduces the refresh rate. Lowering the refresh rate can be beneficial
                    // when streaming a 60 FPS stream on a 90 Hz device. We want to select 60 Hz to
                    // match the frame rate even if the active display mode is 90 Hz.
                }

                bestMode = candidate;
                refreshRateIsGood = isRefreshRateGoodMatch(candidate.getRefreshRate(), prefConfig.fps);
                refreshRateIsEqual = isRefreshRateEqualMatch(candidate.getRefreshRate(), prefConfig.fps);
            }

            LimeLog.info("Best display mode: " + bestMode.getPhysicalWidth() + "x" +
                    bestMode.getPhysicalHeight() + "x" + bestMode.getRefreshRate());

            // Only apply new window layout parameters if we've actually changed the display mode
            if (display.getMode().getModeId() != bestMode.getModeId()) {
                // If we only changed refresh rate and we're on an OS that supports Surface.setFrameRate()
                // use that instead of using preferredDisplayModeId to avoid the possibility of triggering
                // bugs that can cause the system to switch from 4K60 to 4K24 on Chromecast 4K.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || UiHelper.isColorOS() ||
                        display.getMode().getPhysicalWidth() != bestMode.getPhysicalWidth() ||
                        display.getMode().getPhysicalHeight() != bestMode.getPhysicalHeight()) {
                    // Apply the display mode change
                    preferredModeId = bestMode.getModeId();
                } else {
                    LimeLog.info("Using setFrameRate() instead of preferredDisplayModeId due to matching resolution");
                    useSetFrameRate = true;
                }
            } else {
                LimeLog.info("Current display mode is already the best display mode");
            }

            displayRefreshRate = bestMode.getRefreshRate();
        }
        // On L, we can at least tell the OS that we want a refresh rate
        else {
            float bestRefreshRate = display.getRefreshRate();
            for (float candidate : display.getSupportedRefreshRates()) {
                LimeLog.info("Examining refresh rate: " + candidate);

                if (candidate > bestRefreshRate) {
                    // Ensure the frame rate stays around 60 Hz for <= 60 FPS streams
                    if (prefConfig.fps <= 60) {
                        if (candidate >= 63) {
                            continue;
                        }
                    }

                    bestRefreshRate = candidate;
                }
            }

            LimeLog.info("Selected refresh rate: " + bestRefreshRate);
            displayRefreshRate = bestRefreshRate;
            // preferredModeId stays -1 on pre-M; caller should use windowLayoutParams.preferredRefreshRate
        }

        // Check aspect ratio match for pre-M devices
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            android.graphics.Point screenSize = new android.graphics.Point(0, 0);
            display.getSize(screenSize);

            double screenAspectRatio = ((double) screenSize.y) / screenSize.x;
            double streamAspectRatio = ((double) prefConfig.height) / prefConfig.width;
            if (Math.abs(screenAspectRatio - streamAspectRatio) < 0.001) {
                LimeLog.info("Stream has compatible aspect ratio with output display");
                aspectRatioMatch = true;
            }
        }

        return new DisplayModeResult(displayRefreshRate, preferredModeId, useSetFrameRate, aspectRatioMatch);
    }
}
