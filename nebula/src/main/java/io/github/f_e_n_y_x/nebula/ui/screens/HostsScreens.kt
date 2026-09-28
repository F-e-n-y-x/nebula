package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WifiFind
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.HostGating
import io.github.f_e_n_y_x.nebula.domain.WakeState
import io.github.f_e_n_y_x.nebula.domain.model.Gate
import io.github.f_e_n_y_x.nebula.ui.HostActionsViewModel
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.domain.model.PairingAs
import io.github.f_e_n_y_x.nebula.domain.model.PairingState
import io.github.f_e_n_y_x.nebula.ui.HostsViewModel
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.PairViewModel
import io.github.fenyx.nebula.engine.PairingName
import io.github.f_e_n_y_x.nebula.ui.Route
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.NebulaIconButton
import io.github.f_e_n_y_x.nebula.ui.components.NebulaStar
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.StatusDot
import io.github.f_e_n_y_x.nebula.ui.components.label
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.GeistMono
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

private fun Modifier.nebulaBackdrop() = this.background(
    Brush.radialGradient(listOf(Color(0xFF231A55), NebulaColors.bg), radius = 1600f),
)

@Composable
fun OnboardingScreen(container: AppContainer, nav: Navigator) {
    val s = Nebula.scale
    val t = Nebula.type
    Box(Modifier.fillMaxSize().background(NebulaColors.bg).nebulaBackdrop(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = s.dp(520)).statusBarsPadding().navigationBarsPadding().padding(s.dp(32)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NebulaStar(Modifier.size(s.dp(56)))
            Spacer(Modifier.height(s.dp(28)))
            Text("Your PC games, on every screen", style = t.title, color = NebulaColors.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(s.dp(12)))
            Text(
                "Nebula streams games from your PC running Nova or Sunshine to this device. Make sure both are on the same network.",
                style = t.body, color = NebulaColors.textSecondary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(s.dp(32)))
            NebulaButton("Find my PC", onClick = { nav.top(Route.Hosts) }, icon = Icons.Outlined.WifiFind, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(s.dp(12)))
            Text(
                if (container.isDemo) "Demo build: shows a sample host with real artwork." else "Nebula is private: no account, no tracking.",
                style = t.label, color = NebulaColors.textMuted, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun HostsScreen(container: AppContainer, nav: Navigator) {
    val vm = viewModel { HostsViewModel(container) }
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val s = Nebula.scale
    val t = Nebula.type
    val form = Nebula.form
    var adding by remember { mutableStateOf(false) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(s.dp(340)),
        modifier = Modifier.fillMaxSize().background(NebulaColors.bg),
        contentPadding = PaddingValues(start = s.dp(24), end = s.dp(24), bottom = s.dp(32)),
        horizontalArrangement = Arrangement.spacedBy(s.dp(16)),
        verticalArrangement = Arrangement.spacedBy(s.dp(16)),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = s.dp(24))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hosts", style = t.title, color = NebulaColors.text)
                        Spacer(Modifier.height(s.dp(4)))
                        Text(
                            if (searching) "Looking for Nova and Sunshine on your network…" else "PCs on your network. Pair once, then play from the library.",
                            style = t.secondary, color = NebulaColors.textSecondary,
                        )
                    }
                    if (searching) CircularProgressIndicator(Modifier.size(s.dp(22)), color = NebulaColors.accentText, strokeWidth = 2.dp)
                    else NebulaIconButton(Icons.Outlined.Refresh, "Search again", { vm.discover() })
                }
                message?.let {
                    Spacer(Modifier.height(s.dp(12)))
                    Pill(it, color = NebulaColors.warning)
                }
                Spacer(Modifier.height(s.dp(12)))
            }
        }
        if (hosts.isEmpty() && !searching) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No PCs found yet. Make sure Nova or Sunshine is running on a PC on this network, or add it by its address.",
                    style = t.body, color = NebulaColors.textSecondary, modifier = Modifier.widthIn(max = s.dp(560)),
                )
            }
        }
        items(hosts, key = { it.id }) { h ->
            val actions = viewModel(key = "host-actions-card-${h.id}") { HostActionsViewModel(container, h.id, null) }
            val wake by actions.wake.collectAsStateWithLifecycle()
            val busy by actions.busy.collectAsStateWithLifecycle()
            HostActionToasts(actions)
            var menuFor by remember { mutableStateOf<String?>(null) }
            HostCard(h,
                wake = wake,
                sleeping = busy == HostActionsViewModel.SLEEP,
                onMenu = { menuFor = it },
                onPrimary = {
                    when {
                        wake?.finished == false -> actions.cancelWake()
                        HostGating.showWake(h) -> actions.wakeOnly()
                        h.status == HostStatus.OFFLINE && h.paired -> vm.wake(h.id)
                        !h.paired -> nav.push(Route.Pair(h.id))
                        else -> { vm.select(h.id); nav.top(Route.Library(h.id)) }
                    }
                },
            )
            when (menuFor) {
                "sleep" -> SleepConfirmDialog(h.name, streaming = false, onConfirm = { menuFor = null; actions.sleep() }, onDismiss = { menuFor = null })
                "commands" -> HostCommandsDialog(h.name, null, actions, onDismiss = { menuFor = null })
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (adding) {
                AddHostField(onAdd = { vm.addManual(it); adding = false }, onCancel = { adding = false })
            } else {
                NebulaButton("Add a PC by address", onClick = { adding = true }, icon = Icons.Outlined.Add, style = ButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun HostCard(host: Host, wake: WakeState?, sleeping: Boolean, onMenu: (String) -> Unit, onPrimary: () -> Unit) {
    val s = Nebula.scale
    val t = Nebula.type
    val shape = RoundedCornerShape(s.dp(16))
    val wakeable = host.status == HostStatus.OFFLINE && host.paired
    val action = when {
        wake is WakeState.Waiting -> "Waking… ${wake.elapsedS} s · tap to stop"
        wake == WakeState.Sending -> "Sending wake-up signal…"
        wake is WakeState.TimedOut -> "Didn't wake · Try again"
        wake is WakeState.Failed -> wake.reason
        sleeping -> "Going to sleep…"
        wakeable -> "Wake PC"
        !host.paired -> "Pair"
        else -> "Open library"
    }
    val sleepGate = HostGating.sleepGate(host)
    val hasMenu = sleepGate != Gate.UNSUPPORTED || host.features.commands != Gate.UNSUPPORTED
    Column(
        Modifier
            .fillMaxWidth()
            .nebulaClickable(shape, onPrimary, focusScale = 1.02f)
            .background(NebulaColors.surface, shape)
            .border(1.dp, NebulaColors.border, shape)
            .padding(s.dp(20)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(s.dp(44)).background(NebulaColors.raised, RoundedCornerShape(s.dp(12))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Dns, null, tint = NebulaColors.textSecondary, modifier = Modifier.size(s.dp(22))) }
            Spacer(Modifier.width(s.dp(14)))
            Column(Modifier.weight(1f)) {
                Text(host.name, style = t.heading, color = NebulaColors.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(host.status)
                    Spacer(Modifier.width(s.dp(6)))
                    Text(host.status.label() + if (host.paired) " · Paired" else " · Not paired", style = t.secondary, color = NebulaColors.textSecondary)
                }
            }
            // Nova is only confirmed after pairing (its API needs the client certificate).
            val kind = if (host.isNova) "Nova" else host.version?.takeIf { it.isNotBlank() }
            if (kind != null) {
                Pill(kind, color = if (host.isNova) NebulaColors.accentText else NebulaColors.textSecondary,
                    background = if (host.isNova) NebulaColors.accentTint else NebulaColors.raised)
            }
            if (hasMenu) HostMenu(host, sleepGate, onMenu)
        }
        Spacer(Modifier.height(s.dp(16)))
        Text(listOfNotNull(host.gpu, host.address).joinToString("  ·  "), style = t.mono, color = NebulaColors.textMuted)
        Spacer(Modifier.height(s.dp(16)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (wake != null && !wake.finished) {
                CircularProgressIndicator(Modifier.size(s.dp(14)), color = NebulaColors.accentText, strokeWidth = 2.dp)
                Spacer(Modifier.width(s.dp(8)))
            }
            Text(action, style = t.bodyStrong, color = if (host.paired && host.status != HostStatus.OFFLINE) NebulaColors.accentText else NebulaColors.text)
            Spacer(Modifier.width(s.dp(6)))
            if (wake == null) {
                Icon(if (wakeable) Icons.Outlined.Bolt else Icons.AutoMirrored.Rounded.ArrowForward, null,
                    tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(16)))
            }
        }
        if (wake is WakeState.TimedOut) {
            Spacer(Modifier.height(s.dp(8)))
            Text("Check Wake-on-LAN in the PC's BIOS. The signal can't travel over Tailscale or mobile data.", style = t.label, color = NebulaColors.textMuted)
        }
    }
}

/** The host card's "⋮" menu: Sleep PC and host commands, when the host offers them. */
@Composable
private fun HostMenu(host: Host, sleepGate: Gate, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        NebulaIconButton(Icons.Rounded.MoreVert, "More for ${host.name}", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NebulaColors.raised) {
            if (sleepGate != Gate.UNSUPPORTED) {
                val allowed = sleepGate == Gate.AVAILABLE
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Sleep PC", style = Nebula.type.body, color = if (allowed) NebulaColors.text else NebulaColors.textMuted)
                            if (!allowed) Text(HostGating.PERMISSION_HINT, style = Nebula.type.label, color = NebulaColors.accentText)
                        }
                    },
                    leadingIcon = { Icon(Icons.Outlined.Bedtime, null, tint = NebulaColors.textSecondary) },
                    enabled = allowed,
                    onClick = { open = false; onPick("sleep") },
                )
            }
            if (host.features.commands != Gate.UNSUPPORTED && host.status != HostStatus.OFFLINE) {
                DropdownMenuItem(
                    text = { Text("Host commands…", style = Nebula.type.body, color = NebulaColors.text) },
                    leadingIcon = { Icon(Icons.Outlined.Terminal, null, tint = NebulaColors.textSecondary) },
                    onClick = { open = false; onPick("commands") },
                )
            }
        }
    }
}

