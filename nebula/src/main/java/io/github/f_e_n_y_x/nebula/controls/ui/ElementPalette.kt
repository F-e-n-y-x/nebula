package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.describe
import io.github.f_e_n_y_x.nebula.controls.groupTitle
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Remove
import kotlin.math.roundToInt
import io.github.f_e_n_y_x.nebula.controls.Palette
import io.github.f_e_n_y_x.nebula.controls.PaletteCategory
import io.github.f_e_n_y_x.nebula.controls.PaletteItem
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlin.math.min

/**
 * The "Add" panel: a searchable palette of every control, in categories, with a live preview
 * of each, and ready-made groups first. One tap places the item (see [Palette.place]).
 */
@Composable
internal fun ElementPalette(onPick: (PaletteItem) -> Unit, onClose: () -> Unit) {
    val s = Nebula.scale
    var query by rememberSaveable { mutableStateOf("") }
    // null = everything, sectioned by category.
    var category by rememberSaveable { mutableStateOf<PaletteCategory?>(null) }
    val results = remember(query, category) { Palette.search(query, category) }
    val grid = rememberLazyGridState()
    LaunchedEffect(query, category) { grid.scrollToItem(0) }

    Column(Modifier.fillMaxHeight().padding(start = s.dp(18), end = s.dp(18), top = s.dp(18)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
        PanelHeader("Add", "Controls & groups", onClose)
        TextInput(query, { query = it.take(40) }, "Search: ABXY, F5, wheel, WASD…")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(s.dp(6))) {
            Chip("All", category == null) { category = null }
            PaletteCategory.entries.forEach { c -> Chip(c.label, category == c) { category = if (category == c) null else c } }
        }
        if (results.isEmpty()) {
            Text(
                "Nothing matches “${query.trim()}”. Try a key name (Esc, F5), a button (LB, R3) or a kind (stick, zone, macro).",
                style = Nebula.type.label, color = NebulaColors.textMuted,
                modifier = Modifier.padding(top = s.dp(8)),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(s.dp(84)),
            state = grid,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("palette-grid"),
            horizontalArrangement = Arrangement.spacedBy(s.dp(8)),
            verticalArrangement = Arrangement.spacedBy(s.dp(8)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = s.dp(18)),
        ) {
            // Section headers only when browsing everything; a search or a category is one list.
            val sectioned = category == null && query.isBlank()
            if (sectioned) {
                PaletteCategory.entries.forEach { c ->
                    val inCat = results.filter { it.category == c }
                    if (inCat.isNotEmpty()) {
                        item(key = "h-${c.name}", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(c, inCat.size) }
                        items(inCat, key = { it.id }) { PaletteTile(it, onPick) }
                    }
                }
            } else {
                items(results, key = { it.id }) { PaletteTile(it, onPick) }
            }
        }
    }
}

@Composable
private fun SectionHeader(c: PaletteCategory, count: Int) {
    val s = Nebula.scale
    Row(Modifier.fillMaxWidth().padding(top = s.dp(6)), verticalAlignment = Alignment.CenterVertically) {
        Text(c.label.uppercase(), style = Nebula.type.eyebrow, color = NebulaColors.accentText, modifier = Modifier.weight(1f))
        Text("$count", style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}

@Composable
private fun PaletteTile(item: PaletteItem, onPick: (PaletteItem) -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Column(
        Modifier.fillMaxWidth()
            .nebulaClickable(shape, { onPick(item) })
            .background(NebulaColors.surface, shape)
            .border(1.dp, if (item.isGroup) NebulaColors.accentTint else NebulaColors.border, shape)
            .padding(s.dp(8))
            .semantics { contentDescription = "Add ${item.title}" + if (item.isGroup) ", group of ${item.parts.size}" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(s.dp(6)),
    ) {
        ItemPreview(item.parts, Modifier.fillMaxWidth().height(s.dp(50)))
        Text(
            item.title, style = Nebula.type.label, color = NebulaColors.text, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2,
        )
        if (item.isGroup) Text("${item.parts.size} controls", style = Nebula.type.label.copy(fontSize = Nebula.type.label.fontSize * 0.85f), color = NebulaColors.textMuted)
    }
}

/** Screen-sized zones are previewed at this size (dp). */
private const val ZONE_PREVIEW_W = 150f
private const val ZONE_PREVIEW_H = 90f

private val PREVIEW_SWITCH_CONTEXT = SwitchContext(current = "Layout", inSet = true)

private fun ControlElement.previewW() = if (areaSized) ZONE_PREVIEW_W else width
private fun ControlElement.previewH() = if (areaSized) ZONE_PREVIEW_H else height

/**
 * [parts] (dp offsets from the item centre, see [Palette]) drawn with the real element faces
 * at their natural size, then scaled down to fit the tile.
 */
@Composable
internal fun ItemPreview(parts: List<ControlElement>, modifier: Modifier = Modifier) {
    val look = remember { ElementLook() }
    val off = remember { mutableStateOf(false) }
    val boxes = remember(parts) { parts.map { it.copy(width = it.previewW(), height = it.previewH()) } }
    val b = remember(boxes) { Palette.bounds(boxes) }
    val bw = (b[2] - b[0]).coerceAtLeast(1f)
    val bh = (b[3] - b[1]).coerceAtLeast(1f)
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val scale = min(1f, min(maxWidth.value / bw, maxHeight.value / bh)) * 0.94f
        // A layout switch is drawn as it looks inside a set (outside one it is dimmed).
        CompositionLocalProvider(LocalSwitchContext provides PREVIEW_SWITCH_CONTEXT) {
            Box(Modifier.requiredSize(bw.dp, bh.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
                boxes.forEachIndexed { i, p ->
                    Box(
                        Modifier.offset((p.x - p.width / 2 - b[0]).dp, (p.y - p.height / 2 - b[1]).dp).size(p.width.dp, p.height.dp),
                    ) { ElementFace(parts[i], look, off, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- group inspector

/**
 * A selected group: move it by dragging any member, resize it with the corner handle, a pinch
 * or the buttons here, or split it into loose elements. Tapping a member (here or on screen)
 * edits that one control.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun GroupInspector(
    group: String,
    members: List<ControlElement>,
    areaW: Float,
    areaH: Float,
    globalOpacity: Float,
    onScale: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onSelectMember: (String) -> Unit,
    onDuplicate: () -> Unit,
    onFront: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    val s = Nebula.scale
    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(s.dp(18)),
        verticalArrangement = Arrangement.spacedBy(s.dp(16)),
    ) {
        PanelHeader("Group · ${members.size} controls", groupTitle(group), onDone)
        ItemPreview(
            remember(members, areaW, areaH) {
                // Members relative to the group's centre, at their current dp size.
                val cx = members.map { it.x }.average().toFloat()
                val cy = members.map { it.y }.average().toFloat()
                members.map { it.copy(x = (it.x - cx) * areaW, y = (it.y - cy) * areaH) }
            },
            Modifier.fillMaxWidth().height(s.dp(72)),
        )
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            SmallAction("Split into controls", androidx.compose.material.icons.Icons.AutoMirrored.Rounded.CallSplit, onSplit)
            SmallAction("Duplicate", androidx.compose.material.icons.Icons.Rounded.ContentCopy, onDuplicate)
            SmallAction("To front", androidx.compose.material.icons.Icons.Rounded.FlipToFront, onFront)
            SmallAction("Delete group", androidx.compose.material.icons.Icons.Rounded.Delete, onDelete, danger = true)
        }
        Field("Size", "Resizes every control and the gaps between them together. You can also drag the group's corner or pinch.") {
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                SmallAction("Smaller", androidx.compose.material.icons.Icons.Rounded.Remove, { onScale(1f / 1.1f) })
                SmallAction("Larger", androidx.compose.material.icons.Icons.Rounded.Add, { onScale(1.1f) })
            }
        }
        Field("Opacity", "Sets every control in the group; multiplied by the overlay setting (now ${(globalOpacity * 100).roundToInt()}% opaque).") {
            io.github.f_e_n_y_x.nebula.ui.components.SliderField(
                label = "Opacity", value = (members.map { it.opacity }.average().toFloat() * 100f), range = 10f..100f, step = 5f, unit = "%",
                onValueChange = { v -> onOpacity(v / 100f) },
            )
        }
        Field("Controls", "Tap one to change what it sends.") {
            members.forEach { m ->
                val shape = RoundedCornerShape(s.dp(10))
                Row(
                    Modifier.fillMaxWidth().nebulaClickable(shape, { onSelectMember(m.id) }).background(NebulaColors.surface, shape)
                        .border(1.dp, NebulaColors.controlBorder, shape).padding(horizontal = s.dp(12), vertical = s.dp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m.label.ifBlank { m.kind.label }, style = Nebula.type.bodyStrong, color = NebulaColors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.summary(), style = Nebula.type.label, color = NebulaColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun ControlElement.summary(): String = when (kind) {
    io.github.f_e_n_y_x.nebula.controls.ElementKind.STICK -> stick.label
    io.github.f_e_n_y_x.nebula.controls.ElementKind.DPAD -> "D-pad"
    else -> binding.describe()
}
