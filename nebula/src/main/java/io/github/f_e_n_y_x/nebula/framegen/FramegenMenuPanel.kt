package io.github.f_e_n_y_x.nebula.framegen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.domain.model.PostProcessStats
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
import io.github.fenyx.nebula.engine.framegen.FramegenKeys
import io.github.fenyx.nebula.engine.framegen.FramegenSelfTestRunner
import io.github.fenyx.nebula.engine.framegen.QualityPreset
import io.github.fenyx.nebula.engine.framegen.SelfTestVerdict
import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.framegen.UpscalerMode

/**
 * Stream menu: the Frame generation panel. On/off with the live status, the presented vs received
 * numbers, the model settings and the upscaler. Everything applies to the running stream; the
 * engine rebuilds post-processing on the same connection when a model setting changes. The keys
 * are the Settings page's, so both always agree.
 *
 * The on/off switch pauses and resumes instantly while frame generation is set up for the stream.
 * Otherwise it turns the setting on (live) or explains why it can't: unsupported, no engine, or no
 * device check yet. The check never runs during a stream.
 */
@Composable
fun FramegenMenuSection(
    post: PostProcessStats?,
    receivedFps: Float?,
    prefs: LegacyPrefs,
    onPause: (paused: Boolean, force: Boolean) -> Boolean,
    onOpenSettings: () -> Unit,
) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val tick by prefs.changes().collectAsState(initial = null)
    val queued by FramegenSelfTestRunner.queuedAfterStream.collectAsState()
    val config = remember(tick) { FramegenConfig.from(prefs.prefs.all) }
    val upscaler = remember(tick) { UpscalerConfig.from(prefs.prefs.all) }
    val report = remember(tick) { FramegenSelfTestRunner.savedReport(ctx) }
    val state = framegenPanelState(post, receivedFps, config, upscaler, report?.verdict ?: SelfTestVerdict.NOT_RUN, report?.firstFailure, queued)
    // A refusal or a failed pause, shown in place (a toast would be hidden behind the menu on TV).
    var notice by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Frame generation", Modifier.weight(1f).padding(bottom = 0.dp))
            StatusPill(state)
        }
        ToggleRow("Frame generation", state.detail, state.switchOn) { want ->
            notice = null
            when (state.toggle(want)) {
                FramegenPanelState.Toggle.PAUSE -> if (!onPause(true, false)) notice = "Frame generation can't be paused right now."
                FramegenPanelState.Toggle.RESUME -> if (!onPause(false, false)) notice = "Frame generation can't be resumed right now."
                FramegenPanelState.Toggle.FORCE_RESUME -> if (!onPause(false, true)) notice = "Frame generation can't be resumed right now."
                FramegenPanelState.Toggle.ENABLE -> prefs.put(FramegenKeys.ENABLED, true)
                FramegenPanelState.Toggle.DISABLE -> prefs.put(FramegenKeys.ENABLED, false)
                FramegenPanelState.Toggle.REFUSE_UNSUPPORTED -> notice = "This device can't run frame generation. The full device check is in All frame generation settings."
                FramegenPanelState.Toggle.REFUSE_NO_ENGINE -> notice = "Import Lossless.dll in All frame generation settings first."
                FramegenPanelState.Toggle.REFUSE_NEEDS_CHECK -> notice = if (queued) FramegenPanelState.QUEUED_MESSAGE else FramegenPanelState.NEEDS_CHECK_MESSAGE
            }
        }
        notice?.let { Text(it, style = Nebula.type.label, color = NebulaColors.warning, modifier = Modifier.padding(horizontal = s.dp(4))) }
        if (state.offerCheckAfterStream || queued) {
            CheckAfterStream(queued, onQueue = {
                val (w, h) = deviceResolution(ctx)
                FramegenSelfTestRunner.runAfterStream(ctx, maxOf(w, h), minOf(w, h))
                notice = null
            }, onCancel = { FramegenSelfTestRunner.cancelQueued() })
        }
        state.stats?.let { LiveStats(it) }

        // ---- Model ----
        val dim = if (state.status == FramegenPanelState.Status.UNSUPPORTED) 0.45f else 1f
        Column(Modifier.alpha(dim), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
            Block("Multiplier", "3× needs native work: the pipeline has one generated-frame slot.") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                    Pill("${config.multiplier}×", color = NebulaColors.text, background = NebulaColors.raised)
                    Text("Fixed", style = Nebula.type.label, color = NebulaColors.textMuted)
                }
            }
            Block("Quality", "The resolution frame generation works at, as a share of the stream. Lower is faster.") {
                Segmented(
                    listOf("Performance 25%" to QualityPreset.PERFORMANCE, "Balanced 50%" to QualityPreset.BALANCED, "Clarity 75%" to QualityPreset.CLARITY, "Custom" to QualityPreset.CUSTOM),
                    config.quality,
                ) { prefs.put(FramegenKeys.QUALITY_PRESET, it.id) }
                if (config.quality == QualityPreset.CUSTOM) {
                    SliderField(
                        label = "Custom render scale", value = config.customScalePercent.toFloat(),
                        range = FramegenConfig.MIN_CUSTOM_SCALE.toFloat()..100f, step = 5f, unit = "%",
                        onValueChange = { prefs.put(FramegenKeys.CUSTOM_SCALE, it.toInt().coerceIn(FramegenConfig.MIN_CUSTOM_SCALE, 100)) },
                    )
                }
            }
            Block("Flow scale", "Resolution of the motion estimate. 100% is the tested default; lower is lighter.") {
                SliderField(
                    label = "Flow scale", value = config.flowScalePercent.toFloat(),
                    range = FramegenConfig.MIN_FLOW_SCALE.toFloat()..100f, step = 5f, unit = "%",
                    onValueChange = { prefs.put(FramegenKeys.FLOW_SCALE, it.toInt().coerceIn(FramegenConfig.MIN_FLOW_SCALE, 100)) },
                )
            }
            ToggleRow(
                "Performance mode",
                "Lossless Scaling's lighter model (LSFG 3.1P): less GPU work, a little more artefacting on fast motion.",
                config.performanceMode,
            ) { prefs.put(FramegenKeys.PERFORMANCE_MODE, it) }
        }

        // ---- Upscaler ----
        Upscaler(upscaler, state, prefs, onLockedTap = { notice = "The upscaler is off while frame generation is set up for this stream." })

        Text(
            "Changes apply to this stream. The picture pauses for a moment while frame generation restarts with them.",
            style = Nebula.type.label, color = NebulaColors.textMuted, modifier = Modifier.padding(horizontal = s.dp(4)),
        )
        NebulaButton("All frame generation settings", onClick = onOpenSettings, style = ButtonStyle.Secondary, icon = Icons.Outlined.Tune, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatusPill(state: FramegenPanelState) {
    val (fg, bg) = when (state.tone) {
        FramegenPanelState.Tone.SUCCESS -> NebulaColors.success to NebulaColors.successTint
        FramegenPanelState.Tone.WARNING -> NebulaColors.warning to NebulaColors.raised
        FramegenPanelState.Tone.DANGER -> NebulaColors.danger to NebulaColors.dangerTint
        FramegenPanelState.Tone.NEUTRAL -> NebulaColors.textSecondary to NebulaColors.raised
    }
    Pill(state.label, color = fg, background = bg)
}

@Composable
private fun CheckAfterStream(queued: Boolean, onQueue: () -> Unit, onCancel: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Column(
        Modifier.fillMaxWidth().background(NebulaColors.surface, shape).border(1.dp, NebulaColors.border, shape).padding(horizontal = s.dp(14), vertical = s.dp(12)),
        verticalArrangement = Arrangement.spacedBy(s.dp(10)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(10))) {
            Icon(Icons.Outlined.Schedule, null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(20)))
            Text(
                if (queued) "The device check is queued: it runs when this stream ends." else "The device check doesn't run during a stream. Nebula can run it when this one ends.",
                style = Nebula.type.label, color = NebulaColors.textSecondary, modifier = Modifier.weight(1f),
            )
        }
        if (queued) NebulaButton("Don't check after the stream", onClick = onCancel, style = ButtonStyle.Ghost)
        else NebulaButton("Check when the stream ends", onClick = onQueue, style = ButtonStyle.Secondary, icon = Icons.Outlined.Schedule)
    }
}

