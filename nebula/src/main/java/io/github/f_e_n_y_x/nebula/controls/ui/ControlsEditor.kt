package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.controls.Box as SnapBox
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.ElementShape
import io.github.f_e_n_y_x.nebula.controls.LayoutEditor
import io.github.f_e_n_y_x.nebula.controls.LayoutOrientation
import io.github.f_e_n_y_x.nebula.controls.Snapping
import io.github.f_e_n_y_x.nebula.settings.overlayOpacity
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Grid step and snap reach, in dp. */
internal const val GRID_DP = 16f
private const val SNAP_DP = 6f

internal enum class Panel { NONE, INSPECTOR, LIBRARY, PROFILES }

/** Which edge a panel docks to: the side away from what's being edited. */
internal enum class Dock { START, END, TOP, BOTTOM }

/**
 * The working copy the editor changes: the profile as last saved ([base]) and one
 * [LayoutEditor] per orientation that has been opened, each with its own undo history.
 */
internal class EditSession(var base: ControlsProfile) {
    private val editors = HashMap<LayoutOrientation, LayoutEditor>()
    private val ids = { "e-" + UUID.randomUUID().toString().take(8) }

    fun editor(o: LayoutOrientation): LayoutEditor = editors.getOrPut(o) { LayoutEditor(base.layout(o), ids) }

    /** The profile with every opened layout's current elements. */
    fun build(): ControlsProfile {
        var p = base
        editors.forEach { (o, ed) ->
            val untouchedPortrait = o == LayoutOrientation.PORTRAIT && base.portrait == null && ed.elements == base.landscape && !ed.canUndo
            if (!untouchedPortrait) p = p.withLayout(o, ed.elements)
        }
        return p
    }

    val dirty: Boolean get() = build() != base

    fun open(p: ControlsProfile) {
        base = p
        editors.clear()
    }
}

/**
 * The on-screen controls editor, drawn over [background] (the live stream, or a preview). Add,
 * move (grid + alignment snapping), resize (corner handle or pinch), restyle and rebind
 * elements, undo/redo, and manage profiles. [gameKey] enables "use for this game".
 */
