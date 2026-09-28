package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import io.github.f_e_n_y_x.nebula.settings.HapticsSettings
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.settings.MotionHold
import io.github.f_e_n_y_x.nebula.settings.MotionSettings
import io.github.f_e_n_y_x.nebula.settings.MotionSource
import io.github.f_e_n_y_x.nebula.settings.RumbleRoute
import io.github.f_e_n_y_x.nebula.settings.RumbleSettings
import io.github.f_e_n_y_x.nebula.settings.StatLayout
import io.github.f_e_n_y_x.nebula.settings.StatMetric
import io.github.f_e_n_y_x.nebula.settings.StatPosition
import io.github.f_e_n_y_x.nebula.settings.StatPreset
import io.github.f_e_n_y_x.nebula.settings.StatSize
import io.github.f_e_n_y_x.nebula.settings.StatsOverlaySettings
import io.github.f_e_n_y_x.nebula.settings.setOverlayOpacity
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.SliderField
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/** Sample numbers for the Settings preview (no stream is running there). */
internal val PreviewStats = StreamStats(
    resolution = "2560×1440", fps = 120, bitrateMbps = 48f, latencyMs = 7.4f, codec = "HEVC", width = 2560, height = 1440,
    receivedFps = 120f, hostFps = 120f, onePercentLowFps = 104f, jitterMs = 1f, lossPercent = 0.1f,
    hostMs = 2.1f, networkMs = 3.2f, decodeMs = 1.6f, renderMs = 2.0f, decoder = "c2.qti.hevc.decoder.low_latency",
)

private val PreviewHistory = List(STATS_HISTORY) { i ->
    PreviewStats.copy(fps = 112 + ((i * 7) % 11), latencyMs = 6.5f + ((i * 5) % 7) * 0.4f)
}

/** A titled group of controls, Nebula's settings card. */
@Composable
internal fun ControlGroup(title: String, help: String? = null, content: @Composable () -> Unit) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        Text(title, style = Nebula.type.label, color = NebulaColors.textSecondary)
        help?.let { Text(it, style = Nebula.type.label, color = NebulaColors.textMuted) }
        content()
    }
}

/** A multi-select chip; the whole chip is one focus target. */
@Composable
internal fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.heightIn(min = s.dp(40)).semantics { selected = on }
            .nebulaClickable(shape, onClick, role = Role.Checkbox)
            .background(if (on) NebulaColors.accentTint else NebulaColors.surface, shape)
            .border(1.dp, if (on) NebulaColors.accent else NebulaColors.controlBorder, shape)
            .padding(horizontal = s.dp(14), vertical = s.dp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.dp(6)),
    ) {
        if (on) Icon(Icons.Rounded.Check, null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(16)))
        Text(label, style = Nebula.type.label, color = if (on) NebulaColors.text else NebulaColors.textSecondary)
    }
}

/**
 * The eight overlay slots as a 3 × 3 grid (the middle is the screen): tap or D-pad to one.
 * Reads as a picture of the screen rather than a list.
 */
@Composable
internal fun PositionGrid(current: StatPosition, onPick: (StatPosition) -> Unit) {
    val s = Nebula.scale
    val outer = RoundedCornerShape(s.dp(12))
    Column(
        Modifier.width(s.dp(228)).aspectRatio(16f / 10f).background(NebulaColors.bg, outer).border(1.dp, NebulaColors.controlBorder, outer).padding(s.dp(6)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        for (row in 0..2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0..2) {
                    val p = StatPosition.at(col, row)
                    if (p == null) {
                        Box(Modifier.size(s.dp(52), s.dp(30)))
                    } else {
                        val on = p == current
                        val shape = RoundedCornerShape(s.dp(8))
                        Box(
                            Modifier.size(s.dp(52), s.dp(30)).semantics { selected = on; contentDescription = p.label }
                                .nebulaClickable(shape, { onPick(p) }, role = Role.RadioButton)
                                .background(if (on) NebulaColors.accent else NebulaColors.raised, shape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(s.dp(18), s.dp(6)).background(if (on) Color.White else NebulaColors.textMuted, RoundedCornerShape(3)))
                        }
                    }
                }
            }
        }
    }
}

