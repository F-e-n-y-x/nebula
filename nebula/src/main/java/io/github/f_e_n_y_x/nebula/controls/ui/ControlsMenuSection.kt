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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import io.github.f_e_n_y_x.nebula.controls.ProfileGroup
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
 * Stream-menu controls: show/hide, pick this game's controls live, and open the editor. A layout
 * set is one chip ("GTA V · 5 layouts") that expands to its layouts; picking one switches to it
 * now ([onPickLayout]). On TV (no touchscreen) only the toggle is shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlsMenuSection(
    gameKey: String,
    shown: Boolean,
    onShown: (Boolean) -> Unit,
    onEdit: () -> Unit,
    /** The layout on screen now (with a set, the set's current layout). */
    currentLayoutId: String? = null,
    onPickLayout: (String) -> Unit = {},
) {
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
    var setsOpen by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    // The set whose layouts are shown; the game's own set starts open.
    var expanded by remember(activeSet?.id) { mutableStateOf(activeSet?.id) }
    val groups = remember(lib) { lib.groups() }
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        Text(
            if (own) "Controls · this game" else "Controls · the default, until you pick one for this game",
            style = Nebula.type.label, color = NebulaColors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            groups.forEach { g ->
                when (g) {
                    is ProfileGroup.SetItem -> {
                        val on = activeSet?.id == g.set.id
                        MenuChip(
                            g.title, on, Modifier.testTag("menu-set:${g.set.id}"),
                            trailing = if (expanded == g.set.id) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            leading = Icons.Rounded.Layers,
                        ) {
                            if (!on) store.update { it.assign(gameKey, g.set.id) }
                            expanded = if (expanded == g.set.id && on) null else g.set.id
                        }
                    }
                    is ProfileGroup.Single -> {
                        val on = activeSet == null && g.profile.id == active.id
                        MenuChip(g.profile.name, on, Modifier.testTag("menu-profile:${g.profile.id}")) { store.update { it.assign(gameKey, g.profile.id) } }
                    }
                }
            }
        }
        // The open set's layouts: a second row, indented under the chips.
        groups.filterIsInstance<ProfileGroup.SetItem>().firstOrNull { it.set.id == expanded }?.let { g ->
            val on = activeSet?.id == g.set.id
            Column(
                Modifier.fillMaxWidth().background(NebulaColors.surface, RoundedCornerShape(s.dp(12))).border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(12)))
                    .padding(s.dp(10)).testTag("menu-set-layouts"),
                verticalArrangement = Arrangement.spacedBy(s.dp(8)),
            ) {
                Text(
                    if (on) "${g.set.name}: tap a layout to switch to it now" else "${g.set.name}'s layouts",
                    style = Nebula.type.label, color = NebulaColors.textMuted,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(6)), verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
                    val current = currentLayoutId?.takeIf { on }
                    g.layouts.forEach { p ->
                        MenuChip(p.name, p.id == current, Modifier.testTag("menu-layout:${p.id}"), compact = true) {
                            if (!on) store.update { it.assign(gameKey, g.set.id) }
                            onPickLayout(p.id)
                        }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            NebulaButton("Edit controls", onClick = onEdit, style = ButtonStyle.Secondary, icon = Icons.Outlined.Edit)
            NebulaButton("Browse layouts", onClick = { browsing = true }, style = ButtonStyle.Secondary, modifier = Modifier.testTag("menu-browse-layouts"))
            NebulaButton("New set", onClick = { creating = true }, style = ButtonStyle.Secondary, icon = Icons.Rounded.Add, modifier = Modifier.testTag("menu-new-set"))
            NebulaButton("Layout sets", onClick = { setsOpen = true }, style = ButtonStyle.Secondary, modifier = Modifier.testTag("open-layout-sets"))
        }
        if (lib.all.size == 1 && lib.sets.isEmpty()) {
            Text(
                "Only the standard controller is built in. Browse layouts has layouts and layout sets made for games and genres; you can also import a file, share code or QR code in Edit controls → Profiles.",
                style = Nebula.type.label, color = NebulaColors.textMuted,
            )
        }
    }
    if (setsOpen) LayoutSetsDialog(gameKey, onDismiss = { setsOpen = false })
    if (creating) SetEditorDialog(null, gameKey, onDismiss = { creating = false })
    // A layout (or set) added here is given to this game at once.
    if (browsing) LayoutBrowserDialog(store, onAdded = { a ->
        browsing = false
        store.update { l -> l.assign(gameKey, l.setsOf(a.id).firstOrNull()?.id ?: a.id) }
    }, onDismiss = { browsing = false })
}

/** A radio chip of the stream menu's Controls choice. */
@Composable
private fun MenuChip(
    text: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    Row(
        modifier.heightIn(min = s.dp(if (compact) 36 else 40)).semantics { selected = on }
            .nebulaClickable(shape, onClick, role = Role.RadioButton)
            .background(if (on) NebulaColors.accent else NebulaColors.surface, shape)
            .border(1.dp, if (on) NebulaColors.accentText else NebulaColors.controlBorder, shape)
            .padding(horizontal = s.dp(12), vertical = s.dp(if (compact) 7 else 9)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.dp(6)),
    ) {
        val tint = if (on) Color.White else NebulaColors.text
        if (leading != null) Icon(leading, null, tint = if (on) Color.White else NebulaColors.accentText, modifier = Modifier.size(s.dp(16)))
        Text(text, style = Nebula.type.label, color = tint, maxLines = 1)
        if (trailing != null) Icon(trailing, null, tint = tint, modifier = Modifier.size(s.dp(16)))
    }
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
        Text("Default controls", style = Nebula.type.label, color = NebulaColors.textSecondary)
        val defaultSet = lib.resolveSet(null)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            lib.groups().forEach { g ->
                when (g) {
                    is ProfileGroup.SetItem -> MenuChip(g.title, defaultSet?.id == g.set.id, leading = Icons.Rounded.Layers) { store.update { it.setDefault(g.set.id) } }
                    is ProfileGroup.Single -> MenuChip(g.profile.name, defaultSet == null && g.profile.id == default.id) { store.update { it.setDefault(g.profile.id) } }
                }
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