@Composable
private fun LiveStats(st: FramegenPanelState.Stats) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    Row(
        Modifier.fillMaxWidth().background(NebulaColors.surface, shape).padding(s.dp(14)),
        horizontalArrangement = Arrangement.spacedBy(s.dp(8)),
    ) {
        Stat("Presented", "%.0f fps".format(st.presentedFps), NebulaColors.success, Modifier.weight(1f))
        Stat("Received", "%.0f fps".format(st.receivedFps), NebulaColors.text, Modifier.weight(1f))
        Stat("Added latency", "+%.1f ms".format(st.addedLatencyMs), NebulaColors.text, Modifier.weight(1.2f), sub = "%.1f frame · est.".format(st.addedLatencyFrames))
        Stat("LSFG", "${st.lsfgMs} ms", NebulaColors.text, Modifier.weight(0.8f))
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier, sub: String? = null) {
    Column(modifier) {
        Text(value, style = Nebula.type.bodyStrong, color = color, maxLines = 1)
        Text(label, style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 1)
        sub?.let { Text(it, style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 1) }
    }
}

@Composable
private fun Upscaler(cfg: UpscalerConfig, state: FramegenPanelState, prefs: LegacyPrefs, onLockedTap: () -> Unit) {
    val s = Nebula.scale
    val locked = state.upscalerLocked
    Block(
        "Upscaler",
        state.upscalerNote,
        leading = if (locked) ({ Icon(Icons.Outlined.Lock, "Locked", tint = NebulaColors.textMuted, modifier = Modifier.size(s.dp(16))) }) else null,
    ) {
        Column(Modifier.alpha(if (locked) 0.45f else 1f), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            // While locked the chips stay focusable (D-pad order doesn't jump) but only explain.
            Segmented(UpscalerMode.entries.map { upscalerShortLabel(it) to it }, if (locked) UpscalerMode.OFF else cfg.mode) {
                if (locked) onLockedTap() else prefs.put(FramegenKeys.UPSCALER, it.id)
            }
            if (!locked && cfg.mode != UpscalerMode.OFF) {
                SliderField(
                    label = "Strength", value = cfg.strengthPercent.toFloat(), range = 0f..100f, step = 5f, unit = "%",
                    onValueChange = { prefs.put(FramegenKeys.UPSCALER_STRENGTH, it.toInt().coerceIn(0, 100)) },
                )
            }
        }
    }
}

