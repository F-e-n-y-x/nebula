package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.domain.EffectiveStream
import io.github.f_e_n_y_x.nebula.domain.GamePreset
import io.github.f_e_n_y_x.nebula.domain.GamePresets
import io.github.f_e_n_y_x.nebula.domain.HostGameProfile
import io.github.f_e_n_y_x.nebula.domain.HostPower
import io.github.f_e_n_y_x.nebula.domain.HostProfileChange
import io.github.f_e_n_y_x.nebula.domain.PresetResolution
import io.github.f_e_n_y_x.nebula.domain.PresetUpscaler
import io.github.f_e_n_y_x.nebula.domain.ResolutionOptions
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.SliderField
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import io.github.fenyx.nebula.engine.framegen.FramegenConfig
import io.github.fenyx.nebula.engine.framegen.FramegenDll
import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The PC side of Game settings. */
sealed interface HostProfileUi {
    data object Loading : HostProfileUi
    /** The PC has no per-game settings (not Nova, or an older Nova). */
    data object Unsupported : HostProfileUi
    data class Failed(val message: String) : HostProfileUi
    data class Loaded(val profile: HostGameProfile, val saving: Boolean = false, val error: String? = null) : HostProfileUi
}

data class GameSettingsUi(
    val preset: GamePreset = GamePreset.NONE,
    val global: StreamSettings = StreamSettings(),
    val effective: EffectiveStream? = null,
    val host: HostProfileUi = HostProfileUi.Loading,
)

/**
 * Game settings for one game: this device's preset (stored locally) and the game's settings on
 * the PC (Nova's per-game profile, when the PC has one).
 */
class GameSettingsViewModel(private val c: AppContainer, val hostId: String, val gameId: String) : ViewModel() {
    private val host = MutableStateFlow<HostProfileUi>(HostProfileUi.Loading)

    val ui: StateFlow<GameSettingsUi> = combine(
        c.presets.observe(hostId, gameId), c.prefs.streamSettings, c.prefs.videoModeFor(hostId, gameId), host,
    ) { p, g, saved, h ->
        GameSettingsUi(p, g, GamePresets.effective(g, saved, p, c.deviceResolution()), h)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GameSettingsUi())

    init { loadHost() }

    fun loadHost() = viewModelScope.launch {
        host.value = HostProfileUi.Loading
        host.value = c.hosts.gameProfile(hostId, gameId).fold(
            onSuccess = { it?.let { p -> HostProfileUi.Loaded(p) } ?: HostProfileUi.Unsupported },
            onFailure = { HostProfileUi.Failed(it.message ?: "Couldn't reach the PC.") },
        )
    }

    fun update(transform: (GamePreset) -> GamePreset) = viewModelScope.launch { c.presets.update(hostId, gameId, transform) }

    fun reset() = viewModelScope.launch { c.presets.set(hostId, gameId, GamePreset.NONE) }

    fun setHost(change: HostProfileChange) {
        val loaded = host.value as? HostProfileUi.Loaded ?: return
        if (loaded.saving) return
        host.value = loaded.copy(saving = true, error = null)
        viewModelScope.launch {
            host.value = c.hosts.setGameProfile(hostId, gameId, change).fold(
                onSuccess = { HostProfileUi.Loaded(it) },
                onFailure = { loaded.copy(saving = false, error = it.message ?: "The PC didn't save the change.") },
            )
        }
    }
}

/** A choice that means "use the global setting", shown first in each row. */
private fun <T> usingSettings(label: String): Pair<String, T?> = "Settings ($label)" to null

