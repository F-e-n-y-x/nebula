package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.LayoutSet
import io.github.f_e_n_y_x.nebula.controls.ProfileGroup
import io.github.f_e_n_y_x.nebula.controls.ProfileLibrary
import io.github.f_e_n_y_x.nebula.controls.SetSource
import io.github.f_e_n_y_x.nebula.controls.SwitchTarget
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.screens.Segmented
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

// ---------------------------------------------------------------- the list of sets

/**
 * Layout sets: every set as one row (tap to edit it) and an obvious New set. A set's layouts
 * are ordinary profiles; [onOpenLayout] (the editor) opens one for editing.
 */
@Composable
fun LayoutSetsDialog(gameKey: String?, onDismiss: () -> Unit, onOpenLayout: ((ControlsProfile) -> Unit)? = null) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val store = remember { ControlsStore.get(ctx) }
    val data by store.data.collectAsState()
    val lib = remember(data) { store.library(data) }
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.systemBarsPadding().padding(s.dp(16)).widthIn(max = s.dp(640)).background(NebulaColors.raised, RoundedCornerShape(s.dp(18)))
                .verticalScroll(rememberScrollState()).padding(s.dp(20)).testTag("layout-sets"),
            verticalArrangement = Arrangement.spacedBy(s.dp(12)),
        ) {
            PanelHeader("Controls", "Layout sets", onDismiss)
            Text(
                "A set keeps several layouts for one game together, such as on foot, vehicle and aircraft. While playing, the switch button on each layout moves to the next one.",
                style = Nebula.type.label, color = NebulaColors.textMuted,
            )
            NebulaButton("New set", onClick = { creating = true }, icon = Icons.Rounded.Add, modifier = Modifier.testTag("new-set"))
            val groups = lib.groups().filterIsInstance<ProfileGroup.SetItem>()
            if (groups.isEmpty()) Text("No sets yet. Make one from layouts you have, or get one made for a game from Browse layouts.", style = Nebula.type.body, color = NebulaColors.textSecondary)
            groups.forEach { g ->
                val used = gameKey != null && lib.assignedTo(gameKey) == g.set.id
                SetRow(g, used, Modifier.testTag("set:${g.set.id}")) { editing = g.set.id }
            }
        }
    }
    if (creating) SetEditorDialog(null, gameKey, onDismiss = { creating = false }, onOpenLayout = onOpenLayout)
    editing?.let { id -> SetEditorDialog(id, gameKey, onDismiss = { editing = null }, onOpenLayout = onOpenLayout) }
}

@Composable
private fun SetRow(g: ProfileGroup.SetItem, used: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    Row(
        modifier.fillMaxWidth().nebulaClickable(shape, onClick).background(NebulaColors.surface, shape)
            .border(1.dp, if (used) NebulaColors.accentText else NebulaColors.border, shape).padding(horizontal = s.dp(14), vertical = s.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.dp(12)),
    ) {
        Icon(Icons.Rounded.Layers, null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(22)))
        Column(Modifier.weight(1f)) {
            Text(g.title, style = Nebula.type.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                g.layouts.joinToString(" · ") { it.name } + if (used) "  ·  this game" else "",
                style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = NebulaColors.textSecondary, modifier = Modifier.size(s.dp(22)))
    }
}

// ---------------------------------------------------------------- one set: new or edit

/** A layout of a set that isn't made yet (the New set flow saves nothing until Create). */
private data class DraftLayout(val key: Int, val source: SetSource, val name: String, val inCycle: Boolean = true)

