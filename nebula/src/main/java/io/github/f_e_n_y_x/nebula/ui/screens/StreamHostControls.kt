package io.github.f_e_n_y_x.nebula.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.HostGating
import io.github.f_e_n_y_x.nebula.domain.MicAction
import io.github.f_e_n_y_x.nebula.domain.MicPermission
import io.github.f_e_n_y_x.nebula.domain.MicPolicy
import io.github.f_e_n_y_x.nebula.domain.MicUi
import io.github.f_e_n_y_x.nebula.domain.model.ClipboardMode
import io.github.f_e_n_y_x.nebula.domain.model.ClipboardSendResult
import io.github.f_e_n_y_x.nebula.domain.model.Gate
import io.github.f_e_n_y_x.nebula.domain.model.HostCommands
import io.github.f_e_n_y_x.nebula.domain.model.StreamLink
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.ui.HostActionsViewModel
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/**
 * Mic, clipboard, host commands and Sleep PC for the stream screen. Holds the mic permission flow
 * (rationale → system dialog → settings when blocked), honours "Initial microphone state", and
 * forwards window focus so clipboard reads follow Android 10+ rules.
 */
@Stable
class StreamHostState internal constructor(
    val actions: HostActionsViewModel,
    private val container: AppContainer,
) {
    var link by mutableStateOf(StreamLink())
        internal set
    var permission by mutableStateOf(MicPermission.ASKABLE)
        internal set
    internal var rationale by mutableStateOf(false)
    internal var askSleep by mutableStateOf(false)
    internal var ask: () -> Unit = {}
    internal var openSettings: () -> Unit = {}

    val mic: MicUi get() = MicPolicy.ui(link, permission)

    fun toggleMic() = perform(MicPolicy.onToggle(mic))

    internal fun perform(action: MicAction) {
        when (action) {
            MicAction.ASK_PERMISSION -> rationale = true
            MicAction.OPEN_SETTINGS -> openSettings()
            MicAction.START -> container.stream.setMicLive(true)
            MicAction.STOP -> container.stream.setMicLive(false)
            MicAction.NONE -> Unit
        }
    }

    fun sendClipboard(): ClipboardSendResult = container.stream.sendClipboard()
}

private const val ASKED_MIC_KEY = "nebula_asked_mic_permission"

private fun Activity.micPermission(asked: Boolean): MicPermission = when {
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> MicPermission.GRANTED
    asked && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) -> MicPermission.BLOCKED
    else -> MicPermission.ASKABLE
}

/** Creates the stream's [StreamHostState] and composes its dialogs. Call once from the stream screen. */
@Composable
fun rememberStreamHostState(container: AppContainer, hostId: String, gameId: String, live: Boolean): StreamHostState {
    val vm = viewModel(key = "host-actions-stream-$hostId-$gameId") { HostActionsViewModel(container, hostId, gameId) }
    val state = remember(vm) { StreamHostState(vm, container) }
    val activity = LocalActivity.current ?: return state
    val ctx = LocalContext.current
    val prefs = remember { LegacyPrefs(ctx) }
    val link by container.stream.link.collectAsState(initial = StreamLink())
    state.link = link
    HostActionToasts(vm)

    fun refreshPermission() {
        state.permission = activity.micPermission(prefs.prefs.getBoolean(ASKED_MIC_KEY, false))
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAsk = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        val (action, perm) = MicPolicy.afterPermission(granted, canAsk)
        state.permission = perm
        if (!granted) Toast.makeText(ctx, "The microphone stays off. You can allow it later from the stream menu.", Toast.LENGTH_LONG).show()
        state.perform(action)
    }
    state.ask = {
        prefs.put(ASKED_MIC_KEY, true)
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    state.openSettings = {
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
        }
    }

    // Permission can change in system settings while the stream is in the background.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        refreshPermission()
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refreshPermission() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // Android 10+ allows clipboard reads only with input focus: tell the engine when it has it.
    val view = LocalView.current
    DisposableEffect(view) {
        val l = ViewTreeObserver.OnWindowFocusChangeListener { container.stream.onWindowFocus(it) }
        view.viewTreeObserver.addOnWindowFocusChangeListener(l)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(l) }
    }

    // "Initial microphone state", once the host has answered.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(live, link.micSupported) {
        if (live && link.micSupported != null && !started) {
            started = true
            state.perform(MicPolicy.atStart(state.mic, link.micWantedAtStart))
        }
    }

    if (state.rationale) {
        NebulaChoiceDialog(
            title = "Use your microphone on the PC?",
            text = "Nebula sends this device's microphone to your PC as \"Nova Mic\", for voice chat in games and apps. " +
                "Audio is only sent while the mic is on in the stream menu, and it pauses when Nebula leaves the screen.",
            confirm = "Continue",
            dismiss = "Not now",
            onConfirm = { state.rationale = false; state.ask() },
            onDismiss = { state.rationale = false },
        )
    }
    return state
}