/**
 * Game settings: full-screen on phones, a centred panel on tablets and TV. Every row starts with
 * "Settings (…)", which clears the game's value so the global one applies again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameSettingsDialog(container: AppContainer, hostId: String, gameId: String, gameName: String, onDismiss: () -> Unit) {
    val vm = viewModel(key = "game-settings-$hostId-$gameId") { GameSettingsViewModel(container, hostId, gameId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = Nebula.scale
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(s.dp(20))
        Column(
            Modifier.padding(s.dp(16)).widthIn(max = s.dp(760)).fillMaxWidth()
                .background(NebulaColors.panel, shape).border(1.dp, NebulaColors.border, shape)
                .verticalScroll(rememberScrollState()).padding(s.dp(22)),
            verticalArrangement = Arrangement.spacedBy(s.dp(22)),
        ) {
            Column {
                SectionTitle("Game settings", Modifier.padding(bottom = 0.dp))
                Text(gameName, style = Nebula.type.heading, color = NebulaColors.text)
                Text(
                    "Used every time this game starts. Anything left on Settings follows your global settings.",
                    style = Nebula.type.label, color = NebulaColors.textMuted,
                )
            }
            DevicePresetSection(container, ui, vm, hostId, gameId, first)
            HostSection(ui.host, vm)
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(10))) {
                NebulaButton("Done", onClick = onDismiss, style = ButtonStyle.Primary)
                if (!ui.preset.isEmpty) NebulaButton("Reset this device's settings", onClick = { vm.reset() }, style = ButtonStyle.Ghost, icon = Icons.Outlined.RestartAlt)
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun SideHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, help: String) {
    val s = Nebula.scale
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = NebulaColors.accent, modifier = Modifier.size(s.dp(20)))
        Spacer(Modifier.width(s.dp(8)))
        Column {
            Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(help, style = Nebula.type.label, color = NebulaColors.textMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevicePresetSection(container: AppContainer, ui: GameSettingsUi, vm: GameSettingsViewModel, hostId: String, gameId: String, first: FocusRequester) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val p = ui.preset
    val g = ui.global
    val (dw, dh) = remember { container.deviceResolution() }
    val nativeHz = remember { io.github.fenyx.nebula.engine.DisplayRefresh.nativeHz(ctx) }
    val post = remember {
        val all = FramegenDll.prefs(ctx).all
        FramegenConfig.from(all).enabled to UpscalerConfig.from(all).mode.id
    }
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(20))) {
        SideHeader(Icons.Outlined.PhoneAndroid, "On this device", "Only this device streams with these.")
        ui.effective?.let { e ->
            val line = "Streams at ${e.resolution.value.width}×${e.resolution.value.height} · ${e.fps.value} fps · ${GamePresets.formatMbps(e.bitrateKbps.value)} · ${GamePresets.codecLabel(e.codec.value)}"
            Column {
                Text(line, style = Nebula.type.body, color = NebulaColors.text)
                Text(
                    "Size ${GamePresets.sourceLabel(e.resolution.source)} · frame rate ${GamePresets.sourceLabel(e.fps.source)} · bitrate ${GamePresets.sourceLabel(e.bitrateKbps.source)}",
                    style = Nebula.type.label, color = NebulaColors.textMuted,
                )
            }
        }

        val screenSteps = remember(dw, dh) {
            ResolutionOptions.SCREEN_SCALES.filter { it.first < 100 }.mapNotNull { (pct, f) ->
                val r = ResolutionOptions.scaled(Resolution(dw, dh), f)
                if (minOf(r.width, r.height) < ResolutionOptions.MIN_HEIGHT) null else "$pct% ${r.width}×${r.height}" to (PresetResolution.Screen(pct) as PresetResolution)
            }
        }
        val custom = remember { customResolutions(ctx).map { (w, h) -> "${w}×$h" to (PresetResolution.Fixed(w, h) as PresetResolution) } }
        val globalRes = if (g.resolution.width > 0) "${g.resolution.width}×${g.resolution.height}" else "Native"
        Setting("Resolution", "The percentages keep this screen's shape, so the same choice suits your other devices.") {
            Column(verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
                Text("This screen", style = Nebula.type.label, color = NebulaColors.textMuted)
                Segmented(
                    listOf(usingSettings<PresetResolution>(globalRes), "Native ${dw}×$dh" to PresetResolution.Native) + screenSteps,
                    p.resolution,
                    modifier = Modifier.focusRequester(first),
                ) { r -> vm.update { it.copy(resolution = r) } }
                Text("Standard", style = Nebula.type.label, color = NebulaColors.textMuted)
                Segmented(
                    listOf<Pair<String, PresetResolution?>>(
                        "720p" to PresetResolution.Fixed(1280, 720), "1080p" to PresetResolution.Fixed(1920, 1080),
                        "1440p" to PresetResolution.Fixed(2560, 1440), "4K" to PresetResolution.Fixed(3840, 2160),
                    ) + custom,
                    p.resolution,
                ) { r -> vm.update { it.copy(resolution = r) } }
            }
        }
        Setting("Frame rate", "Native is this screen's highest refresh rate ($nativeHz Hz).") {
            Segmented(
                listOf(usingSettings<Int>("${g.fps}")) + io.github.fenyx.nebula.engine.DisplayRefresh.frameRateChoices(nativeHz).map { it.first to (it.second as Int?) },
                p.fps,
            ) { f -> vm.update { it.copy(fps = f) } }
        }
        Setting("Bitrate", "Higher looks sharper; lower copes better with weak Wi-Fi.") {
            Column(verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
                Segmented(listOf(usingSettings<Int>(GamePresets.formatMbps(g.bitrateKbps)), "For this game" to (p.bitrateKbps ?: g.bitrateKbps)), p.bitrateKbps) { b ->
                    vm.update { it.copy(bitrateKbps = b) }
                }
                p.bitrateKbps?.let { kbps ->
                    SliderField(
                        label = "Bitrate for this game", value = kbps / 1000f, range = 5f..150f, step = 5f, unit = "Mbps",
                        onValueChange = { v -> vm.update { it.copy(bitrateKbps = (v * 1000).roundToInt()) } },
                    )
                }
            }
        }
        Setting("Video codec", "Auto picks HEVC when both sides support it.") {
            Segmented(
                listOf(usingSettings<VideoCodec>(GamePresets.codecLabel(g.codec).removeSuffix(" codec")),
                    "Auto" to VideoCodec.AUTO, "HEVC" to VideoCodec.HEVC, "H.264" to VideoCodec.H264, "AV1" to VideoCodec.AV1),
                p.codec,
            ) { v -> vm.update { it.copy(codec = v) } }
        }
        Setting("Screen on the PC", "What Play starts with. Both buttons stay on the game's page.") {
            Segmented(
                listOf(usingSettings<DisplayMode>(modeShort(g.defaultMode)), "Virtual display" to DisplayMode.VIRTUAL, "Desktop (Mirror)" to DisplayMode.MIRROR),
                p.displayMode,
            ) { m -> vm.update { it.copy(displayMode = m) } }
        }
        Setting("Frame generation", "Doubles the frames shown on this device. It adds a little delay.") {
            Segmented(listOf(usingSettings<Boolean>(if (post.first) "On" else "Off"), "On" to true, "Off" to false), p.frameGen) { v ->
                vm.update { it.copy(frameGen = v) }
            }
        }
        Setting("Upscaler", "Sharpens or upscales the video on this device when frame generation is off.") {
            Segmented(
                listOf(usingSettings<String>(PresetUpscaler.label(post.second))) + PresetUpscaler.ALL.map { PresetUpscaler.label(it) to (it as String?) },
                p.upscaler,
            ) { v -> vm.update { it.copy(upscaler = v) } }
        }
        ControlsChoice(ctx, hostId, gameId)
    }
}

/** The game's on-screen controls: a layout or a layout set, or the default layout. */
@Composable
private fun ControlsChoice(ctx: android.content.Context, hostId: String, gameId: String) {
    val store = remember { ControlsStore.get(ctx) }
    val data by store.data.collectAsStateWithLifecycle()
    val lib = remember(data) { store.library(data) }
    val key = ControlsStore.gameKey(hostId, gameId)
    val default = lib.find(data.activeProfileId)?.name ?: "Standard"
    val options = listOf<Pair<String, String?>>("Default ($default)" to null) +
        lib.sets.map { "${it.name} (set)" to it.id } +
        lib.all.map { it.name to it.id }
    Setting("On-screen controls", "The layout, or a set of layouts you switch between, used for this game.") {
        Segmented(options, lib.assignedTo(key)) { id -> store.update { it.assign(key, id) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostSection(state: HostProfileUi, vm: GameSettingsViewModel) {
    val s = Nebula.scale
    if (state == HostProfileUi.Unsupported) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = NebulaColors.textMuted, modifier = Modifier.size(s.dp(18)))
            Spacer(Modifier.width(s.dp(8)))
            Text("This PC has no per-game settings. Updating Nova on it adds a frame rate limit, FSR, a bitrate limit and power mode per game.",
                style = Nebula.type.label, color = NebulaColors.textMuted)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(20))) {
        SideHeader(Icons.Outlined.Computer, "On the PC", "Saved on the PC: they apply whichever device starts this game.")
        when (state) {
            HostProfileUi.Loading -> Text("Reading the PC's settings…", style = Nebula.type.label, color = NebulaColors.textSecondary)
            is HostProfileUi.Failed -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(10))) {
                Text(state.message, style = Nebula.type.label, color = NebulaColors.warning, modifier = Modifier.weight(1f))
                NebulaButton("Try again", onClick = { vm.loadHost() }, style = ButtonStyle.Secondary)
            }
            is HostProfileUi.Loaded -> HostControls(state, vm)
            HostProfileUi.Unsupported -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostControls(state: HostProfileUi.Loaded, vm: GameSettingsViewModel) {
    val s = Nebula.scale
    val p = state.profile
    val edit: (HostProfileChange) -> Unit = { if (p.canEdit) vm.setHost(it) }
    if (!p.canEdit) Pill("This device can't change them. Allow \"Change game settings\" for it on the PC's Devices page.", color = NebulaColors.warning)
    state.error?.let { Text(it, style = Nebula.type.label, color = NebulaColors.warning) }
    if (state.saving) Text("Saving on the PC…", style = Nebula.type.label, color = NebulaColors.textSecondary)
    if (!p.launchSettingsApply) {
        Text("Steam starts this game, so the frame rate limit, FSR and sharpening don't reach it. The bitrate limit and power mode still apply.",
            style = Nebula.type.label, color = NebulaColors.textMuted)
    }
    val fpsChoices = listOf(0, 30, 45, 60, 90, 120, 144).filter { it <= p.maxFpsCap }.let { if (p.fpsCap > 0 && p.fpsCap !in it) it + p.fpsCap else it }
    Setting("Frame rate limit", "Caps how fast the game renders on the PC. A virtual display already caps at the stream's rate.") {
        Segmented(fpsChoices.map { (if (it == 0) "No limit" else "$it") to it }, p.fpsCap) { edit(HostProfileChange.FpsCap(it)) }
    }
    Setting("AMD FSR (Proton)", "Upscales a game running fullscreen below the screen's resolution. 1 is the sharpest.") {
        Segmented(listOf("Off" to 0) + (1..5).map { "$it" to it }, p.fsr) { edit(HostProfileChange.Fsr(it)) }
    }
    ToggleRow("Sharpening (vkBasalt)", "Contrast-adaptive sharpening on the PC, for Vulkan and Proton games.", p.sharpening) { edit(HostProfileChange.Sharpening(it)) }
    val bitrates = listOf(0, 20_000, 40_000, 60_000, 80_000, 100_000, 150_000).let { if (p.bitrateKbps > 0 && p.bitrateKbps !in it) it + p.bitrateKbps else it }
    Setting("Stream bitrate limit", "The most any device streams this game at; a device asking for more gets this.") {
        Segmented(bitrates.map { (if (it == 0) "No limit" else GamePresets.formatMbps(it)) to it }, p.bitrateKbps) { edit(HostProfileChange.BitrateKbps(it)) }
    }
    Setting("Power mode", "Raise the PC's GPU and CPU performance while this game streams, or leave power settings alone.") {
        Segmented(HostPower.entries.map { it.label to it }, p.power) { edit(HostProfileChange.Power(it)) }
    }
}
