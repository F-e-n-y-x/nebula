package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.LayoutSet
import io.github.f_e_n_y_x.nebula.controls.ProfileLibrary
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.NebulaConfirmDialog
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/**
 * In-stream layout picker, opened by a switch element set to "picker". Everything held was
 * released before it opened; it is a dialog window, so taps on it never reach the stream.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutPickerDialog(layouts: List<ControlsProfile>, current: String, setName: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        modifier = Modifier.testTag("layout-picker"),
        title = { Text(setName, style = Nebula.type.heading, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                layouts.forEach { p ->
                    Chip(p.name, selected = p.id == current, modifier = Modifier.testTag("pick-layout:${p.id}")) { onPick(p.id) }
                }
            }
        },
        confirmButton = { NebulaButton("Close", onClick = onDismiss, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}

/**
 * Layout sets: make one from existing profiles, reorder its layouts, pick which are in the
 * switch's cycle and which one a stream starts on, share it as one file, or give it to the game
 * ([gameKey], when opened from a stream). A set's layouts are ordinary profiles; edit them in
 * the controls editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutSetsDialog(gameKey: String?, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ControlsStore.get(ctx) }
    val data by store.data.collectAsState()
    val lib = remember(data) { store.library(data) }
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<LayoutSet?>(null) }
    var deleting by remember { mutableStateOf<LayoutSet?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.systemBarsPadding().padding(s.dp(16)).widthIn(max = s.dp(640)).background(NebulaColors.raised, RoundedCornerShape(s.dp(18)))
                .verticalScroll(rememberScrollState()).padding(s.dp(20)).testTag("layout-sets"),
            verticalArrangement = Arrangement.spacedBy(s.dp(12)),
        ) {
            PanelHeader("Controls", "Layout sets", onDismiss)
            Text(
                "A set groups several layouts for one game, such as on foot, vehicle and aircraft. Put a Layout switch on each layout (editor → Add) to change layout while playing; switching lets go of every button first.",
                style = Nebula.type.label, color = NebulaColors.textMuted,
            )
            val sets = lib.sets
            if (sets.isEmpty()) Text("No sets yet.", style = Nebula.type.body, color = NebulaColors.textSecondary)
            sets.forEach { set ->
                val open = editing == set.id
                SetCard(
                    lib, set, open, gameKey,
                    onToggle = { editing = if (open) null else set.id },
                    onChange = { changed -> store.update { it.updateSet(changed) } },
                    onUse = { gameKey?.let { k -> store.update { it.assign(k, set.id) } } },
                    onShare = { sharing = set },
                    onDelete = { deleting = set },
                )
            }
            NebulaButton("New set", onClick = { creating = true }, style = ButtonStyle.Secondary, icon = Icons.Rounded.Add, modifier = Modifier.testTag("new-set"))
        }
    }

    if (creating) {
        NewSetDialog(lib, onCreate = { name, members ->
            creating = false
            var made: LayoutSet? = null
            store.update { l -> l.createSet(name, members)?.also { made = it.second }?.first ?: l }
            editing = made?.id
        }, onDismiss = { creating = false })
    }
    sharing?.let { set ->
        val layouts = set.members.mapNotNull(lib::find)
        val start = set.startId()?.let(lib::find) ?: layouts.firstOrNull()
        if (start == null) sharing = null else ShareLayoutDialog(
            start, onMeta = { m -> store.update { it.updateSet(set.copy(meta = m)) } }, onDismiss = { sharing = null },
            set = set, setLayouts = layouts,
        )
    }
    deleting?.let { set ->
        NebulaConfirmDialog(
            title = "Delete ${set.name}?",
            text = "The set goes; its layouts stay in your profiles. Games that used it go back to the default.",
            confirm = "Delete set",
            onConfirm = { store.update { it.deleteSet(set.id) }; deleting = null; if (editing == set.id) editing = null },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetCard(
    lib: ProfileLibrary,
    set: LayoutSet,
    open: Boolean,
    gameKey: String?,
    onToggle: () -> Unit,
    onChange: (LayoutSet) -> Unit,
    onUse: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    val members = set.members.mapNotNull(lib::find)
    val cycle = set.cycleOrder()
    val used = gameKey != null && lib.assignedTo(gameKey) == set.id
    Column(
        Modifier.fillMaxWidth().background(NebulaColors.surface, shape).border(1.dp, if (used) NebulaColors.accentText else NebulaColors.border, shape)
            .padding(s.dp(12)).testTag("set:${set.id}"),
        verticalArrangement = Arrangement.spacedBy(s.dp(8)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(set.name, style = Nebula.type.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    members.joinToString(" · ") { it.name } + if (used) "  (this game)" else "",
                    style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            SmallAction(if (open) "Done" else if (set.isBuiltIn) "View" else "Edit", null, onToggle)
        }
        if (!open) return@Column
        members.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(6))) {
                Text("${i + 1}. ${p.name}", style = Nebula.type.body, color = NebulaColors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!set.isBuiltIn) {
                    Chip("Cycle", selected = p.id in cycle) {
                        val next = if (p.id in cycle) cycle - p.id else set.members.filter { it in cycle || it == p.id }
                        onChange(set.copy(cycle = next.ifEmpty { null }))
                    }
                    Chip("Start", selected = set.startId() == p.id) { onChange(set.copy(start = p.id)) }
                    if (i > 0) ToolIcon(Icons.Rounded.KeyboardArrowUp, "Move up", onClick = { onChange(set.copy(members = set.members.move(p.id, -1))) })
                    if (i < members.lastIndex) ToolIcon(Icons.Rounded.KeyboardArrowDown, "Move down", onClick = { onChange(set.copy(members = set.members.move(p.id, 1))) })
                    if (members.size > 1) ToolIcon(Icons.Rounded.Delete, "Remove from set", onClick = { set.without(p.id)?.let(onChange) })
                }
            }
        }
        if (!set.isBuiltIn && members.size < LayoutSet.MAX_LAYOUTS) {
            val others = lib.all.filter { it.id !in set.members }
            if (others.isNotEmpty()) {
                Text("Add a layout", style = Nebula.type.label, color = NebulaColors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(6)), verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
                    others.forEach { p -> Chip("+ ${p.name}", selected = false) { onChange(set.copy(members = set.members + p.id)) } }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            if (gameKey != null && !used) SmallAction("Use for this game", null, onUse)
            SmallAction("Share", Icons.Rounded.Share, onShare)
            if (!set.isBuiltIn) SmallAction("Delete set", Icons.Rounded.Delete, onDelete, danger = true)
        }
    }
}

private fun List<String>.move(id: String, by: Int): List<String> {
    val i = indexOf(id)
    val j = (i + by).coerceIn(0, lastIndex)
    if (i < 0 || i == j) return this
    return toMutableList().also { it.removeAt(i); it.add(j, id) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewSetDialog(lib: ProfileLibrary, onCreate: (String, List<String>) -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<String>()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        title = { Text("New layout set", style = Nebula.type.heading, color = NebulaColors.text) },
        text = {
            Column(Modifier.heightIn(max = s.dp(420)).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                Field("Name") { TextInput(name, { name = it.take(LayoutSet.MAX_NAME) }, "e.g. GTA V layouts") }
                Text("Layouts, in order (tap to add or remove; up to ${LayoutSet.MAX_LAYOUTS})", style = Nebula.type.label, color = NebulaColors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(6)), verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
                    lib.all.forEach { p ->
                        val n = picked.indexOf(p.id)
                        Chip(if (n >= 0) "${n + 1}. ${p.name}" else p.name, selected = n >= 0) {
                            picked = if (n >= 0) picked - p.id else if (picked.size < LayoutSet.MAX_LAYOUTS) picked + p.id else picked
                        }
                    }
                }
            }
        },
        confirmButton = {
            NebulaButton("Create", onClick = { if (picked.isNotEmpty()) onCreate(name, picked) }, modifier = Modifier.testTag("create-set"))
        },
        dismissButton = { NebulaButton("Cancel", onClick = onDismiss, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}