/**
 * New set ([setId] null) or one set's editor. New: name it, add layouts (new from the standard
 * controller, a copy, or one you have), order them, pick the start layout, which are in the
 * switch's cycle and what the switch does; Create saves it all at once. Edit: the same, applied
 * as you go, plus rename, share as one file, use for [gameKey]'s game and delete.
 * Every layout without a layout switch gets one, top centre, clear of its controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetEditorDialog(
    setId: String?,
    gameKey: String?,
    onDismiss: () -> Unit,
    onOpenLayout: ((ControlsProfile) -> Unit)? = null,
    onCreated: (LayoutSet) -> Unit = {},
) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val store = remember { ControlsStore.get(ctx) }
    val data by store.data.collectAsState()
    val lib = remember(data) { store.library(data) }
    val existing = setId?.let(lib::findSet)
    if (setId != null && existing == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }; return }
    val readOnly = existing?.isBuiltIn == true

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var drafts by remember { mutableStateOf(listOf<DraftLayout>()) }
    var nextKey by remember { mutableIntStateOf(0) }
    var draftStart by remember { mutableIntStateOf(0) }
    var switchTo by remember { mutableStateOf<SwitchTarget>(SwitchTarget.Next) }
    var picking by remember { mutableStateOf<PickFor?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    fun addDraft(src: SetSource, label: String) {
        if (drafts.size >= LayoutSet.MAX_LAYOUTS) return
        drafts = drafts + DraftLayout(nextKey++, src, label)
    }
    fun addTo(set: LayoutSet, src: SetSource) { store.update { l -> l.addToSet(set.id, src, switchTo)?.first ?: l } }
    fun addLayout(src: SetSource, label: String) = if (existing == null) addDraft(src, label) else addTo(existing, src)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.systemBarsPadding().padding(s.dp(16)).widthIn(max = s.dp(640)).background(NebulaColors.raised, RoundedCornerShape(s.dp(18)))
                .verticalScroll(rememberScrollState()).padding(s.dp(20)).testTag(if (existing == null) "new-set-flow" else "set-editor"),
            verticalArrangement = Arrangement.spacedBy(s.dp(14)),
        ) {
            PanelHeader("Layout set", existing?.let { "${it.name} · ${it.members.size} layouts" } ?: "New set", onDismiss)

            if (!readOnly) {
                Field("Name") {
                    Box(Modifier.testTag("set-name")) {
                        TextInput(name, { v ->
                            name = v.take(LayoutSet.MAX_NAME)
                            if (existing != null && name.isNotBlank()) store.update { it.renameSet(existing.id, name) }
                        }, "e.g. GTA V")
                    }
                }
            }

            // ---- the layouts, in switch order
            val count = existing?.members?.size ?: drafts.size
            Text(
                if (count == 0) "Layouts" else "Layouts · $count of ${LayoutSet.MAX_LAYOUTS}, in switch order",
                style = Nebula.type.label, color = NebulaColors.textSecondary,
            )
            if (existing != null) {
                val members = existing.members.mapNotNull(lib::find)
                val cycle = existing.cycleOrder()
                members.forEachIndexed { i, p ->
                    LayoutRow(
                        index = i, count = members.size, title = p.name, nameEditable = false,
                        start = existing.startId() == p.id, inCycle = p.id in cycle, readOnly = readOnly,
                        onName = {}, onStart = { store.update { it.updateSet(existing.copy(start = p.id)) } },
                        onCycle = {
                            val next = if (p.id in cycle) cycle - p.id else existing.members.filter { it in cycle || it == p.id }
                            if (next.isNotEmpty()) store.update { it.updateSet(existing.copy(cycle = next)) }
                        },
                        onMove = { by -> store.update { it.updateSet(existing.copy(members = existing.members.move(p.id, by))) } },
                        onRemove = { existing.without(p.id)?.let { w -> store.update { it.updateSet(w) } } },
                        onOpen = onOpenLayout?.let { open -> { onDismiss(); open(p) } },
                        tag = "set-layout:${p.id}",
                    )
                }
            } else {
                if (drafts.isEmpty()) {
                    Text("Add the layouts this game needs: one for walking, one for driving…", style = Nebula.type.body, color = NebulaColors.textMuted)
                }
                drafts.forEachIndexed { i, d ->
                    LayoutRow(
                        index = i, count = drafts.size, title = d.name, nameEditable = d.source !is SetSource.Existing,
                        start = draftStart == i, inCycle = d.inCycle, readOnly = false,
                        onName = { v -> drafts = drafts.map { if (it.key == d.key) it.copy(name = v.take(ProfileLibrary.MAX_NAME)) else it } },
                        onStart = { draftStart = i },
                        onCycle = { if (!d.inCycle || drafts.count { it.inCycle } > 1) drafts = drafts.map { if (it.key == d.key) it.copy(inCycle = !it.inCycle) else it } },
                        onMove = { by ->
                            val j = (i + by).coerceIn(0, drafts.lastIndex)
                            if (j != i) {
                                val startKey = drafts.getOrNull(draftStart)?.key
                                drafts = drafts.toMutableList().also { val x = it.removeAt(i); it.add(j, x) }
                                draftStart = drafts.indexOfFirst { it.key == startKey }.coerceAtLeast(0)
                            }
                        },
                        onRemove = {
                            val startKey = drafts.getOrNull(draftStart)?.key
                            drafts = drafts.filterNot { it.key == d.key }
                            draftStart = drafts.indexOfFirst { it.key == startKey }.coerceAtLeast(0)
                        },
                        onOpen = null,
                        tag = "draft-layout:$i",
                    )
                }
            }

            if (!readOnly && count < LayoutSet.MAX_LAYOUTS) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                    SmallActionTagged("New layout", Icons.Rounded.Add, "set-add-new") {
                        val n = count + 1
                        addLayout(SetSource.Blank("Layout $n"), "Layout $n")
                    }
                    SmallActionTagged("Copy a layout", Icons.Rounded.ContentCopy, "set-add-copy") { picking = PickFor.COPY }
                    SmallActionTagged("Add one I have", Icons.Rounded.SportsEsports, "set-add-existing") { picking = PickFor.EXISTING }
                }
            }

            if (!readOnly) {
                Field("The switch button", "Each layout without one gets a switch button at the top centre, clear of its controls; move it in the editor.") {
                    Segmented(listOf("Next layout" to "next", "Pick from a list" to "picker"), if (switchTo == SwitchTarget.Picker) "picker" else "next") { v ->
                        switchTo = if (v == "picker") SwitchTarget.Picker else SwitchTarget.Next
                    }
                }
                Text(
                    "Start: the layout a stream begins on. Cycle: the layouts Next steps through, in order; the others are reached from a picker switch.",
                    style = Nebula.type.label, color = NebulaColors.textMuted,
                )
            }

            // ---- actions
            if (existing == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                    NebulaButton(
                        if (drafts.isEmpty()) "Add a layout first" else "Create set",
                        onClick = {
                            if (drafts.isEmpty()) return@NebulaButton
                            val sources = drafts.map { d ->
                                when (val src = d.source) {
                                    is SetSource.Blank -> SetSource.Blank(d.name)
                                    is SetSource.Copy -> src.copy(name = d.name)
                                    is SetSource.Existing -> src
                                }
                            }
                            var made: LayoutSet? = null
                            store.update { l ->
                                l.newSet(name.ifBlank { "My layout set" }, sources, draftStart, drafts.map { it.inCycle }, switchTo)
                                    ?.also { made = it.second }?.first ?: l
                            }
                            made?.let { set ->
                                if (gameKey != null) store.update { it.assign(gameKey, set.id) }
                                onCreated(set)
                            }
                            onDismiss()
                        },
                        modifier = Modifier.testTag("create-set"),
                    )
                    NebulaButton("Cancel", onClick = onDismiss, style = ButtonStyle.Ghost)
                }
                if (gameKey != null) Text("The new set is used for this game right away.", style = Nebula.type.label, color = NebulaColors.textMuted)
            } else {
                val used = gameKey != null && lib.assignedTo(gameKey) == existing.id
                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                    if (gameKey != null) {
                        if (used) SmallAction("Used for this game", null, {}) else SmallActionTagged("Use for this game", null, "set-use") { store.update { it.assign(gameKey, existing.id) } }
                    }
                    SmallActionTagged("Share set", Icons.Rounded.Share, "set-share") { sharing = true }
                    if (!readOnly) SmallAction("Delete set", Icons.Rounded.Delete, { deleting = true }, danger = true)
                    NebulaButton("Done", onClick = onDismiss, compact = true)
                }
            }
        }
    }

    picking?.let { mode ->
        PickProfileDialog(lib, mode, exclude = existing?.members.orEmpty().toSet() + drafts.mapNotNull { (it.source as? SetSource.Existing)?.profileId }, onPick = { p ->
            picking = null
            when (mode) {
                PickFor.COPY -> addLayout(SetSource.Copy(p.id, "${p.name} copy".take(ProfileLibrary.MAX_NAME)), "${p.name} copy".take(ProfileLibrary.MAX_NAME))
                PickFor.EXISTING -> addLayout(SetSource.Existing(p.id), if (p.isBuiltIn) "My ${p.name}" else p.name)
            }
        }, onDismiss = { picking = null })
    }
    if (sharing && existing != null) {
        val layouts = existing.members.mapNotNull(lib::find)
        val start = existing.startId()?.let(lib::find) ?: layouts.firstOrNull()
        if (start == null) sharing = false else ShareLayoutDialog(
            start, onMeta = { m -> store.update { it.updateSet(existing.copy(meta = m)) } }, onDismiss = { sharing = false },
            set = existing, setLayouts = layouts,
        )
    }
    if (deleting && existing != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = NebulaColors.raised,
            title = { Text("Delete ${existing.name}?", style = Nebula.type.heading, color = NebulaColors.text) },
            text = {
                Text(
                    "Games that use it go back to the default. You can keep its ${existing.members.size} layouts as separate profiles, or delete them too.",
                    style = Nebula.type.body, color = NebulaColors.textSecondary,
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                    NebulaButton("Keep layouts", onClick = { deleting = false; store.update { it.deleteSet(existing.id) }; onDismiss() }, style = ButtonStyle.Secondary, modifier = Modifier.testTag("delete-set-keep"))
                    NebulaButton("Delete all", onClick = { deleting = false; store.update { it.deleteSet(existing.id, withLayouts = true) }; onDismiss() }, style = ButtonStyle.Danger)
                }
            },
            dismissButton = { NebulaButton("Cancel", onClick = { deleting = false }, style = ButtonStyle.Ghost) },
            shape = RoundedCornerShape(s.dp(18)),
        )
    }
}

private enum class PickFor { COPY, EXISTING }

/** Pick a profile to copy (any, set layouts included) or to move in (one of yours not in a set, or Standard, which is copied). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickProfileDialog(lib: ProfileLibrary, mode: PickFor, exclude: Set<String>, onPick: (ControlsProfile) -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val groups = lib.groups()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        modifier = Modifier.testTag("pick-profile"),
        title = { Text(if (mode == PickFor.COPY) "Copy which layout?" else "Add which layout?", style = Nebula.type.heading, color = NebulaColors.text) },
        text = {
            Column(Modifier.heightIn(max = s.dp(420)).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
                val loose = groups.filterIsInstance<ProfileGroup.Single>().map { it.profile }.filter { it.id !in exclude }
                if (mode == PickFor.EXISTING) {
                    Text("It moves into the set (Standard is copied). To keep it on its own too, copy it instead.", style = Nebula.type.label, color = NebulaColors.textMuted)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(6)), verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
                    loose.forEach { p -> Chip(p.name, selected = false, modifier = Modifier.testTag("pick:${p.id}")) { onPick(p) } }
                }
                if (mode == PickFor.COPY) {
                    groups.filterIsInstance<ProfileGroup.SetItem>().forEach { g ->
                        Text(g.set.name, style = Nebula.type.label, color = NebulaColors.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(6)), verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
                            g.layouts.forEach { p -> Chip(p.name, selected = false, modifier = Modifier.testTag("pick:${p.id}")) { onPick(p) } }
                        }
                    }
                }
                if (loose.isEmpty() && mode == PickFor.EXISTING) Text("Every layout you have is already in a set.", style = Nebula.type.body, color = NebulaColors.textSecondary)
            }
        },
        confirmButton = { NebulaButton("Cancel", onClick = onDismiss, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}

/** One layout in a set: number, name (editable for layouts not made yet), Start, Cycle, move, remove, open. */
@Composable
private fun LayoutRow(
    index: Int,
    count: Int,
    title: String,
    nameEditable: Boolean,
    start: Boolean,
    inCycle: Boolean,
    readOnly: Boolean,
    onName: (String) -> Unit,
    onStart: () -> Unit,
    onCycle: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onOpen: (() -> Unit)?,
    tag: String,
) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Column(
        Modifier.fillMaxWidth().background(NebulaColors.surface, shape).border(1.dp, if (start) NebulaColors.accentText else NebulaColors.border, shape)
            .padding(horizontal = s.dp(12), vertical = s.dp(10)).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(s.dp(8)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(10))) {
            Text("${index + 1}", style = Nebula.type.bodyStrong, color = NebulaColors.accentText, modifier = Modifier.widthIn(min = s.dp(16)))
            Box(Modifier.weight(1f)) {
                if (nameEditable && !readOnly) TextInput(title, onName, "Layout name")
                else Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onOpen != null && !readOnly) ToolIcon(Icons.Rounded.Edit, "Edit $title in the editor", onOpen)
        }
        if (!readOnly) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(6))) {
                Chip("Start", selected = start, modifier = Modifier.testTag("$tag:start"), onClick = onStart)
                Chip(if (inCycle) "In cycle" else "Picker only", selected = inCycle, modifier = Modifier.testTag("$tag:cycle"), onClick = onCycle)
                Box(Modifier.weight(1f))
                ToolIcon(Icons.Rounded.KeyboardArrowUp, "Move $title up", { onMove(-1) }, enabled = index > 0)
                ToolIcon(Icons.Rounded.KeyboardArrowDown, "Move $title down", { onMove(1) }, enabled = index < count - 1)
                ToolIcon(Icons.Rounded.Remove, "Remove $title from the set", onRemove, enabled = count > 1 || nameEditable)
            }
        } else {
            Text(listOfNotNull("Start".takeIf { start }, if (inCycle) "In cycle" else "Picker only").joinToString(" · "), style = Nebula.type.label, color = NebulaColors.textMuted)
        }
    }
}