@Composable
fun ControlsEditor(
    store: ControlsStore,
    gameKey: String?,
    gameName: String?,
    startProfileId: String? = null,
    onClose: () -> Unit,
    background: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val data by store.data.collectAsState()
    val lib = remember(data) { store.library(data) }
    val session = remember {
        EditSession(lib.find(startProfileId) ?: lib.resolve(gameKey))
    }
    val form = Nebula.form
    val orientation = if (form.isLandscape || form.widthDp > form.heightDp) LayoutOrientation.LANDSCAPE else LayoutOrientation.PORTRAIT
    var rev by remember { mutableIntStateOf(0) }
    // Reading rev makes every edit recompose the editor (not the live controls, which never do this).
    @Suppress("UNUSED_VARIABLE") val observed = rev
    val editor = session.editor(orientation)
    fun act(block: LayoutEditor.() -> Unit) { editor.block(); rev++ }

    var gridOn by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var confirmClose by remember { mutableStateOf(false) }
    var guides by remember { mutableStateOf<Pair<List<Float>, List<Float>>>(emptyList<Float>() to emptyList()) }
    val globalOpacity = remember { overlayOpacity(ctx) / 100f }
    val selected = editor.selected
    LaunchedEffect(editor.selectedId) {
        if (editor.selectedId != null && panel != Panel.PROFILES) panel = Panel.INSPECTOR
        if (editor.selectedId == null && panel == Panel.INSPECTOR) panel = Panel.NONE
    }

    val save: () -> Unit = {
        val p = session.build()
        val oldId = p.id
        var saved = p
        store.update { l ->
            val (next, s) = l.save(p)
            saved = s
            if (gameKey != null && s.id != oldId && next.assignedTo(gameKey) == oldId) next.assign(gameKey, s.id) else next
        }
        // Keep editing the saved copy with its history intact.
        session.base = saved
        rev++
    }
    val close: () -> Unit = { if (session.dirty) confirmClose = true else onClose() }
    BackHandler {
        when {
            panel == Panel.LIBRARY || panel == Panel.PROFILES -> panel = if (editor.selectedId != null) Panel.INSPECTOR else Panel.NONE
            editor.selectedId != null -> act { select(null) }
            else -> close()
        }
    }

    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        background()
        // A light scrim so the controls read as editable, not live.
        Box(Modifier.fillMaxSize().background(Color(0x66000000)))

        BoxWithConstraints(Modifier.controlsArea()) {
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val gridPx = with(density) { GRID_DP.dp.toPx() }
            if (gridOn) {
                Canvas(Modifier.fillMaxSize()) {
                    val dot = 1.2.dp.toPx()
                    var x = 0f
                    while (x <= size.width) {
                        var y = 0f
                        while (y <= size.height) {
                            drawCircle(Color(0x33FFFFFF), dot, Offset(x, y)); y += gridPx
                        }
                        x += gridPx
                    }
                }
            }
            // Empty space: tap to deselect, pinch to resize the selected element.
            Box(
                Modifier.fillMaxSize().pointerInput(editor) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pinched = false
                        var moved = 0f
                        do {
                            val ev = awaitPointerEvent()
                            if (ev.changes.count { it.pressed } >= 2) {
                                val sel = editor.selected
                                if (sel != null) {
                                    if (!pinched) { editor.beginGesture(); pinched = true }
                                    val z = ev.calculateZoom()
                                    resizeBy(editor, sel.id, z, gridOn)
                                    rev++
                                }
                            } else {
                                moved += ev.changes.firstOrNull()?.positionChange()?.getDistance() ?: 0f
                            }
                            ev.changes.forEach { it.consume() }
                        } while (ev.changes.any { it.pressed })
                        if (pinched) { editor.endGesture(); rev++ } else if (moved < 12f) { act { select(null) } }
                    }
                },
            )
            Canvas(Modifier.fillMaxSize()) {
                val (gx, gy) = guides
                gx.forEach { drawLine(NebulaColors.accentText, Offset(it, 0f), Offset(it, size.height), 1.dp.toPx()) }
                gy.forEach { drawLine(NebulaColors.accentText, Offset(0f, it), Offset(size.width, it), 1.dp.toPx()) }
            }
            // Zones sit under everything else, as they do while playing.
            editor.elements.sortedBy { it.kind != ElementKind.ZONE }.forEach { e ->
                key(e.id) {
                    EditableElement(
                        e = e, selected = e.id == editor.selectedId, areaW = w, areaH = h, opacity = globalOpacity,
                        editor = editor, gridOn = gridOn, gridPx = gridPx,
                        onGuides = { guides = it }, onChanged = { rev++ },
                    )
                }
            }
            selected?.let { sel ->
                ResizeHandle(sel, w, h, editor, gridOn) { rev++ }
            }
        }

        // Where panels go: away from the selected element.
        val sideDock = form.isLandscape || !form.isCompact
        val dock = when {
            sideDock -> if ((selected?.x ?: 0f) > 0.5f) Dock.START else Dock.END
            else -> if ((selected?.y ?: 1f) > 0.5f) Dock.TOP else Dock.BOTTOM
        }
        val toolbarAtBottom = !sideDock && dock == Dock.TOP && panel != Panel.NONE

        EditorToolbar(
            profileName = session.base.name + if (session.dirty) " •" else "",
            orientationLabel = layoutLabel(orientation, session.base),
            canUndo = editor.canUndo, canRedo = editor.canRedo, gridOn = gridOn, dirty = session.dirty,
            onClose = close,
            onProfiles = { panel = if (panel == Panel.PROFILES) Panel.NONE else Panel.PROFILES },
            onUndo = { act { undo() } }, onRedo = { act { redo() } },
            onGrid = { gridOn = !gridOn },
            onAdd = { panel = if (panel == Panel.LIBRARY) Panel.NONE else Panel.LIBRARY },
            onSave = save,
            // A side panel takes one edge; the toolbar moves to the other so neither covers the other.
            modifier = Modifier.align(
                when {
                    toolbarAtBottom -> Alignment.BottomCenter
                    sideDock && panel != Panel.NONE && dock == Dock.START -> Alignment.TopEnd
                    sideDock && panel != Panel.NONE && dock == Dock.END -> Alignment.TopStart
                    else -> Alignment.TopCenter
                },
            ),
        )

        if (panel == Panel.NONE && editor.selectedId == null) {
            Hint(
                if (editor.elements.isEmpty()) "Tap + to add a button, stick, D-pad or touchpad"
                else "Tap to select · drag to move · pinch or drag the corner to resize",
                // Under the toolbar, where no default control sits.
                Modifier.align(Alignment.TopCenter).padding(top = Nebula.scale.dp(76)),
            )
        }

        AnimatedVisibility(panel != Panel.NONE, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            EditorPanel(dock, sideDock) {
                when (panel) {
                    Panel.INSPECTOR -> selected?.let { sel ->
                        Inspector(
                            e = sel, globalOpacity = globalOpacity,
                            onEdit = { mergeKey, change -> act { edit(sel.id, mergeKey, change) } },
                            onDuplicate = { act { duplicate(sel.id) } },
                            onDelete = { act { delete(sel.id) } },
                            onFront = { act { bringToFront(sel.id) } },
                            onDone = { act { select(null) } },
                        )
                    }
                    Panel.LIBRARY -> ElementLibrary(
                        onPick = { kind ->
                            act { if (kind == ElementKind.ZONE) add(kind, 0.75f, 0.5f) else add(kind, 0.5f, if (orientation == LayoutOrientation.PORTRAIT) 0.72f else 0.5f) }
                            panel = Panel.INSPECTOR
                        },
                        onClose = { panel = Panel.NONE },
                    )
                    Panel.PROFILES -> ProfilesPanel(
                        store = store, session = session, gameKey = gameKey, gameName = gameName, orientation = orientation,
                        onOpen = { p -> session.open(p); rev++ },
                        onReplaceLayout = { list -> act { replaceAll(list) } },
                        onChanged = { rev++ },
                        onClose = { panel = if (editor.selectedId != null) Panel.INSPECTOR else Panel.NONE },
                    )
                    Panel.NONE -> Unit
                }
            }
        }
    }

    if (confirmClose) {
        UnsavedDialog(
            onSave = { confirmClose = false; save(); onClose() },
            onDiscard = { confirmClose = false; onClose() },
            onDismiss = { confirmClose = false },
        )
    }
}