@Composable
private fun AddHostField(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    val s = Nebula.scale
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(s.dp(12))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
        BasicTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            textStyle = Nebula.type.body.copy(color = NebulaColors.text), cursorBrush = SolidColor(NebulaColors.accentText),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAdd(text) }),
            modifier = Modifier.weight(1f).widthIn(max = s.dp(420)).focusRequester(focus).onFocusChanged { focused = it.isFocused }
                .background(NebulaColors.surface, shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) NebulaColors.accentText else NebulaColors.controlBorder, shape)
                .padding(s.dp(14)),
            decorationBox = { inner -> if (text.isEmpty()) Text("192.168.1.20 or pc.local", style = Nebula.type.body, color = NebulaColors.textMuted); inner() },
        )
        NebulaButton("Add", onClick = { if (text.isNotBlank()) onAdd(text) })
        NebulaIconButton(Icons.Rounded.Close, "Cancel", onCancel)
    }
}

@Composable
fun PairScreen(container: AppContainer, nav: Navigator, hostId: String) {
    val vm = viewModel { PairViewModel(container, hostId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val host by vm.host.collectAsStateWithLifecycle()
    val pairingAs by vm.pairingAs.collectAsStateWithLifecycle()
    val renamedLate by vm.renamedLate.collectAsStateWithLifecycle()
    val s = Nebula.scale
    val t = Nebula.type
    val name = host?.name ?: "your PC"
    Box(Modifier.fillMaxSize().background(NebulaColors.bg).nebulaBackdrop()) {
        NebulaIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() }, Modifier.statusBarsPadding().padding(s.dp(20)))
        Column(
            Modifier.align(Alignment.Center).widthIn(max = s.dp(560)).padding(s.dp(28)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val st = state) {
                PairingState.Connecting -> {
                    CircularProgressIndicator(color = NebulaColors.accentText)
                    Spacer(Modifier.height(s.dp(20)))
                    Text("Connecting to $name…", style = t.heading, color = NebulaColors.text)
                }
                is PairingState.ShowPin -> {
                    Text("Pair with $name", style = t.title, color = NebulaColors.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(s.dp(12)))
                    PairingAsRow(pairingAs, renamedLate, vm::renameDevice)
                    Spacer(Modifier.height(s.dp(24)))
                    Row(horizontalArrangement = Arrangement.spacedBy(s.dp(12))) {
                        st.pin.forEach { d ->
                            Box(
                                Modifier.size(width = s.dp(64), height = s.dp(80)).background(NebulaColors.surface, RoundedCornerShape(s.dp(14)))
                                    .border(1.dp, NebulaColors.controlBorder, RoundedCornerShape(s.dp(14))),
                                contentAlignment = Alignment.Center,
                            ) { Text(d.toString(), style = t.title.copy(fontFamily = GeistMono, fontSize = s.sp(36)), color = NebulaColors.text) }
                        }
                    }
                    Spacer(Modifier.height(s.dp(28)))
                    Steps(listOf(
                        "On your PC, open Nova's web UI and go to Pair a device.",
                        "Enter this PIN. Nova fills in this device's name for you.",
                        "Keep this screen open. It moves on by itself.",
                    ))
                    Spacer(Modifier.height(s.dp(20)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pulse = rememberInfiniteTransition(label = "wait").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
                        Box(Modifier.size(8.dp).alpha(pulse.value).background(NebulaColors.accentText, RoundedCornerShape(50)))
                        Spacer(Modifier.width(s.dp(8)))
                        Text("Waiting for $name…", style = t.secondary, color = NebulaColors.textSecondary)
                    }
                }
                PairingState.Paired -> {
                    Box(Modifier.size(s.dp(64)).background(NebulaColors.successTint, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = NebulaColors.success, modifier = Modifier.size(s.dp(34)))
                    }
                    Spacer(Modifier.height(s.dp(20)))
                    Text("Paired with $name", style = t.title, color = NebulaColors.text)
                    Spacer(Modifier.height(s.dp(8)))
                    Text("Your games are ready.", style = t.body, color = NebulaColors.textSecondary)
                    Spacer(Modifier.height(s.dp(24)))
                    NebulaButton("Open library", onClick = { nav.top(Route.Library(hostId)) })
                }
                is PairingState.Failed -> {
                    Text("Couldn't pair with $name", style = t.title, color = NebulaColors.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(s.dp(10)))
                    Text(st.reason, style = t.body, color = NebulaColors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(s.dp(16)))
                    PairingAsRow(pairingAs, renamedLate = false, onRename = vm::renameDevice)
                    Spacer(Modifier.height(s.dp(24)))
                    Row(horizontalArrangement = Arrangement.spacedBy(s.dp(10))) {
                        NebulaButton("Try again", onClick = { vm.start() })
                        NebulaButton("Back", onClick = { nav.back() }, style = ButtonStyle.Secondary)
                    }
                }
            }
        }
    }
}

/**
 * "Pairing as: Nebula from Ayush's S25 Ultra · Edit". Editing changes the device part; a request
 * the host already has keeps its name, so [renamedLate] says where to change it.
 */
@Composable
private fun PairingAsRow(pairingAs: PairingAs, renamedLate: Boolean, onRename: (String) -> Unit) {
    val s = Nebula.scale
    val t = Nebula.type
    var editing by remember { mutableStateOf(false) }
    if (!editing) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
            Text("Pairing as", style = t.secondary, color = NebulaColors.textSecondary)
            Text(pairingAs.displayName, style = t.bodyStrong, color = NebulaColors.text, modifier = Modifier.weight(1f, fill = false))
            NebulaButton("Edit", onClick = { editing = true }, style = ButtonStyle.Ghost)
        }
        if (renamedLate) {
            Text(
                "Saved for next time. Nova already has this request, so change the name there before you enter the PIN.",
                style = t.secondary, color = NebulaColors.textSecondary, textAlign = TextAlign.Center,
            )
        }
        return
    }
    var text by remember { mutableStateOf(pairingAs.deviceName) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shape = RoundedCornerShape(s.dp(12))
    val save = { onRename(text); editing = false }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
            Text("Nebula from", style = t.secondary, color = NebulaColors.textSecondary)
            BasicTextField(
                value = text, onValueChange = { text = it.take(PairingName.MAX_DEVICE_CHARS * 2) }, singleLine = true,
                textStyle = t.body.copy(color = NebulaColors.text), cursorBrush = SolidColor(NebulaColors.accentText),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.weight(1f).widthIn(max = s.dp(320)).focusRequester(focus).onFocusChanged { focused = it.isFocused }
                    .background(NebulaColors.surface, shape)
                    .border(if (focused) 2.dp else 1.dp, if (focused) NebulaColors.accentText else NebulaColors.controlBorder, shape)
                    .padding(s.dp(12)),
                decorationBox = { inner -> if (text.isEmpty()) Text("This device (automatic)", style = t.body, color = NebulaColors.textMuted); inner() },
            )
            NebulaButton("Save", onClick = save)
            NebulaIconButton(Icons.Rounded.Close, "Cancel", { editing = false })
        }
        Spacer(Modifier.height(s.dp(6)))
        Text("Leave it empty to use this device's own name.", style = t.secondary, color = NebulaColors.textMuted)
    }
}

@Composable
private fun Steps(steps: List<String>) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(12)), modifier = Modifier.fillMaxWidth()) {
        steps.forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(s.dp(24)).background(NebulaColors.accentTint, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = Nebula.type.label, color = NebulaColors.accentText)
                }
                Spacer(Modifier.width(s.dp(12)))
                Text(text, style = Nebula.type.body, color = NebulaColors.textSecondary)
            }
        }
    }
}

