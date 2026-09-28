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
    val own = lib.assignedTo(gameKey) != null
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        Text(
            if (own) "Controls profile · this game" else "Controls profile · the default, until you pick one for this game",
            style = Nebula.type.label, color = NebulaColors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            lib.all.forEach { p ->
                val on = p.id == active.id
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
        NebulaButton("Edit controls", onClick = onEdit, style = ButtonStyle.Secondary, icon = Icons.Outlined.Edit)
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
        val games = data.games.size
        if (games > 0) Text("$games game${if (games == 1) " has its" else "s have their"} own profile.", style = Nebula.type.label, color = NebulaColors.textMuted)
        if (Nebula.form.isTv) {
            Text("The editor needs a touchscreen; edit on a phone or tablet and share the profile.", style = Nebula.type.label, color = NebulaColors.textMuted)
        } else {
            NebulaButton("Open controls editor", onClick = onOpenEditor, icon = Icons.Outlined.Edit)
        }
    }
}