/** Every stats-overlay option; used in Settings and in the stream menu's dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatsOverlayControls(prefs: LegacyPrefs, cfg: StatsOverlaySettings, showToggle: Boolean, showPreview: Boolean) {
    val s = Nebula.scale
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(18))) {
        if (showToggle) {
            ToggleRow("Show stats while streaming", "The overlay can also be switched from the stream menu.", cfg.enabled) { StatsOverlaySettings.setEnabled(prefs, it) }
        }
        if (showPreview) {
            Box(
                Modifier.fillMaxWidth().widthIn(max = s.dp(560)).heightIn(min = s.dp(120))
                    .background(Color(0xFF1A1830), RoundedCornerShape(s.dp(14))).padding(s.dp(10)),
                contentAlignment = cfg.position.alignment(),
            ) { StatsPanel(PreviewStats, PreviewHistory, cfg, battery = 76) }
        }
        ControlGroup("Layout") {
            Segmented(StatLayout.entries.map { it.label to it }, cfg.layout) { StatsOverlaySettings.setLayout(prefs, it) }
        }
        ControlGroup("Position", if (Nebula.form.isTv) null else "While streaming you can also long-press the overlay and drag it to a corner or edge.") {
            PositionGrid(cfg.position) { StatsOverlaySettings.setPosition(prefs, it) }
        }
        ControlGroup("Size") {
            Segmented(StatSize.entries.map { it.label to it }, cfg.size) { StatsOverlaySettings.setSize(prefs, it) }
        }
        ControlGroup("Transparency", "Shared with the other stream overlays (keyboard, on-screen controls, mouse bar, float ball).") {
            SliderField(
                label = "Overlay transparency", value = (100 - cfg.opacity).toFloat(), range = 0f..90f, step = 5f, unit = "%",
                onValueChange = { setOverlayOpacity(ctx, 100 - it.toInt()) },
            )
        }
        ControlGroup("Metrics", "Presets, or pick any mix.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                StatPreset.entries.forEach { p ->
                    NebulaButton(p.label, onClick = { StatsOverlaySettings.setMetrics(prefs, p.metrics) }, style = if (cfg.preset == p) ButtonStyle.Primary else ButtonStyle.Secondary)
                }
            }
            FlowRow(Modifier.padding(top = s.dp(4)), horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                StatMetric.entries.forEach { m ->
                    val on = m in cfg.metrics
                    Chip(m.label, on) {
                        val next = if (on) cfg.metrics - m else cfg.metrics + m
                        // At least one metric stays on, or the overlay would be an empty box.
                        if (next.isNotEmpty()) StatsOverlaySettings.setMetrics(prefs, next)
                    }
                }
            }
        }
    }
}

/** The stream menu's stats dialog: the same controls without the preview (the stream is behind it). */
@Composable
internal fun StatsOverlayDialog(prefs: LegacyPrefs, cfg: StatsOverlaySettings, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val first = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        titleContentColor = NebulaColors.text,
        title = { Text("Stats overlay", style = Nebula.type.heading) },
        text = {
            Column(Modifier.heightIn(max = s.dp(460)).verticalScroll(rememberScrollState())) {
                Box(Modifier.focusRequester(first)) { StatsOverlayControls(prefs, cfg, showToggle = true, showPreview = false) }
            }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        },
        confirmButton = { NebulaButton("Done", onClick = onDismiss, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}

/** Gyro passthrough options. [hostNote] explains a host that can't take motion. */
@Composable
internal fun MotionControls(prefs: LegacyPrefs, cfg: MotionSettings, hostNote: String?, phoneHasGyro: Boolean) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(16))) {
        hostNote?.let { Text(it, style = Nebula.type.label, color = NebulaColors.warning) }
        ControlGroup("Source", "The host asks for motion when a game uses it; sensors stay off until then.") {
            Segmented(MotionSource.entries.map { it.label to it }, cfg.source) { MotionSettings.write(prefs, cfg.copy(source = it)) }
            if (cfg.source == MotionSource.PHONE && !phoneHasGyro) {
                Text("This device has no gyroscope.", style = Nebula.type.label, color = NebulaColors.warning)
            }
        }
        if (cfg.source != MotionSource.OFF) {
            if (cfg.source == MotionSource.CONTROLLER) {
                ToggleRow(
                    "Use this device when the controller has no gyro",
                    "Player 1 gets this device's sensors instead. Controller sensors need Android 13 or later.",
                    cfg.phoneFallback,
                ) { MotionSettings.write(prefs, cfg.copy(phoneFallback = it)) }
            }
            ControlGroup("Sensitivity") {
                SliderField(
                    label = "Gyro sensitivity", value = cfg.sensitivity.toFloat(),
                    range = MotionSettings.MIN_SENSITIVITY.toFloat()..MotionSettings.MAX_SENSITIVITY.toFloat(), step = 5f, unit = "%",
                    onValueChange = { MotionSettings.write(prefs, cfg.copy(sensitivity = it.toInt())) },
                )
            }
            ControlGroup("Gyro active", "Aim-while-held: the camera only follows the gyro while the trigger is pressed.") {
                Segmented(MotionHold.entries.map { holdShort(it) to it }, cfg.hold) { MotionSettings.write(prefs, cfg.copy(hold = it)) }
            }
        }
    }
}

