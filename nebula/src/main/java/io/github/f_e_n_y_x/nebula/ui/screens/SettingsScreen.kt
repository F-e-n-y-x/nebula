package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.BuildConfig
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.SettingsViewModel
import io.github.f_e_n_y_x.nebula.ui.components.NebulaIconButton
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

private enum class SettingsSection(val title: String, val summary: String, val icon: ImageVector, val ready: Boolean) {
    Stream("Stream", "Resolution, frame rate, bitrate, codec", Icons.Outlined.Videocam, true),
    Display("Display & audio", "HDR, scaling, surround, microphone", Icons.Outlined.Tv, false),
    Controls("Controls & input", "Keyboard, mouse, touch, gamepads, on-screen controls", Icons.Outlined.Gamepad, false),
    FrameGen("Frame generation", "Lossless Scaling engine, upscaling", Icons.Outlined.AutoAwesome, false),
    Network("Network", "Packet size, VPN, Wake-on-LAN", Icons.Outlined.NetworkCheck, false),
    About("About", "Version, licenses, credits", Icons.Outlined.Info, true),
}

@Composable
fun SettingsScreen(container: AppContainer, nav: Navigator) {
    val vm = viewModel { SettingsViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val form = Nebula.form
    val s = Nebula.scale
    val twoPane = form.widthDp >= 720
    var open by remember { mutableStateOf(if (twoPane) SettingsSection.Stream else null) }

    Box(Modifier.fillMaxSize().background(NebulaColors.bg)) {
        if (twoPane) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Column(Modifier.width(s.dp(320)).fillMaxHeight().verticalScroll(rememberScrollState()).padding(s.dp(24))) {
                    Text("Settings", style = Nebula.type.title, color = NebulaColors.text)
                    Spacer(Modifier.height(s.dp(20)))
                    SettingsSection.entries.forEach { sec -> SectionRow(sec, sec == open) { open = sec } }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(NebulaColors.border))
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(s.dp(32))) {
                    SectionBody(open ?: SettingsSection.Stream, settings, vm::update)
                }
            }
        } else {
            val sec = open
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(s.dp(20))) {
                if (sec == null) {
                    Text("Settings", style = Nebula.type.title, color = NebulaColors.text)
                    Spacer(Modifier.height(s.dp(20)))
                    SettingsSection.entries.forEach { SectionRow(it, false) { open = it } }
                } else {
                    NebulaIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to settings", { open = null })
                    Spacer(Modifier.height(s.dp(12)))
                    SectionBody(sec, settings, vm::update)
                }
            }
        }
    }
}

@Composable
private fun SectionRow(sec: SettingsSection, selected: Boolean, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = s.dp(3))
            .semantics { this.selected = selected }
            .nebulaClickable(shape, onClick, role = Role.Tab)
            .background(if (selected) NebulaColors.raised else Color.Transparent, shape)
            .padding(horizontal = s.dp(14), vertical = s.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(sec.icon, null, tint = if (selected) NebulaColors.accentText else NebulaColors.textSecondary, modifier = Modifier.size(s.dp(22)))
        Spacer(Modifier.width(s.dp(14)))
        Column(Modifier.weight(1f)) {
            Text(sec.title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(sec.summary, style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NebulaColors.textMuted, modifier = Modifier.size(s.dp(20)))
    }
}

@Composable
private fun SectionBody(sec: SettingsSection, settings: StreamSettings, update: ((StreamSettings) -> StreamSettings) -> Unit) {
    val s = Nebula.scale
    Text(sec.title, style = Nebula.type.title, color = NebulaColors.text)
    Spacer(Modifier.height(s.dp(6)))
    Text(sec.summary, style = Nebula.type.secondary, color = NebulaColors.textSecondary)
    Spacer(Modifier.height(s.dp(24)))
    when (sec) {
        SettingsSection.Stream -> StreamSection(settings, update)
        SettingsSection.About -> AboutSection()
        else -> Column(
            Modifier.widthIn(max = s.dp(640)).fillMaxWidth().background(NebulaColors.surface, RoundedCornerShape(s.dp(14)))
                .border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(14))).padding(s.dp(20)),
        ) {
            Pill("Next update", color = NebulaColors.accentText, background = NebulaColors.accentTint)
            Spacer(Modifier.height(s.dp(12)))
            Text(
                "Every option from Nebula 0.1 moves here, redesigned, as the streaming engine is connected: " + sec.summary.lowercase() + ".",
                style = Nebula.type.body, color = NebulaColors.textSecondary,
            )
        }
    }
}