private fun micTint(scheme: String?): Color = when (scheme) {
    "gradient_blue" -> NebulaColors.info
    "gradient_purple" -> NebulaColors.accentText
    "gradient_green" -> NebulaColors.success
    "gradient_orange" -> NebulaColors.warning
    "gradient_red" -> NebulaColors.danger
    else -> NebulaColors.text
}

/**
 * The on-stream mic button (V+ "floating microphone button": position and colour from Settings)
 * that mutes and unmutes, or, with the button hidden, a small "Mic on" indicator while it's live.
 */
@Composable
fun StreamMicIndicator(state: StreamHostState, visible: Boolean, prefs: LegacyPrefs) {
    val ui = state.mic
    if (!visible || ui == MicUi.HIDDEN || ui == MicUi.UNSUPPORTED || ui == MicUi.CONNECTING) return
    val s = Nebula.scale
    val tick by prefs.changes().collectAsState(initial = null)
    val showButton = remember(tick) { prefs.prefs.getBoolean("checkbox_show_mic_button", true) }
    val position = remember(tick) { prefs.prefs.getString("list_mic_button_position", "center_right") }
    val tint = remember(tick) { micTint(prefs.prefs.getString("list_mic_icon_color", "solid_white")) }
    val floatBallAt = remember(tick) { if (prefs.prefs.getBoolean("checkbox_enable_float_ball", true)) prefs.prefs.getString("list_float_ball_position", "center_right") else null }
    val on = ui == MicUi.LIVE
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(s.dp(10))) {
        if (!showButton) {
            if (on) {
                Pill(
                    "Mic on", color = NebulaColors.text, background = Color(0xCC17171A),
                    modifier = Modifier.align(Alignment.TopEnd),
                    leading = { Box(Modifier.size(s.dp(8)).background(NebulaColors.danger, CircleShape)) },
                )
            }
            return@Box
        }
        val align = when (position) {
            "top_left" -> Alignment.TopStart
            "top_center" -> Alignment.TopCenter
            "top_right" -> Alignment.TopEnd
            "center_left" -> Alignment.CenterStart
            "bottom_left" -> Alignment.BottomStart
            "bottom_center" -> Alignment.BottomCenter
            "bottom_right" -> Alignment.BottomEnd
            else -> Alignment.CenterEnd
        }
        // Sit just below the float ball when both are at the same spot.
        val nudge = if (floatBallAt == (position ?: "center_right")) s.dp(56) else 0.dp
        Box(
            Modifier.align(align).offset(y = nudge).size(s.dp(44))
                .nebulaClickable(CircleShape, { state.toggleMic() })
                .background(if (on) NebulaColors.accent.copy(alpha = 0.9f) else Color(0xCC17171A), CircleShape)
                .border(1.dp, if (on) NebulaColors.accentText else NebulaColors.controlBorder, CircleShape)
                .semantics { contentDescription = "Microphone"; stateDescription = MicPolicy.label(ui) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (on) Icons.Rounded.Mic else Icons.Rounded.MicOff, null, tint = if (on) Color.White else tint.copy(alpha = 0.85f), modifier = Modifier.size(s.dp(22)))
        }
    }
}