internal fun upscalerShortLabel(m: UpscalerMode) = when (m) {
    UpscalerMode.OFF -> "Off"
    UpscalerMode.SHARPEN -> "Sharpen"
    UpscalerMode.SGSR1 -> "SGSR 1"
    UpscalerMode.FSR1 -> "FSR 1"
}

@Composable
private fun Block(title: String, help: String, leading: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Column(
        Modifier.fillMaxWidth().background(NebulaColors.surface, shape).padding(horizontal = s.dp(16), vertical = s.dp(12)),
        verticalArrangement = Arrangement.spacedBy(s.dp(8)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(6))) {
            leading?.invoke()
            Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
        }
        Text(help, style = Nebula.type.label, color = NebulaColors.textMuted)
        content()
    }
}

/**
 * Settings › Frame generation over the running stream ("All frame generation settings"). It's
 * the same page as in Settings, shown on top of the stream so the video surface stays: leaving
 * the stream screen would pause the video, or end the stream with "Disconnect in background".
 * Back returns to the stream menu.
 */
@Composable
fun FramegenSettingsSheet(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val s = Nebula.scale
    val form = Nebula.form
    val side = form.isLandscape || form.isTv || !form.isCompact
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack),
        )
        val shape = if (side) RoundedCornerShape(topStart = s.dp(20), bottomStart = s.dp(20)) else RoundedCornerShape(topStart = s.dp(20), topEnd = s.dp(20))
        Column(
            (if (side) Modifier.align(Alignment.CenterEnd).width(s.dp(if (form.isTv) 720 else 600)).fillMaxHeight() else Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.92f))
                .background(NebulaColors.panel, shape)
                .border(1.dp, NebulaColors.border, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .then(if (side) Modifier.statusBarsPadding() else Modifier)
                .navigationBarsPadding()
                .padding(horizontal = s.dp(22), vertical = s.dp(16)),
            verticalArrangement = Arrangement.spacedBy(s.dp(12)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(12))) {
                NebulaButton("Back", onClick = onBack, style = ButtonStyle.Ghost, icon = Icons.AutoMirrored.Rounded.ArrowBack)
                Icon(Icons.Outlined.AutoAwesome, null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(20)))
                Text("Frame generation", style = Nebula.type.heading, color = NebulaColors.text, modifier = Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                FramegenSettingsSection()
            }
        }
    }
}