internal fun layoutLabel(o: LayoutOrientation, p: ControlsProfile) = when {
    o == LayoutOrientation.LANDSCAPE -> "Landscape layout"
    p.portrait == null -> "Portrait layout · starts as a copy of landscape"
    else -> "Portrait layout"
}

/** Elements that stay square (sticks, D-pads, round buttons) resize both sides together. */
internal fun ControlElement.keepsAspect() = kind == ElementKind.DPAD || (kind == ElementKind.STICK && !floating) ||
    (shape == ElementShape.ROUND && width == height && kind != ElementKind.TOUCHPAD)

private fun resizeBy(editor: LayoutEditor, id: String, zoom: Float, gridOn: Boolean) {
    val e = editor.elements.firstOrNull { it.id == id } ?: return
    // Snap only coarse pinches to the grid, or small steps get eaten.
    if (e.areaSized) { editor.resizeTo(id, e.width * zoom, e.height * zoom); return }
    val nw = Snapping.size(e.width * zoom, GRID_DP / 2, false)
    val nh = if (e.keepsAspect()) nw else Snapping.size(e.height * zoom, GRID_DP / 2, false)
    editor.resizeTo(id, if (gridOn) nw.roundToInt().toFloat() else nw, if (gridOn) nh.roundToInt().toFloat() else nh)
}