/**
 * The stream menu's "PC" section: microphone, clipboard (real two-way sync, typing as an extra),
 * host commands and Sleep PC. Every row is a D-pad target.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamHostMenuSection(state: StreamHostState, prefs: LegacyPrefs, onTypeClipboard: () -> Unit, onSlept: () -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val vm = state.actions
    val host by vm.host.collectAsStateWithLifecycle()
    val commands by vm.commands.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val hostName = host?.name ?: "your PC"
    LaunchedEffect(Unit) { vm.loadCommands(force = true) }

    Column(verticalArrangement = Arrangement.spacedBy(s.dp(14))) {
        // Microphone
        if (state.mic != MicUi.HIDDEN) {
            Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                SectionTitle("Microphone", Modifier.padding(bottom = 0.dp))
                MicRow(state)
                if (prefs.prefs.getString("list_mic_menu_action_mode", "show_button") == "show_button" && state.mic != MicUi.UNSUPPORTED) {
                    val shown = prefs.prefs.getBoolean("checkbox_show_mic_button", true)
                    ToggleRow("Mic button on the stream", "A mute button over the game; its spot and colour are in Settings → Audio.", shown) {
                        prefs.put("checkbox_show_mic_button", it)
                    }
                }
            }
        }

        // Clipboard
        Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            SectionTitle("Clipboard", Modifier.padding(bottom = 0.dp))
            val text = prefs.prefs.getBoolean("checkbox_clipboard_sync_text", false)
            val images = prefs.prefs.getBoolean("checkbox_clipboard_sync_image", false)
            when (state.link.clipboard) {
                ClipboardMode.SYNCING -> Pill(
                    "Syncing ${listOfNotNull("text".takeIf { text }, "images".takeIf { images }).joinToString(" and ")} both ways",
                    color = NebulaColors.success, background = NebulaColors.successTint,
                )
                ClipboardMode.UNSUPPORTED -> Text("$hostName doesn't sync the clipboard. Typing it still works.", style = Nebula.type.label, color = NebulaColors.textMuted)
                ClipboardMode.NOT_ALLOWED -> PermissionNote("$hostName has clipboard sync off for this device.")
                ClipboardMode.OFF -> Text("Clipboard sync is off. Turn it on in Settings → Host to copy and paste both ways.", style = Nebula.type.label, color = NebulaColors.textMuted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                if (state.link.clipboard == ClipboardMode.SYNCING) {
                    NebulaButton("Send clipboard to PC", icon = Icons.Outlined.ContentPaste, style = ButtonStyle.Secondary, onClick = {
                        val msg = when (state.sendClipboard()) {
                            ClipboardSendResult.SENT -> "Sent to $hostName's clipboard"
                            ClipboardSendResult.EMPTY -> "The clipboard is empty"
                            ClipboardSendResult.NOT_SYNCING -> "Clipboard sync isn't running"
                        }
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    })
                }
                NebulaButton("Type clipboard", icon = Icons.Outlined.Keyboard, style = ButtonStyle.Secondary, onClick = onTypeClipboard)
            }
            if (state.link.clipboard == ClipboardMode.SYNCING) {
                Text("Copy on either side and paste on the other. “Type clipboard” types the text as keystrokes instead.", style = Nebula.type.label, color = NebulaColors.textMuted)
            }
        }

        // Host commands
        val cmdGate = if (commands == null) host?.features?.commands ?: Gate.UNSUPPORTED else HostGating.commandsGate(host, commands ?: HostCommands.None)
        if (cmdGate != Gate.UNSUPPORTED) {
            Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                SectionTitle("Host commands", Modifier.padding(bottom = 0.dp))
                HostCommandList(hostName, commands, cmdGate, busy, vm::run)
            }
        }

        // Power
        val sleepGate = HostGating.sleepGate(host)
        if (sleepGate != Gate.UNSUPPORTED) {
            Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                SectionTitle("Power", Modifier.padding(bottom = 0.dp))
                if (sleepGate == Gate.AVAILABLE) {
                    NebulaButton(
                        if (busy == HostActionsViewModel.SLEEP) "Putting $hostName to sleep…" else "Sleep $hostName",
                        icon = Icons.Outlined.Bedtime, style = ButtonStyle.Secondary, onClick = { state.askSleep = true },
                    )
                } else {
                    PermissionNote("$hostName hasn't allowed this device to put it to sleep.")
                }
            }
        }
    }
    if (state.askSleep) {
        SleepConfirmDialog(hostName, streaming = true, onConfirm = { state.askSleep = false; vm.sleep(onSlept) }, onDismiss = { state.askSleep = false })
    }
}

@Composable
private fun MicRow(state: StreamHostState) {
    val s = Nebula.scale
    val ui = state.mic
    val shape = RoundedCornerShape(s.dp(14))
    val on = ui == MicUi.LIVE
    val actionable = MicPolicy.onToggle(ui) != MicAction.NONE
    Row(
        Modifier.fillMaxWidth().heightIn(min = s.dp(60))
            .nebulaClickable(shape, { state.toggleMic() }, role = Role.Switch)
            .background(if (on) NebulaColors.accentTint else NebulaColors.surface, shape)
            .border(1.dp, if (on) NebulaColors.accent else NebulaColors.border, shape)
            .padding(horizontal = s.dp(14), vertical = s.dp(10))
            .semantics { stateDescription = MicPolicy.label(ui) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(s.dp(36)).background(if (on) NebulaColors.accent else NebulaColors.raised, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(if (on) Icons.Rounded.Mic else Icons.Rounded.MicOff, null, tint = if (on) Color.White else NebulaColors.textSecondary, modifier = Modifier.size(s.dp(20))) }
        Spacer(Modifier.width(s.dp(12)))
        Column(Modifier.weight(1f)) {
            Text(if (on) "Microphone on" else "Microphone", style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(MicPolicy.label(ui), style = Nebula.type.label, color = if (ui == MicUi.BLOCKED) NebulaColors.warning else NebulaColors.textSecondary)
        }
        if (actionable) {
            Text(
                when (ui) {
                    MicUi.LIVE -> "Mute"
                    MicUi.PAUSED -> "Turn off"
                    MicUi.NEEDS_PERMISSION -> "Allow"
                    MicUi.BLOCKED -> "Settings"
                    else -> "Unmute"
                },
                style = Nebula.type.label, color = NebulaColors.accentText,
            )
        }
    }
}