private fun holdShort(h: MotionHold) = when (h) {
    MotionHold.ALWAYS -> "Always"
    MotionHold.LEFT_TRIGGER -> "Hold L2"
    MotionHold.RIGHT_TRIGGER -> "Hold R2"
    MotionHold.EITHER_TRIGGER -> "Hold either"
}

/** Host rumble routing and strength (V+'s keys). */
@Composable
internal fun RumbleControls(prefs: LegacyPrefs, cfg: RumbleSettings, hostNote: String?) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(16))) {
        hostNote?.let { Text(it, style = Nebula.type.label, color = NebulaColors.warning) }
        ControlGroup("Game rumble goes to", "Coordinated: the controller, or this device when the controller can't vibrate.") {
            Segmented(RumbleRoute.entries.map { it.label to it }, cfg.route) { prefs.put(RumbleSettings.ROUTE_KEY, it.id) }
        }
        if (cfg.route != RumbleRoute.CONTROLLER) {
            ControlGroup("This device's vibration strength") {
                SliderField(
                    label = "Device rumble strength", value = cfg.deviceStrength.toFloat(), range = 0f..200f, step = 5f, unit = "%",
                    onValueChange = { prefs.put(RumbleSettings.STRENGTH_KEY, it.toInt()) },
                )
            }
        }
    }
}

/** Audio haptics, for the stream menu (Settings shows the V+ rows directly). */
@Composable
internal fun HapticsControls(prefs: LegacyPrefs, cfg: HapticsSettings, note: String?) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(12))) {
        ToggleRow("Audio haptics", "Feel the game's sound as vibration: explosions, engines, footsteps.", cfg.enabled) {
            HapticsSettings.write(prefs, cfg.copy(enabled = it))
        }
        if (cfg.enabled) {
            ControlGroup("Intensity") {
                SliderField(
                    label = "Audio haptics intensity", value = cfg.strength.toFloat(), range = 0f..200f, step = 5f, unit = "%",
                    onValueChange = { HapticsSettings.write(prefs, cfg.copy(strength = it.toInt())) },
                )
            }
            ControlGroup("Output") {
                Segmented(HapticsSettings.ROUTES.map { it.second to it.first }, cfg.route) { HapticsSettings.write(prefs, cfg.copy(route = it)) }
            }
            ControlGroup("Tuned for") {
                Segmented(HapticsSettings.SCENES.map { it.second to it.first }, cfg.scene) { HapticsSettings.write(prefs, cfg.copy(scene = it)) }
            }
        }
        note?.let { Text(it, style = Nebula.type.label, color = NebulaColors.textMuted) }
    }
}