@Composable
private fun EditableElement(
    e: ControlElement,
    selected: Boolean,
    areaW: Int,
    areaH: Int,
    opacity: Float,
    editor: LayoutEditor,
    gridOn: Boolean,
    gridPx: Float,
    onGuides: (Pair<List<Float>, List<Float>>) -> Unit,
    onChanged: () -> Unit,
) {
    val look = remember { ElementLook() }
    val latched = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .elementBounds(e, areaW, areaH)
            .pointerInput(e.id, gridOn, areaW, areaH) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (editor.selectedId != e.id) { editor.select(e.id); onChanged() }
                    val start = editor.elements.firstOrNull { it.id == e.id } ?: return@awaitEachGesture
                    val sz = start.sizePx(areaW, areaH, density)
                    val wPx = sz.width.toFloat()
                    val hPx = sz.height.toFloat()
                    var left = start.x * areaW - wPx / 2
                    var top = start.y * areaH - hPx / 2
                    val others = editor.elements.filter { it.id != e.id }.map { o ->
                        val osz = o.sizePx(areaW, areaH, density)
                        val ow = osz.width.toFloat()
                        val oh = osz.height.toFloat()
                        SnapBox(o.x * areaW - ow / 2, o.y * areaH - oh / 2, ow, oh)
                    }
                    val threshold = with(density) { SNAP_DP.dp.toPx() }
                    var began = false
                    var travelled = 0f
                    var pinchBase = 0f
                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            // Two fingers on the element: pinch resize.
                            if (!began) { editor.beginGesture(); began = true }
                            val d = hypot(pressed[0].position.x - pressed[1].position.x, pressed[0].position.y - pressed[1].position.y)
                            if (pinchBase > 0f && d > 0f) { resizeBy(editor, e.id, d / pinchBase, gridOn); onChanged() }
                            pinchBase = d
                        } else {
                            pinchBase = 0f
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.first()
                            val delta = c.positionChange()
                            travelled += delta.getDistance()
                            if (travelled > viewConfiguration.touchSlop || began) {
                                if (!began) { editor.beginGesture(); began = true }
                                left += delta.x
                                top += delta.y
                                val r = Snapping.move(SnapBox(left, top, wPx, hPx), others, areaW.toFloat(), areaH.toFloat(), gridPx, threshold, gridOn)
                                editor.moveTo(e.id, (r.left + wPx / 2) / areaW, (r.top + hPx / 2) / areaH)
                                onGuides(r.guidesX to r.guidesY)
                                onChanged()
                            }
                        }
                        ev.changes.forEach { it.consume() }
                    } while (ev.changes.any { it.pressed })
                    onGuides(emptyList<Float>() to emptyList())
                    if (began) { editor.endGesture(); onChanged() }
                }
            }
            .semantics { contentDescription = "${e.kind.label} ${e.label}".trim() + if (selected) ", selected" else "" },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = (opacity * e.opacity).coerceAtLeast(if (e.kind == ElementKind.ZONE) 0.85f else 0.35f) }) {
            ElementFace(e, look, latched)
        }
        // Every element shows its bounds; the selected one in accent.
        Canvas(Modifier.fillMaxSize()) {
            val stroke = if (selected) 2.dp.toPx() else 1.dp.toPx()
            val inset = -3.dp.toPx()
            drawRoundRect(
                if (selected) NebulaColors.accentText else Color(0x55FFFFFF),
                topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(stroke, pathEffect = if (selected) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
            )
        }
        if (e.mode == io.github.f_e_n_y_x.nebula.controls.PressMode.TOGGLE) {
            Text(
                "toggle", style = Nebula.type.label.copy(fontSize = Nebula.type.label.fontSize * 0.7f), color = NebulaColors.accentText,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-14).dp).background(Color(0xCC0A0A0B), shape).padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ResizeHandle(e: ControlElement, areaW: Int, areaH: Int, editor: LayoutEditor, gridOn: Boolean, onChanged: () -> Unit) {
    val density = LocalDensity.current
    val touch = 36.dp
    Box(
        Modifier
            .offset {
                val sz = e.sizePx(areaW, areaH, this)
                val w = sz.width
                val h = sz.height
                val t = touch.roundToPx()
                // Kept on screen even for a zone that fills the screen.
                IntOffset(
                    (e.x * areaW + w / 2f - t / 2f + 3.dp.toPx()).roundToInt().coerceIn(0, (areaW - t).coerceAtLeast(0)),
                    (e.y * areaH + h / 2f - t / 2f + 3.dp.toPx()).roundToInt().coerceIn(0, (areaH - t).coerceAtLeast(0)),
                )
            }
            .size(touch)
            .semantics { contentDescription = "Resize" }
            .pointerInput(e.id, gridOn, areaW, areaH) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    editor.beginGesture()
                    val s0 = editor.elements.firstOrNull { it.id == e.id } ?: return@awaitEachGesture
                    var w = s0.width
                    var h = s0.height
                    do {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            val d = c.positionChange()
                            // The handle is the bottom-right corner; the centre stays put, so each side grows by 2×.
                            if (s0.areaSized) {
                                w += d.x * 2 / areaW
                                h += d.y * 2 / areaH
                            } else {
                                w += with(density) { (d.x * 2).toDp().value }
                                h += with(density) { (d.y * 2).toDp().value }
                            }
                            c.consume()
                        }
                        val cur = editor.elements.firstOrNull { it.id == e.id } ?: break
                        if (cur.areaSized) {
                            // Zones snap to 1/20 of the screen with the grid on.
                            val step = if (gridOn) 0.05f else 0f
                            editor.resizeTo(e.id, if (step > 0) Snapping.toGrid(w, step) else w, if (step > 0) Snapping.toGrid(h, step) else h)
                            onChanged()
                        } else {
                            val nw = Snapping.size(w, GRID_DP / 2, gridOn)
                            val nh = if (cur.keepsAspect()) nw else Snapping.size(h, GRID_DP / 2, gridOn)
                            editor.resizeTo(e.id, nw, nh)
                            onChanged()
                        }
                    } while (ev.changes.any { it.pressed })
                    editor.endGesture()
                    onChanged()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(18.dp).background(NebulaColors.accent, CircleShape).border(2.dp, Color.White, CircleShape))
    }
}