@Composable
private fun SmallActionTagged(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, tag: String, onClick: () -> Unit) {
    Box(Modifier.testTag(tag)) { SmallAction(text, icon, onClick) }
}

internal fun List<String>.move(id: String, by: Int): List<String> {
    val i = indexOf(id)
    val j = (i + by).coerceIn(0, lastIndex)
    if (i < 0 || i == j) return this
    return toMutableList().also { it.removeAt(i); it.add(j, id) }
}

// ---------------------------------------------------------------- a set as one item in a list

/**
 * A layout set as ONE row of a profiles list ("GTA V · 5 layouts"), expanding to its layouts.
 * [current] is the layout open (editor) or on screen (stream); [onLayout] picks one.
 */
@Composable
internal fun SetGroupRow(
    g: ProfileGroup.SetItem,
    current: String?,
    tags: List<String>,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onLayout: (ControlsProfile) -> Unit,
    actions: @Composable () -> Unit,
) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    val holds = g.layouts.any { it.id == current }
    Column(
        Modifier.fillMaxWidth().background(if (holds) NebulaColors.accentTint else NebulaColors.surface, shape)
            .border(1.dp, if (holds) NebulaColors.accentText else NebulaColors.border, shape).testTag("group:${g.set.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().nebulaClickable(shape, { onExpand(!expanded) }, role = Role.Button)
                .padding(horizontal = s.dp(14), vertical = s.dp(11)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.dp(12)),
        ) {
            Icon(Icons.Rounded.Layers, null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(20)))
            Column(Modifier.weight(1f)) {
                Text(g.title, style = Nebula.type.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((listOf("Layout set") + tags).joinToString(" · "), style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                if (expanded) "Hide the layouts of ${g.set.name}" else "Show the layouts of ${g.set.name}",
                tint = NebulaColors.textSecondary, modifier = Modifier.size(s.dp(22)),
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = s.dp(14), end = s.dp(14), bottom = s.dp(12)), verticalArrangement = Arrangement.spacedBy(s.dp(4))) {
                val start = g.set.startId()
                g.layouts.forEach { p ->
                    val on = p.id == current
                    val rs = RoundedCornerShape(s.dp(10))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = s.dp(44)).nebulaClickable(rs, { onLayout(p) }, role = Role.RadioButton)
                            .background(if (on) NebulaColors.accent.copy(alpha = 0.28f) else androidx.compose.ui.graphics.Color.Transparent, rs)
                            .padding(start = s.dp(32), end = s.dp(10)).testTag("group-layout:${p.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.name, style = Nebula.type.body, color = NebulaColors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull("Start".takeIf { p.id == start }, "Open".takeIf { on }, "${p.landscape.size} controls").joinToString(" · "),
                            style = Nebula.type.label, color = NebulaColors.textMuted,
                        )
                    }
                }
                Box(Modifier.padding(start = s.dp(32), top = s.dp(6))) { actions() }
            }
        }
    }
}