@Composable
private fun StreamSection(st: StreamSettings, update: ((StreamSettings) -> StreamSettings) -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val (dw, dh) = remember { deviceResolution(ctx) }
    Column(Modifier.widthIn(max = s.dp(680)), verticalArrangement = Arrangement.spacedBy(s.dp(26))) {
        Setting("Resolution", "Virtual display streams at exactly this size.") {
            Segmented(
                listOf("This device ${dw}×$dh" to Resolution.Native, "1080p" to Resolution(1920, 1080), "1440p" to Resolution(2560, 1440), "4K" to Resolution(3840, 2160)),
                st.resolution,
            ) { r -> update { it.copy(resolution = r) } }
        }
        Setting("Frame rate", "120 fps needs a 120 Hz screen on this device.") {
            Segmented(listOf("60 fps" to 60, "90 fps" to 90, "120 fps" to 120), st.fps) { f -> update { it.copy(fps = f) } }
        }
        Setting("Bitrate", "Higher looks sharper; lower copes better with weak Wi-Fi.") {
            var v by remember(st.bitrateKbps) { mutableFloatStateOf(st.bitrateKbps / 1000f) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = v, onValueChange = { v = it }, valueRange = 5f..150f,
                    onValueChangeFinished = { update { it.copy(bitrateKbps = (v * 1000).toInt()) } },
                    colors = SliderDefaults.colors(thumbColor = NebulaColors.text, activeTrackColor = NebulaColors.accent, inactiveTrackColor = NebulaColors.raised),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(s.dp(16)))
                Text("${v.toInt()} Mbps", style = Nebula.type.mono, color = NebulaColors.text, modifier = Modifier.width(s.dp(84)))
            }
        }
        Setting("Video codec", "Auto picks HEVC when both sides support it.") {
            Segmented(listOf("Auto" to VideoCodec.AUTO, "HEVC" to VideoCodec.HEVC, "H.264" to VideoCodec.H264, "AV1" to VideoCodec.AV1), st.codec) { c -> update { it.copy(codec = c) } }
        }
        Setting("Default screen for games", "Used until you choose per game on its page.") {
            Segmented(listOf("Virtual display" to DisplayMode.VIRTUAL, "Desktop (Mirror)" to DisplayMode.MIRROR), st.defaultMode) { m -> update { it.copy(defaultMode = m) } }
        }
    }
}

@Composable
private fun Setting(title: String, help: String, control: @Composable () -> Unit) {
    val s = Nebula.scale
    Column {
        Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
        Text(help, style = Nebula.type.label, color = NebulaColors.textMuted)
        Spacer(Modifier.height(s.dp(12)))
        control()
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val s = Nebula.scale
    val outer = RoundedCornerShape(s.dp(12))
    Row(
        Modifier.horizontalScroll(rememberScrollState())
            .background(NebulaColors.surface, outer).border(1.dp, NebulaColors.controlBorder, outer).padding(s.dp(4)),
        horizontalArrangement = Arrangement.spacedBy(s.dp(4)),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            val shape = RoundedCornerShape(s.dp(9))
            Box(
                Modifier
                    .semantics { this.selected = on }
                    .nebulaClickable(shape, { onSelect(value) }, role = Role.RadioButton)
                    .background(if (on) NebulaColors.accent else Color.Transparent, shape)
                    .padding(horizontal = s.dp(14), vertical = s.dp(10)),
            ) {
                Text(label, style = Nebula.type.label, color = if (on) Color.White else NebulaColors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AboutSection() {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(10)), modifier = Modifier.widthIn(max = s.dp(640))) {
        Text("Nebula ${BuildConfig.VERSION_NAME}", style = Nebula.type.heading, color = NebulaColors.text)
        Text(
            "Nebula is a game streaming client for Nova, Sunshine and GameStream hosts, by Fenyx. Its streaming engine comes from Moonlight (moonlight-stream) and moonlight-vplus by qiin2333, licensed GPL-3.0.",
            style = Nebula.type.body, color = NebulaColors.textSecondary,
        )
        Text("Geist and Geist Mono fonts: SIL Open Font License.", style = Nebula.type.label, color = NebulaColors.textMuted)
        SectionTitle("Privacy", Modifier.padding(top = s.dp(12)))
        Text("No account, no analytics, no tracking. Nebula only talks to the hosts you pair.", style = Nebula.type.body, color = NebulaColors.textSecondary)
    }
}
