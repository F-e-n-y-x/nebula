package io.github.f_e_n_y_x.nebula.framegen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import io.github.f_e_n_y_x.nebula.container
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.SliderField
import io.github.f_e_n_y_x.nebula.ui.screens.Segmented
import io.github.f_e_n_y_x.nebula.ui.screens.ToggleRow
import io.github.f_e_n_y_x.nebula.ui.screens.deviceResolution
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import io.github.fenyx.nebula.engine.framegen.FramegenConfig
import io.github.fenyx.nebula.engine.framegen.FramegenDll
import io.github.fenyx.nebula.engine.framegen.FramegenKeys
import io.github.fenyx.nebula.engine.framegen.FramegenSelfTestRunner
import io.github.fenyx.nebula.engine.framegen.QualityPreset
import io.github.fenyx.nebula.engine.framegen.SelfTestReport
import io.github.fenyx.nebula.engine.framegen.SelfTestStart
import io.github.fenyx.nebula.engine.framegen.SelfTestState
import io.github.fenyx.nebula.engine.framegen.SelfTestVerdict
import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.framegen.UpscalerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings › Frame generation: the Lossless.dll engine, the device check, the frame generation
 * options (V+'s keys plus multiplier, flow scale, performance model and auto-off) and the
 * decoder-output upscaler.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FramegenSettingsSection() {
    val ctx = LocalContext.current
    val s = Nebula.scale
    val prefs = remember { LegacyPrefs(ctx) }
    val tick by prefs.changes().collectAsState(initial = null)
    val test by FramegenSelfTestRunner.state.collectAsState()
    val scope = rememberCoroutineScope()
    val config = remember(tick, test) { FramegenConfig.from(prefs.prefs.all) }
    val report = remember(tick, test) { FramegenSelfTestRunner.savedReport(ctx) }
    val verdict = report?.verdict ?: SelfTestVerdict.NOT_RUN
    val dll = remember(tick) { FramegenDll.describe(prefs.prefs) }
    val bundled = remember { FramegenDll.hasBundled(ctx) }
    val running = test is SelfTestState.Running
    val streaming by FramegenSelfTestRunner.streaming.collectAsState()
    val queued by FramegenSelfTestRunner.queuedAfterStream.collectAsState()
    val unsupported = verdict == SelfTestVerdict.UNSUPPORTED
    // During a stream (this page over the stream menu) the check is refused; the card offers to
    // run it when the stream ends instead.
    val runTest = {
        val (w, h) = deviceResolution(ctx)
        if (FramegenSelfTestRunner.start(ctx, maxOf(w, h), minOf(w, h)) == SelfTestStart.REFUSED_STREAMING) {
            Toast.makeText(ctx, "Run the device check when you're not streaming.", Toast.LENGTH_LONG).show()
        }
    }
    val runAfterStream = {
        val (w, h) = deviceResolution(ctx)
        FramegenSelfTestRunner.runAfterStream(ctx, maxOf(w, h), minOf(w, h))
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { FramegenDll.importPicked(ctx, uri) } }
            r.onSuccess {
                FramegenSelfTestRunner.clear(ctx)
                Toast.makeText(ctx, "Lossless.dll imported; checking this device…", Toast.LENGTH_SHORT).show()
                runTest()
            }.onFailure { Toast.makeText(ctx, it.message ?: "Couldn't import that file.", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.widthIn(max = s.dp(720)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
        // ---- Engine ----
        SectionTitle("Frame generation engine")
        Card {
            Text("Lossless.dll", style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(
                dll ?: "Not imported. Copy Lossless.dll from your Lossless Scaling folder on a Windows PC and pick it here.",
                style = Nebula.type.label, color = if (dll != null) NebulaColors.textSecondary else NebulaColors.warning,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8)), modifier = Modifier.padding(top = s.dp(8))) {
                NebulaButton(if (dll == null) "Import Lossless.dll" else "Replace", onClick = { pick.launch(arrayOf("application/octet-stream", "application/x-msdownload", "*/*")) }, style = ButtonStyle.Secondary, icon = Icons.Outlined.FileOpen)
                if (bundled && !FramegenDll.isBuiltin(prefs.prefs)) {
                    NebulaButton("Reset to built-in", onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { FramegenDll.resetToBuiltin(ctx) }
                            FramegenSelfTestRunner.clear(ctx)
                        }
                    }, style = ButtonStyle.Ghost, icon = Icons.Outlined.RestartAlt)
                }
            }
            Text(
                "Lossless.dll belongs to Lossless Scaling (THS). Use your own copy; it stays in Nebula's private storage and is never backed up or shared.",
                style = Nebula.type.label, color = NebulaColors.textMuted, modifier = Modifier.padding(top = s.dp(6)),
            )
        }

        // ---- Device check ----
        SelfTestCard(
            test, report, canRun = dll != null && !running, streaming = streaming, queued = queued,
            onRun = runTest, onRunAfterStream = runAfterStream, onCancelQueued = FramegenSelfTestRunner::cancelQueued,
        )

        // ---- Frame generation ----
        SectionTitle("Frame generation", Modifier.padding(top = s.dp(10)))
        Column(Modifier.alpha(if (unsupported) 0.45f else 1f), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
            ToggleRow(
                "Frame generation",
                if (unsupported) "Not supported on this device. ${report?.firstFailure?.detail.orEmpty()}"
                else "Doubles the frame rate on this device: stream at 60 fps for 120 fps on screen. Adds about half a frame of latency.",
                config.enabled && !unsupported,
            ) { on ->
                if (unsupported) {
                    Toast.makeText(ctx, "Frame generation isn't supported on this device.", Toast.LENGTH_SHORT).show()
                    return@ToggleRow
                }
                if (on && dll == null) {
                    Toast.makeText(ctx, "Import Lossless.dll first.", Toast.LENGTH_SHORT).show()
                    return@ToggleRow
                }
                if (on && streaming && verdict != SelfTestVerdict.PASSED && verdict != SelfTestVerdict.SLOW) {
                    // Never checked (or failed) and a stream is running: don't try it on the live stream.
                    Toast.makeText(ctx, "Run the device check when you're not streaming. Use \"Check when the stream ends\" above.", Toast.LENGTH_LONG).show()
                    return@ToggleRow
                }
                prefs.put(FramegenKeys.ENABLED, on)
                // First enable on this device (or after a DLL / engine change): check it now.
                if (on && verdict == SelfTestVerdict.NOT_RUN && !running) runTest()
            }
            Labeled("Multiplier", "V+'s pipeline generates one frame between each pair: 2×. 3× needs a multi-frame output path it doesn't have yet.") {
                Segmented(FramegenConfig.SUPPORTED_MULTIPLIERS.map { "$it×" to it }, config.multiplier) { prefs.put(FramegenKeys.MULTIPLIER, it.toString()) }
            }
            ToggleRow(
                "Weak-network frame fill",
                "During packet loss or uneven delivery, fill gaps with generated frames to hold the target rate. Works at any stream fps.",
                config.adaptive,
            ) { prefs.put(FramegenKeys.ADAPTIVE, it) }
            Labeled("Quality", "The resolution frame generation works at, as a share of the stream. Lower is faster; the result is upscaled with FSR 1.") {
                Segmented(
                    listOf("Performance · 25%" to QualityPreset.PERFORMANCE, "Balanced · 50%" to QualityPreset.BALANCED, "Clarity · 75%" to QualityPreset.CLARITY, "Custom" to QualityPreset.CUSTOM),
                    config.quality,
                ) { prefs.put(FramegenKeys.QUALITY_PRESET, it.id) }
            }
            if (config.quality == QualityPreset.CUSTOM) {
                Slider("Custom render scale", config.customScalePercent, 20..100, 5, "%") { prefs.put(FramegenKeys.CUSTOM_SCALE, it) }
            }
            Slider(
                "Flow scale", config.flowScalePercent, FramegenConfig.MIN_FLOW_SCALE..100, 5, "%",
                help = "Resolution of the motion estimate. 100% is the tested default; lower is lighter but on Adreno often slower.",
            ) { prefs.put(FramegenKeys.FLOW_SCALE, it) }
            ToggleRow(
                "Performance mode",
                "Uses Lossless Scaling's lighter performance model (LSFG 3.1P). Less GPU work, a little more artefacting on fast motion.",
                config.performanceMode,
            ) { prefs.put(FramegenKeys.PERFORMANCE_MODE, it) }
            ToggleRow(
                "Turn off when hot or slow",
                "Stops generating when the device is about to throttle (thermal headroom under 0.2) or can't keep up, and tells you. The stream carries on.",
                config.thermalGuard,
            ) { prefs.put(FramegenKeys.THERMAL_GUARD, it) }
            Slider(
                "Smoothness protection", config.slowThresholdMs, 8..30, 1, "ms",
                help = "Frames that take longer than this to generate are shown without the generated frame. Default 18 ms.",
            ) { prefs.put(FramegenKeys.SLOW_THRESHOLD_MS, it) }
            ToggleRow(
                "Motion cadence fallback",
                "Shows the real frame first. Troubleshooting only; leave off unless motion looks uneven.",
                config.presentRealFirst,
            ) { prefs.put(FramegenKeys.PRESENT_REAL_FIRST, it) }
        }

        // ---- Upscaling ----
        UpscalerSettings(prefs, tick)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelfTestCard(
    test: SelfTestState,
    report: SelfTestReport?,
    canRun: Boolean,
    streaming: Boolean,
    queued: Boolean,
    onRun: () -> Unit,
    onRunAfterStream: () -> Unit,
    onCancelQueued: () -> Unit,
) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    SectionTitle("Device check", Modifier.padding(top = s.dp(10)))
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Can this device run frame generation?", style = Nebula.type.bodyStrong, color = NebulaColors.text)
                Text(
                    when {
                        test is SelfTestState.Running -> "Checking: ${stageLabel(test.stage)}…"
                        queued -> "Runs when this stream ends." + (report?.let { " Last result: ${it.summary}" } ?: "")
                        streaming -> (report?.summary?.let { "$it. " } ?: "") + "Run the device check when you're not streaming: it can't share the GPU with a live stream."
                        else -> report?.summary ?: "Not checked yet. It runs by itself the first time you turn frame generation on."
                    },
                    style = Nebula.type.label, color = NebulaColors.textSecondary,
                )
            }
            Spacer(Modifier.width(s.dp(12)))
            when {
                test is SelfTestState.Running -> CircularProgressIndicator(color = NebulaColors.accentText, strokeWidth = s.dp(2), modifier = Modifier.size(s.dp(22)))
                report != null -> VerdictPill(report.verdict)
            }
        }
        report?.takeIf { test !is SelfTestState.Running }?.let { r ->
            Column(Modifier.padding(top = s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
                if (r.gpu.isNotEmpty()) Text(r.gpu, style = Nebula.type.mono, color = NebulaColors.textMuted)
                r.checks.forEach { c ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            if (c.passed) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                            tint = if (c.passed) NebulaColors.success else NebulaColors.danger, modifier = Modifier.size(s.dp(18)),
                        )
                        Spacer(Modifier.width(s.dp(8)))
                        Column {
                            Text(c.title, style = Nebula.type.label, color = NebulaColors.text)
                            // In full: which requirement failed, with the Vulkan feature or extension name.
                            Text(c.detail, style = Nebula.type.label, color = if (c.passed) NebulaColors.textMuted else NebulaColors.textSecondary)
                        }
                    }
                }
            }
        }
        FlowRow(Modifier.padding(top = s.dp(10)), horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            when {
                streaming && queued -> NebulaButton("Don't check after the stream", onClick = onCancelQueued, style = ButtonStyle.Ghost, icon = Icons.Outlined.Schedule)
                streaming -> NebulaButton("Check when the stream ends", onClick = onRunAfterStream, style = ButtonStyle.Secondary, icon = Icons.Outlined.Schedule)
                else -> NebulaButton(if (report == null) "Run check" else "Run again", onClick = { if (canRun) onRun() }, style = if (canRun) ButtonStyle.Secondary else ButtonStyle.Ghost, icon = Icons.Outlined.Refresh)
            }
            if (report != null && test !is SelfTestState.Running) {
                NebulaButton("Copy details", onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Nebula frame generation device check", report.fullText()))
                    Toast.makeText(ctx, "Device check details copied", Toast.LENGTH_SHORT).show()
                }, style = ButtonStyle.Ghost, icon = Icons.Outlined.ContentCopy)
            }
        }
    }
}

