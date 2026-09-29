package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.screens.ToggleRow
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/**
 * Stream-menu controls: show/hide, switch profile live for this game, and open the editor.
 * On TV (no touchscreen) only the toggle is shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlsMenuSection(gameKey: String, shown: Boolean, onShown: (Boolean) -> Unit, onEdit: () -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val store = remember { ControlsStore.get(ctx) }
    val data by store.data.collectAsState()
    val lib = remember(data) { store.library(data) }
    ToggleRow("On-screen controls", "Virtual gamepad buttons over the stream.", shown, onShown)
    if (!shown || Nebula.form.isTv) return
    val active = lib.resolve(gameKey)
    val activeSet = lib.resolveSet(gameKey)
    val own = lib.assignedTo(gameKey) != null
    var setsOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    var browsing by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        Text(
            if (own) "Controls profile · this game" else "Controls profile · the default, until you pick one for this game",
            style = Nebula.type.label, color = NebulaColors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            // Layout sets first: the game then switches between their layouts on screen.
            lib.sets.forEach { set ->
                val on = activeSet?.id == set.id
                val shape = RoundedCornerShape(s.dp(10))
                Box(
                    Modifier.heightIn(min = s.dp(40)).semantics { selected = on }
                        .nebulaClickable(shape, { store.update { it.assign(gameKey, set.id) } }, role = Role.RadioButton)
                        .background(if (on) NebulaColors.accent else NebulaColors.surface, shape)
                        .border(1.dp, if (on) NebulaColors.accentText else NebulaColors.controlBorder, shape)
                        .padding(horizontal = s.dp(12), vertical = s.dp(9)),
                    contentAlignment = Alignment.Center,
                ) { Text("${set.name} · ${set.members.size} layouts", style = Nebula.type.label, color = if (on) Color.White else NebulaColors.text, maxLines = 1) }
            }
            lib.all.forEach { p ->
                val on = activeSet == null && p.id == active.id
                val shape = RoundedCornerShape(s.dp(10))
                Box(
                    Modifier.heightIn(min = s.dp(40)).semantics { selected = on }
                        .nebulaClickable(shape, { store.update { it.assign(gameKey, p.id) } }, role = Role.RadioButton)
                        .background(if (on) NebulaColors.accent else NebulaColors.surface, shape)
                        .border(1.dp, if (on) NebulaColors.accentText else NebulaColors.controlBorder, shape)
                        .padding(horizontal = s.dp(12), vertical = s.dp(9)),
                    contentAlignment = Alignment.Center,
                ) { Text(p.name, style = Nebula.type.label, color = if (on) Color.White else NebulaColors.text, maxLines = 1) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            NebulaButton("Edit controls", onClick = onEdit, style = ButtonStyle.Secondary, icon = Icons.Outlined.Edit)
            NebulaButton("Browse layouts", onClick = { browsing = true }, style = ButtonStyle.Secondary, modifier = androidx.compose.ui.Modifier.testTag("menu-browse-layouts"))
            NebulaButton("Layout sets", onClick = { setsOpen = true }, style = ButtonStyle.Secondary, modifier = androidx.compose.ui.Modifier.testTag("open-layout-sets"))
        }
        if (lib.all.size == 1 && lib.sets.isEmpty()) {
            Text(
                "Only the standard controller is built in. Browse layouts has layouts and layout sets made for games and genres; you can also import a file, share code or QR code in Edit controls → Profiles.",
                style = Nebula.type.label, color = NebulaColors.textMuted,
            )
        }
    }
    if (setsOpen) LayoutSetsDialog(gameKey, onDismiss = { setsOpen = false })
    // A layout (or set) added here is given to this game at once.
    if (browsing) LayoutBrowserDialog(store, onAdded = { a ->
        browsing = false
        store.update { l -> l.assign(gameKey, l.setsOf(a.id).firstOrNull()?.id ?: a.id) }
    }, onDismiss = { browsing = false })
}

/**
 * Settings → On-screen controls: the default profile and the way into the editor. Per-game
 * choices are made from the stream menu or the editor opened for a game.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlsSettingsSection(onOpenEditor: () -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val store = remember { ControlsStore.get(ctx) }
    val data by store.data.collectAsState()
    val lib = remember(data) { store.library(data) }
    val default = lib.resolve(null)
    Column(Modifier.widthIn(max = s.dp(720)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
        io.github.f_e_n_y_x.nebula.ui.components.SectionTitle("Layout and profiles")
        Text(
            "Place, resize and rebind buttons, sticks, D-pads, triggers, touchpads, combos and macros. Save layouts as profiles, pick one per game from the stream menu, and import V+ Crown profiles.",
            style = Nebula.type.label, color = NebulaColors.textMuted,
        )
        Text("Default profile", style = Nebula.type.label, color = NebulaColors.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            lib.all.forEach { p ->
                val on = p.id == default.id
                val shape = RoundedCornerShape(s.dp(10))
                Box(
                    Modifier.heightIn(min = s.dp(40)).semantics { selected = on }
                        .nebulaClickable(shape, { store.update { it.setDefault(p.id) } }, role = Role.RadioButton)
                        .background(if (on) NebulaColors.accent else NebulaColors.surface, shape)
                        .border(1.dp, if (on) NebulaColors.accentText else NebulaColors.controlBorder, shape)
                        .padding(horizontal = s.dp(12), vertical = s.dp(9)),
                    contentAlignment = Alignment.Center,
                ) { Text(p.name, style = Nebula.type.label, color = if (on) Color.White else NebulaColors.text, maxLines = 1) }
            }
        }
        var setsOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
        if (lib.sets.isNotEmpty()) {
            Text("${lib.sets.size} layout set${if (lib.sets.size == 1) "" else "s"}: ${lib.sets.joinToString(", ") { it.name }}. Give one to a game from the stream menu.", style = Nebula.type.label, color = NebulaColors.textMuted)
        }
        if (lib.all.size == 1 && lib.sets.isEmpty()) {
            Text(
                "Only the standard controller is built in. Get layouts and layout sets made for your games from Browse layouts (in the controls editor's Profiles or the stream menu), or import a file, share code or QR code.",
                style = Nebula.type.label, color = NebulaColors.textMuted, modifier = Modifier.testTag("controls-empty"),
            )
        }
        if (!Nebula.form.isTv) NebulaButton("Layout sets", onClick = { setsOpen = true }, style = ButtonStyle.Secondary)
        if (setsOpen) LayoutSetsDialog(null, onDismiss = { setsOpen = false })
        val games = data.games.size
        if (games > 0) Text("$games game${if (games == 1) " has its" else "s have their"} own profile.", style = Nebula.type.label, color = NebulaColors.textMuted)
        if (Nebula.form.isTv) {
            Text("The editor needs a touchscreen; edit on a phone or tablet and share the profile.", style = Nebula.type.label, color = NebulaColors.textMuted)
        } else {
            NebulaButton("Open controls editor", onClick = onOpenEditor, icon = Icons.Outlined.Edit)
        }
    }
}
