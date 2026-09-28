package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.f_e_n_y_x.nebula.domain.HostGating
import io.github.f_e_n_y_x.nebula.domain.WakeState
import io.github.f_e_n_y_x.nebula.domain.model.Gate
import io.github.f_e_n_y_x.nebula.domain.model.HostCommand
import io.github.f_e_n_y_x.nebula.domain.model.HostCommands
import io.github.f_e_n_y_x.nebula.ui.HostActionsViewModel
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.NebulaStar
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/** Shows [HostActionsViewModel] result messages as toasts. */
@Composable
fun HostActionToasts(vm: HostActionsViewModel) {
    val ctx = LocalContext.current
    LaunchedEffect(vm) { vm.messages.collect { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show() } }
}

/**
 * Full-screen progress while a sleeping PC wakes up: "Waking atom…", the seconds so far, then the
 * game launches by itself. Timeout and failure explain what to check. Back or Cancel stops waiting.
 */
@Composable
fun WakeOverlay(hostName: String, gameName: String?, state: WakeState?, onCancel: () -> Unit, onRetry: () -> Unit) {
    state ?: return
    val s = Nebula.scale
    val t = Nebula.type
    androidx.activity.compose.BackHandler(onBack = onCancel)
    Box(
        Modifier.fillMaxSize().background(Color(0xE60A0A0B))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = s.dp(460)).padding(s.dp(28)).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val focus = remember { FocusRequester() }
            when (state) {
                WakeState.Sending, is WakeState.Waiting, WakeState.Online -> {
                    WakePulse()
                    Spacer(Modifier.height(s.dp(26)))
                    Text("Waking $hostName…", style = t.title, color = NebulaColors.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(s.dp(10)))
                    Text(
                        if (gameName != null) "Sent a wake-up signal. $gameName starts as soon as $hostName answers." else "Sent a wake-up signal. $hostName is ready when its card says so.",
                        style = t.body, color = NebulaColors.textSecondary, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(s.dp(18)))
                    val w = state as? WakeState.Waiting
                    Text(
                        if (w == null) "Sending…" else "${w.elapsedS} s · gives up after ${w.timeoutS} s",
                        style = t.mono, color = NebulaColors.textMuted,
                    )
                    Spacer(Modifier.height(s.dp(24)))
                    NebulaButton("Cancel", onClick = onCancel, style = ButtonStyle.Secondary, modifier = Modifier.focusRequester(focus))
                }
                is WakeState.TimedOut -> {
                    Icon(Icons.Outlined.Bedtime, null, tint = NebulaColors.warning, modifier = Modifier.size(s.dp(40)))
                    Spacer(Modifier.height(s.dp(18)))
                    Text("$hostName didn't wake up", style = t.title, color = NebulaColors.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(s.dp(10)))
                    Text(
                        "No answer after ${state.afterS} s. Check that Wake-on-LAN is on in the PC's BIOS and network settings, and that this device is on the same network: the wake-up signal can't travel over Tailscale or mobile data.",
                        style = t.body, color = NebulaColors.textSecondary, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(s.dp(24)))
                    Row(horizontalArrangement = Arrangement.spacedBy(s.dp(10))) {
                        NebulaButton("Try again", onClick = onRetry, icon = Icons.Rounded.Bolt, modifier = Modifier.focusRequester(focus))
                        NebulaButton("Close", onClick = onCancel, style = ButtonStyle.Secondary)
                    }
                }
                is WakeState.Failed -> {
                    Text("Couldn't wake $hostName", style = t.title, color = NebulaColors.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(s.dp(10)))
                    Text(state.reason, style = t.body, color = NebulaColors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(s.dp(24)))
                    NebulaButton("Close", onClick = onCancel, style = ButtonStyle.Secondary, modifier = Modifier.focusRequester(focus))
                }
            }
            LaunchedEffect(state::class) { runCatching { focus.requestFocus() } }
        }
    }
}