@Composable
private fun VerdictPill(v: SelfTestVerdict) = when (v) {
    SelfTestVerdict.PASSED -> Pill("Supported", color = NebulaColors.success, background = NebulaColors.successTint)
    SelfTestVerdict.SLOW -> Pill("Slow", color = NebulaColors.warning, background = NebulaColors.raised)
    SelfTestVerdict.UNSUPPORTED -> Pill("Not supported", color = NebulaColors.danger, background = NebulaColors.dangerTint)
    SelfTestVerdict.FAILED -> Pill("Failed", color = NebulaColors.danger, background = NebulaColors.dangerTint)
    SelfTestVerdict.NOT_RUN -> Pill("Not checked", color = NebulaColors.textSecondary, background = NebulaColors.raised)
}

private fun stageLabel(stage: String) = when (stage) {
    "environment" -> "Android and CPU"
    "caps" -> "Vulkan features"
    "dll" -> "translating Lossless.dll shaders"
    "benchmark" -> "timing generated frames"
    else -> stage
}

@Composable
private fun UpscalerSettings(prefs: LegacyPrefs, tick: String?) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val cfg = remember(tick) { UpscalerConfig.from(prefs.prefs.all) }
    SectionTitle("Upscaling", Modifier.padding(top = s.dp(10)))
    Labeled(
        "Upscaler",
        "Sharpens or enlarges the decoded picture to this screen's resolution: useful when streaming 1080p to a 1440p screen. " +
            "Used when frame generation isn't running; off keeps the zero-copy video path. Not for HDR streams.",
    ) {
        Segmented(UpscalerMode.entries.map { upscalerShortLabel(it) to it }, cfg.mode) {
            prefs.put(FramegenKeys.UPSCALER, it.id)
            ctx.container.stream.refreshUpscaler()
        }
    }
    Text(
        when (cfg.mode) {
            UpscalerMode.OFF -> "The decoder draws straight to the screen."
            UpscalerMode.SHARPEN -> "Bilinear scaling plus AMD RCAS sharpening. Cheapest; good everywhere."
            UpscalerMode.SGSR1 -> "Qualcomm's single-pass edge-directed upscaler, designed for Adreno. About 1 ms at 1080p→1440p."
            UpscalerMode.FSR1 -> "AMD FSR 1: EASU edge-adaptive upscaling, then RCAS sharpening. Two passes; works on any GPU."
        },
        style = Nebula.type.label, color = NebulaColors.textMuted,
    )
    if (cfg.mode != UpscalerMode.OFF) {
        Slider("Strength", cfg.strengthPercent, 0..100, 5, "%", help = "How much edges are sharpened.") {
            prefs.put(FramegenKeys.UPSCALER_STRENGTH, it)
            ctx.container.stream.refreshUpscaler()
        }
    }
    Text(
        "Temporal upscalers (SGSR 2, FSR 2 and later) need motion vectors and depth from the game, which a video stream doesn't carry.",
        style = Nebula.type.label, color = NebulaColors.textMuted,
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val s = Nebula.scale
    Column(Modifier.fillMaxWidth().background(NebulaColors.surface, RoundedCornerShape(s.dp(12))).padding(horizontal = s.dp(16), vertical = s.dp(12))) { content() }
}

@Composable
private fun Labeled(title: String, help: String, control: @Composable () -> Unit) {
    val s = Nebula.scale
    Column(Modifier.fillMaxWidth().background(NebulaColors.surface, RoundedCornerShape(s.dp(12))).padding(horizontal = s.dp(16), vertical = s.dp(12)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
        Text(help, style = Nebula.type.label, color = NebulaColors.textMuted)
        control()
    }
}

@Composable
private fun Slider(title: String, value: Int, range: IntRange, step: Int, unit: String, help: String? = null, onChange: (Int) -> Unit) {
    val s = Nebula.scale
    Column(Modifier.fillMaxWidth().background(NebulaColors.surface, RoundedCornerShape(s.dp(12))).padding(horizontal = s.dp(16), vertical = s.dp(12))) {
        Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
        help?.let { Text(it, style = Nebula.type.label, color = NebulaColors.textMuted) }
        SliderField(
            label = title, value = value.toFloat(), range = range.first.toFloat()..range.last.toFloat(), step = step.toFloat(), unit = unit,
            onValueChange = { onChange(it.toInt().coerceIn(range)) },
        )
    }
}