@Composable
private fun EditorToolbar(
    profileName: String,
    orientationLabel: String,
    canUndo: Boolean,
    canRedo: Boolean,
    gridOn: Boolean,
    dirty: Boolean,
    onClose: () -> Unit,
    onProfiles: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onGrid: () -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(50)
    val compact = Nebula.form.widthDp < 600
    Row(
        modifier
            .systemBarsPadding()
            .padding(top = s.dp(10), bottom = s.dp(10), start = s.dp(12), end = s.dp(12))
            .background(Color(0xF00E0E10), shape)
            .border(1.dp, NebulaColors.border, shape)
            .padding(horizontal = s.dp(6), vertical = s.dp(4)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.dp(2)),
    ) {
        ToolIcon(Icons.Rounded.Close, "Close editor", onClose)
        Row(
            Modifier.widthIn(max = s.dp(if (compact) 128 else 240)).nebulaClickable(RoundedCornerShape(50), onProfiles).padding(horizontal = s.dp(10), vertical = s.dp(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f, fill = false)) {
                Text(profileName, style = Nebula.type.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) Text(orientationLabel, style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ExpandMore, "Profiles", tint = NebulaColors.textSecondary, modifier = Modifier.size(s.dp(20)))
        }
        Divider()
        ToolIcon(Icons.AutoMirrored.Rounded.Undo, "Undo", onUndo, enabled = canUndo)
        ToolIcon(Icons.AutoMirrored.Rounded.Redo, "Redo", onRedo, enabled = canRedo)
        ToolIcon(if (gridOn) Icons.Rounded.GridOn else Icons.Rounded.GridOff, if (gridOn) "Grid and snapping on" else "Grid and snapping off", onGrid, active = gridOn)
        ToolIcon(Icons.Rounded.Add, "Add element", onAdd)
        Divider()
        Box(
            Modifier.nebulaClickable(shape, onSave).background(if (dirty) NebulaColors.accent else NebulaColors.raised, shape)
                .padding(horizontal = s.dp(if (compact) 10 else 14), vertical = s.dp(9))
                .semantics { contentDescription = if (dirty) "Save" else "Saved" },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = if (dirty) Color.White else NebulaColors.textSecondary, modifier = Modifier.size(s.dp(18)))
                if (!compact) {
                    Spacer(Modifier.width(s.dp(6)))
                    Text(if (dirty) "Save" else "Saved", style = Nebula.type.label, color = if (dirty) Color.White else NebulaColors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(NebulaColors.border))
}

@Composable
internal fun ToolIcon(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, active: Boolean = false) {
    val s = Nebula.scale
    Box(
        Modifier
            .size(s.dp(if (Nebula.form.widthDp < 600) 40 else 44))
            .alpha(if (enabled) 1f else 0.35f)
            .nebulaClickable(CircleShape, { if (enabled) onClick() })
            .background(if (active) NebulaColors.accentTint else Color.Transparent, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) NebulaColors.accentText else NebulaColors.text, modifier = Modifier.size(s.dp(22)))
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier) {
    val s = Nebula.scale
    Text(
        text, style = Nebula.type.label, color = NebulaColors.textSecondary,
        modifier = modifier.systemBarsPadding().padding(bottom = s.dp(16), start = s.dp(16), end = s.dp(16))
            .background(Color(0xE00A0A0B), RoundedCornerShape(50)).padding(horizontal = s.dp(14), vertical = s.dp(8)),
    )
}

/** A docked panel: a side sheet in landscape and on tablets, a top or bottom sheet on portrait phones. */
@Composable
internal fun EditorPanel(dock: Dock, side: Boolean, content: @Composable () -> Unit) {
    val s = Nebula.scale
    val form = Nebula.form
    Box(Modifier.fillMaxSize()) {
        val shape = when (dock) {
            Dock.START -> RoundedCornerShape(topEnd = s.dp(20), bottomEnd = s.dp(20))
            Dock.END -> RoundedCornerShape(topStart = s.dp(20), bottomStart = s.dp(20))
            Dock.TOP -> RoundedCornerShape(bottomStart = s.dp(20), bottomEnd = s.dp(20))
            Dock.BOTTOM -> RoundedCornerShape(topStart = s.dp(20), topEnd = s.dp(20))
        }
        val width = if (form.isCompact || form.heightDp < 500) s.dp(320) else s.dp(360)
        val m = when (dock) {
            Dock.START -> Modifier.align(Alignment.CenterStart).width(width).fillMaxHeight()
            Dock.END -> Modifier.align(Alignment.CenterEnd).width(width).fillMaxHeight()
            Dock.TOP -> Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(0.46f)
            Dock.BOTTOM -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.46f)
        }
        Box(
            m.background(Color(0xF50E0E10), shape).border(1.dp, NebulaColors.border, shape)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); do { val ev = awaitPointerEvent() } while (ev.changes.any { it.pressed }) } }
                .systemBarsPadding(),
        ) {
            content()
        }
    }
}

@Composable
private fun UnsavedDialog(onSave: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        titleContentColor = NebulaColors.text,
        textContentColor = NebulaColors.textSecondary,
        title = { Text("Save your changes?", style = Nebula.type.heading) },
        text = { Text("This layout has changes you haven't saved.", style = Nebula.type.body) },
        confirmButton = { NebulaButton("Save", onClick = onSave) },
        dismissButton = { NebulaButton("Discard", onClick = onDiscard, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}