/** The Nebula mark breathing inside a soft ring while the PC wakes. */
@Composable
private fun WakePulse() {
    val s = Nebula.scale
    val reduced = Nebula.reducedMotion
    val pulse = if (reduced) 1f else rememberInfiniteTransition(label = "wake").animateFloat(0.45f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p").value
    Box(Modifier.size(s.dp(96)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(s.dp(96)).alpha(pulse * 0.6f).background(NebulaColors.accentTint, CircleShape).border(1.dp, NebulaColors.accent.copy(alpha = 0.5f), CircleShape))
        NebulaStar(Modifier.size(s.dp(44)).alpha(0.55f + pulse * 0.45f))
    }
}

/** Nova's command icon names → Material symbols. */
fun commandIcon(name: String?): ImageVector = when (name) {
    "refresh" -> Icons.Outlined.Refresh
    "power" -> Icons.Rounded.PowerSettingsNew
    "lock" -> Icons.Outlined.Lock
    "volume" -> Icons.Outlined.VolumeUp
    "mic-off" -> Icons.Outlined.MicOff
    "monitor" -> Icons.Outlined.Monitor
    "gamepad" -> Icons.Outlined.SportsEsports
    "stop" -> Icons.Outlined.StopCircle
    "play" -> Icons.Rounded.PlayArrow
    "folder" -> Icons.Outlined.Folder
    "settings" -> Icons.Outlined.Settings
    else -> Icons.Outlined.Terminal
}

/**
 * The host commands list ("super menu"): host-wide commands, then the game's own. Each row is a
 * D-pad target; commands that ask for it get a confirmation first. [gate] NOT_ALLOWED shows how to
 * allow commands for this device instead of a list.
 */
@Composable
fun HostCommandList(
    hostName: String,
    commands: HostCommands?,
    gate: Gate,
    busy: String?,
    onRun: (HostCommand) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
) {
    val s = Nebula.scale
    var confirming by remember { mutableStateOf<HostCommand?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        when {
            gate == Gate.NOT_ALLOWED -> PermissionNote("$hostName hasn't allowed this device to run commands.")
            commands == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(s.dp(16)), color = NebulaColors.accentText, strokeWidth = 2.dp)
                Spacer(Modifier.width(s.dp(10)))
                Text("Loading commands…", style = Nebula.type.label, color = NebulaColors.textMuted)
            }
            commands.commands.isEmpty() -> Text(
                "No commands set up. Add them in Nova's web UI.",
                style = Nebula.type.label, color = NebulaColors.textMuted,
            )
            else -> commands.commands.forEachIndexed { i, cmd ->
                CommandRow(
                    cmd, running = busy == cmd.id,
                    onClick = { if (cmd.confirm) confirming = cmd else onRun(cmd) },
                    modifier = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
    confirming?.let { cmd ->
        NebulaChoiceDialog(
            title = "Run “${cmd.name}”?",
            text = "This runs on $hostName now.",
            confirm = "Run",
            onConfirm = { confirming = null; onRun(cmd) },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun CommandRow(cmd: HostCommand, running: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    val enabled = cmd.runnable && !cmd.running
    Row(
        modifier.fillMaxWidth().heightIn(min = s.dp(52))
            .nebulaClickable(shape, { if (enabled && !running) onClick() }, role = Role.Button)
            .background(NebulaColors.surface, shape).border(1.dp, NebulaColors.border, shape)
            .padding(horizontal = s.dp(14), vertical = s.dp(10))
            .semantics { if (!enabled) contentDescription = "${cmd.name}, unavailable" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(s.dp(32)).background(NebulaColors.raised, RoundedCornerShape(s.dp(9))), contentAlignment = Alignment.Center) {
            Icon(commandIcon(cmd.icon), null, tint = if (enabled) NebulaColors.accentText else NebulaColors.textMuted, modifier = Modifier.size(s.dp(18)))
        }
        Spacer(Modifier.width(s.dp(12)))
        Column(Modifier.weight(1f)) {
            Text(cmd.name, style = Nebula.type.bodyStrong, color = if (enabled) NebulaColors.text else NebulaColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                cmd.running -> "Running now"
                !cmd.runnable -> "Needs its game running"
                cmd.appScoped -> "For this game"
                else -> null
            }
            if (sub != null) Text(sub, style = Nebula.type.label, color = NebulaColors.textMuted)
        }
        if (running) CircularProgressIndicator(Modifier.size(s.dp(18)), color = NebulaColors.accentText, strokeWidth = 2.dp)
    }
}

/** "Enable in Nova → Devices → Permissions" under an action the host turned off for this device. */
@Composable
fun PermissionNote(what: String, modifier: Modifier = Modifier) {
    val s = Nebula.scale
    Column(
        modifier.fillMaxWidth().background(NebulaColors.raised, RoundedCornerShape(s.dp(12)))
            .border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(12))).padding(s.dp(12)),
    ) {
        Text(what, style = Nebula.type.label, color = NebulaColors.textSecondary)
        Text(HostGating.PERMISSION_HINT, style = Nebula.type.label, color = NebulaColors.accentText)
    }
}

/** Host commands as a dialog panel (details page), sized for phone, tablet and TV. */
@Composable
fun HostCommandsDialog(hostName: String, gameName: String?, vm: HostActionsViewModel, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val commands by vm.commands.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val host by vm.host.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadCommands(force = true) }
    val first = remember { FocusRequester() }
    val close = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(s.dp(20))
        Column(
            Modifier.padding(s.dp(20)).widthIn(max = s.dp(520)).fillMaxWidth()
                .background(NebulaColors.panel, shape).border(1.dp, NebulaColors.border, shape)
                .verticalScroll(rememberScrollState()).padding(s.dp(22)),
            verticalArrangement = Arrangement.spacedBy(s.dp(14)),
        ) {
            Column {
                SectionTitle("Host commands", Modifier.padding(bottom = 0.dp))
                Text(if (gameName != null) "$hostName · $gameName" else hostName, style = Nebula.type.heading, color = NebulaColors.text)
            }
            HostCommandList(hostName, commands, HostGating.commandsGate(host, commands ?: HostCommands.None).let { if (commands == null) Gate.AVAILABLE else it }, busy, vm::run, firstFocus = first)
            NebulaButton("Close", onClick = onDismiss, style = ButtonStyle.Secondary, modifier = Modifier.focusRequester(close))
        }
        LaunchedEffect(commands) { runCatching { if (commands?.commands?.isNotEmpty() == true) first.requestFocus() else close.requestFocus() } }
    }
}

/** A confirm dialog with a primary (not destructive) confirm button. */
@Composable
fun NebulaChoiceDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismiss: String = "Cancel", danger: Boolean = false) {
    val s = Nebula.scale
    val focus = remember { FocusRequester() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        titleContentColor = NebulaColors.text,
        textContentColor = NebulaColors.textSecondary,
        title = { Text(title, style = Nebula.type.heading) },
        text = { Text(text, style = Nebula.type.body) },
        confirmButton = {
            NebulaButton(confirm, onClick = onConfirm, style = if (danger) ButtonStyle.Danger else ButtonStyle.Primary, modifier = Modifier.focusRequester(focus))
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        },
        dismissButton = { NebulaButton(dismiss, onClick = onDismiss, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}

/** "Sleep PC" confirmation; [streaming] adds that the stream ends. */
@Composable
fun SleepConfirmDialog(hostName: String, streaming: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    NebulaChoiceDialog(
        title = "Put $hostName to sleep?",
        text = (if (streaming) "This stream ends and " else "") + "$hostName suspends. Wake it again from Nebula with Wake-on-LAN, or press Play.",
        confirm = "Sleep",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        danger = true,
    )
}
