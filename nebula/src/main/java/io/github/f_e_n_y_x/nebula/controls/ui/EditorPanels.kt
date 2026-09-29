package io.github.f_e_n_y_x.nebula.controls.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ControlCamera
import androidx.compose.material.icons.rounded.Crop75
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.f_e_n_y_x.nebula.controls.Binding
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.ControlsFormatException
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.CrownImport
import io.github.f_e_n_y_x.nebula.controls.DefaultProfiles
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.ElementShape
import io.github.f_e_n_y_x.nebula.controls.LayoutOrientation
import io.github.f_e_n_y_x.nebula.controls.MacroStep
import io.github.f_e_n_y_x.nebula.controls.MouseKey
import io.github.f_e_n_y_x.nebula.controls.PadFlags
import io.github.f_e_n_y_x.nebula.controls.PressMode
import io.github.f_e_n_y_x.nebula.controls.ProfileImport
import io.github.f_e_n_y_x.nebula.controls.ProfileJson
import io.github.f_e_n_y_x.nebula.controls.Side
import io.github.f_e_n_y_x.nebula.controls.StickOutput
import io.github.f_e_n_y_x.nebula.controls.VirtualKeys
import io.github.f_e_n_y_x.nebula.controls.describe
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.NebulaConfirmDialog
import io.github.f_e_n_y_x.nebula.ui.components.SliderField
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.screens.Segmented
import io.github.f_e_n_y_x.nebula.ui.screens.ToggleRow
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.rounded.QrCodeScanner
import java.util.UUID
import kotlin.math.roundToInt

// ---------------------------------------------------------------- shared bits

@Composable
internal fun PanelHeader(eyebrow: String, title: String, onClose: () -> Unit) {
    val s = Nebula.scale
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = Nebula.type.eyebrow, color = NebulaColors.accentText)
            Text(title, style = Nebula.type.heading, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(s.dp(8)))
        ToolIcon(Icons.Rounded.Close, "Close panel", onClose)
    }
}

@Composable
internal fun Field(title: String, help: String? = null, content: @Composable () -> Unit) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        Text(title, style = Nebula.type.label, color = NebulaColors.textSecondary)
        content()
        if (help != null) Text(help, style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}

@Composable
internal fun SmallAction(text: String, icon: ImageVector?, onClick: () -> Unit, danger: Boolean = false) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    Row(
        Modifier.heightIn(min = s.dp(40)).nebulaClickable(shape, onClick)
            .background(if (danger) NebulaColors.dangerTint else NebulaColors.surface, shape)
            .border(1.dp, if (danger) NebulaColors.danger else NebulaColors.controlBorder, shape)
            .padding(horizontal = s.dp(12), vertical = s.dp(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (danger) NebulaColors.danger else NebulaColors.textSecondary, modifier = Modifier.size(s.dp(18)))
            Spacer(Modifier.width(s.dp(6)))
        }
        Text(text, style = Nebula.type.label, color = if (danger) NebulaColors.danger else NebulaColors.text)
    }
}

@Composable
internal fun TextInput(value: String, onChange: (String) -> Unit, placeholder: String) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = Nebula.type.body.copy(color = NebulaColors.text), cursorBrush = SolidColor(NebulaColors.accentText),
        modifier = Modifier.fillMaxWidth().background(NebulaColors.surface, shape).border(1.dp, NebulaColors.controlBorder, shape)
            .padding(horizontal = s.dp(12), vertical = s.dp(10)),
        decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, style = Nebula.type.body, color = NebulaColors.textMuted); inner() },
    )
}

/** A binding slot: tap to pick what it sends. */
@Composable
internal fun BindingRow(title: String, binding: Binding, allowNone: Boolean = true, onPick: (Binding) -> Unit) {
    val s = Nebula.scale
    var picking by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(s.dp(10))
    Row(
        Modifier.fillMaxWidth().nebulaClickable(shape, { picking = true }).background(NebulaColors.surface, shape)
            .border(1.dp, NebulaColors.controlBorder, shape).padding(horizontal = s.dp(12), vertical = s.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Nebula.type.label, color = NebulaColors.textSecondary, modifier = Modifier.weight(1f))
        Text(binding.describe(), style = Nebula.type.bodyStrong, color = if (binding == Binding.None) NebulaColors.textMuted else NebulaColors.text)
    }
    if (picking) BindingPicker(title, binding, allowNone, onDismiss = { picking = false }) { picking = false; onPick(it) }
}

// ---------------------------------------------------------------- inspector

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Inspector(
    e: ControlElement,
    globalOpacity: Float,
    onEdit: (String?, (ControlElement) -> ControlElement) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onFront: () -> Unit,
    onDone: () -> Unit,
) {
    val s = Nebula.scale
    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(s.dp(18)),
        verticalArrangement = Arrangement.spacedBy(s.dp(16)),
    ) {
        PanelHeader(e.kind.label, e.label.ifBlank { e.kind.label }, onDone)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            SmallAction("Duplicate", Icons.Rounded.ContentCopy, onDuplicate)
            SmallAction("To front", Icons.Rounded.FlipToFront, onFront)
            SmallAction("Delete", Icons.Rounded.Delete, onDelete, danger = true)
        }
        if (e.kind != ElementKind.DPAD) {
            Field("Label") { TextInput(e.label, { v -> onEdit("label") { it.copy(label = v.take(24)) } }, "No label") }
        }
        if (e.areaSized) ZoneAreaField(e, onEdit) else Field("Size") {
            SliderField(
                label = if (e.keepsAspect()) "Size" else "Width", value = e.width, range = ControlElement.MIN_SIZE_DP..ControlElement.MAX_SIZE_DP, step = 2f, unit = "dp",
                onValueChange = { v -> onEdit("w") { if (it.keepsAspect()) it.copy(width = v, height = v) else it.copy(width = v) } },
            )
            if (!e.keepsAspect()) {
                SliderField(
                    label = "Height", value = e.height, range = ControlElement.MIN_SIZE_DP..ControlElement.MAX_SIZE_DP, step = 2f, unit = "dp",
                    onValueChange = { v -> onEdit("h") { it.copy(height = v) } },
                )
            }
        }
        Field("Opacity", "Multiplied by the overlay transparency setting (now ${(globalOpacity * 100).roundToInt()}% opaque), so one slider in the stream menu still fades every control.") {
            SliderField(
                label = "Opacity", value = e.opacity * 100f, range = 10f..100f, step = 5f, unit = "%",
                onValueChange = { v -> onEdit("opacity") { it.copy(opacity = v / 100f) } },
            )
        }
        when (e.kind) {
            ElementKind.BUTTON, ElementKind.TRIGGER -> {
                Field("Sends") { BindingRow("Press", e.binding, allowNone = e.lookThrough) { b -> onEdit(null) { it.copy(bindings = listOf(b)) } } }
                PressModeField(e, onEdit)
                if (e.kind == ElementKind.BUTTON) ShapeField(e, onEdit)
                RoleField(e, onEdit)
            }
            ElementKind.COMBO -> {
                Field("Sends together", "Up to five, pressed at once while held. Keys work too, e.g. Ctrl + Shift + Esc.") {
                    e.bindings.forEachIndexed { i, b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                BindingRow("Key ${i + 1}", b, allowNone = false) { nb -> onEdit(null) { it.copy(bindings = it.bindings.toMutableList().also { l -> l[i] = nb }) } }
                            }
                            if (e.bindings.size > 1) ToolIcon(Icons.Rounded.Close, "Remove key ${i + 1}", { onEdit(null) { it.copy(bindings = it.bindings.filterIndexed { j, _ -> j != i }) } })
                        }
                    }
                    if (e.bindings.size < 5) SmallAction("Add key", null, { onEdit(null) { it.copy(bindings = it.bindings + Binding.Pad(PadFlags.A)) } })
                }
                PressModeField(e, onEdit)
                ShapeField(e, onEdit)
            }
            ElementKind.MACRO -> {
                Field("Steps", "Played in order once per tap: each is held, then there's a pause before the next.") {
                    e.steps.forEachIndexed { i, st ->
                        Column(
                            Modifier.fillMaxWidth().border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(12))).padding(s.dp(10)),
                            verticalArrangement = Arrangement.spacedBy(s.dp(6)),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    BindingRow("Step ${i + 1}", st.binding) { nb -> onEdit(null) { it.copy(steps = it.steps.toMutableList().also { l -> l[i] = st.copy(binding = nb) }) } }
                                }
                                if (e.steps.size > 1) ToolIcon(Icons.Rounded.Close, "Remove step ${i + 1}", { onEdit(null) { it.copy(steps = it.steps.filterIndexed { j, _ -> j != i }) } })
                            }
                            SliderField(
                                label = "Hold", value = st.holdMs.toFloat(), range = 20f..1000f, step = 10f, unit = "ms",
                                onValueChange = { v -> onEdit("hold$i") { it.copy(steps = it.steps.toMutableList().also { l -> l[i] = st.copy(holdMs = v.roundToInt()) }) } },
                            )
                            SliderField(
                                label = "Then wait", value = st.gapMs.toFloat(), range = 0f..1000f, step = 10f, unit = "ms",
                                onValueChange = { v -> onEdit("gap$i") { it.copy(steps = it.steps.toMutableList().also { l -> l[i] = st.copy(gapMs = v.roundToInt()) }) } },
                            )
                        }
                    }
                    if (e.steps.size < 16) SmallAction("Add step", null, { onEdit(null) { it.copy(steps = it.steps + MacroStep(Binding.Pad(PadFlags.A))) } })
                }
                ShapeField(e, onEdit)
            }
            ElementKind.DPAD -> DirectionFields(e, onEdit)
            ElementKind.STICK -> {
                Field("Moves") {
                    Segmented(StickOutput.entries.map { it.label to it }, e.stick) { o ->
                        onEdit(null) {
                            val label = when {
                                it.label != "LS" && it.label != "RS" -> it.label
                                o == StickOutput.LEFT -> "LS"
                                o == StickOutput.RIGHT -> "RS"
                                else -> "WASD"
                            }
                            it.copy(stick = o, label = label, bindings = if (o == StickOutput.KEYS && it.bindings.size < 4) WASD else it.bindings)
                        }
                    }
                }
                if (e.stick == StickOutput.KEYS) DirectionFields(e, onEdit)
                Field("Press the stick", "A quick tap sends this (L3 / R3 on a gamepad).") {
                    BindingRow("Tap", e.click) { b -> onEdit(null) { it.copy(click = b) } }
                }
                ToggleRow("Floating stick", "The stick starts wherever your thumb lands inside this area; the area itself stays invisible while playing.", e.floating) { v ->
                    onEdit(null) { it.copy(floating = v) }
                }
                Field("Dead zone") {
                    SliderField(
                        label = "Dead zone", value = e.deadzone * 100f, range = 0f..50f, step = 1f, unit = "%",
                        onValueChange = { v -> onEdit("deadzone") { it.copy(deadzone = v / 100f) } },
                    )
                }
                if (e.stick != StickOutput.RIGHT) MoveFields(e, onEdit)
            }
            ElementKind.ZONE -> ZoneFields(e, onEdit)
            ElementKind.TOUCHPAD -> {
                Field("Tap sends") { BindingRow("Tap", e.click) { b -> onEdit(null) { it.copy(click = b) } } }
                Field("Pointer speed", "Drag inside the pad to move the PC's mouse.") {
                    SliderField(
                        label = "Pointer speed", value = e.sensitivity * 100f, range = 10f..400f, step = 10f, unit = "%",
                        onValueChange = { v -> onEdit("sens") { it.copy(sensitivity = v / 100f) } },
                    )
                }
            }
        }
    }
}

/** A zone's area: quick halves plus width and height as a share of the screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneAreaField(e: ControlElement, onEdit: (String?, (ControlElement) -> ControlElement) -> Unit) {
    val s = Nebula.scale
    Field("Area", "Drag the zone to move it; drag its corner or pinch to resize.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            SmallAction("Right half", null, { onEdit(null) { it.copy(x = 0.75f, y = 0.5f, width = 0.5f, height = 1f) } })
            SmallAction("Left half", null, { onEdit(null) { it.copy(x = 0.25f, y = 0.5f, width = 0.5f, height = 1f) } })
            SmallAction("Whole screen", null, { onEdit(null) { it.copy(x = 0.5f, y = 0.5f, width = 1f, height = 1f) } })
        }
        Text("Width", style = Nebula.type.label, color = NebulaColors.textMuted)
        SliderField(
            label = "Width", value = e.width * 100f, range = ControlElement.MIN_ZONE * 100f..100f, step = 5f, unit = "%",
            onValueChange = { v -> onEdit("zw") { it.copy(width = v / 100f) } },
        )
        Text("Height", style = Nebula.type.label, color = NebulaColors.textMuted)
        SliderField(
            label = "Height", value = e.height * 100f, range = ControlElement.MIN_ZONE * 100f..100f, step = 5f, unit = "%",
            onValueChange = { v -> onEdit("zh") { it.copy(height = v / 100f) } },
        )
    }
}

@Composable
private fun ZoneFields(e: ControlElement, onEdit: (String?, (ControlElement) -> ControlElement) -> Unit) {
    Field("Type", e.zone.help) {
        Segmented(io.github.f_e_n_y_x.nebula.controls.ZoneType.entries.map { it.label to it }, e.zone) { z -> onEdit(null) { it.copy(zone = z) } }
    }
    if (e.zone == io.github.f_e_n_y_x.nebula.controls.ZoneType.FLOATING_STICK) {
        Field("Moves") {
            Segmented(listOf("Left stick" to StickOutput.LEFT, "Right stick" to StickOutput.RIGHT, "Direction keys" to StickOutput.KEYS), e.stick) { o ->
                onEdit(null) { it.copy(stick = o, bindings = if (o == StickOutput.KEYS && it.bindings.size < 4) WASD else it.bindings) }
            }
        }
        if (e.stick == StickOutput.KEYS) DirectionFields(e, onEdit)
        if (e.stick != StickOutput.RIGHT) MoveFields(e, onEdit)
    } else if (e.zone != io.github.f_e_n_y_x.nebula.controls.ZoneType.CAMERA_MOUSE) {
        Field("Stick") {
            Segmented(listOf("Left stick" to StickOutput.LEFT, "Right stick" to StickOutput.RIGHT), if (e.stick == StickOutput.LEFT) StickOutput.LEFT else StickOutput.RIGHT) { o ->
                onEdit(null) { it.copy(stick = o) }
            }
        }
    }
    if (e.zone == io.github.f_e_n_y_x.nebula.controls.ZoneType.CAMERA_STICK) {
        Field("Minimum push", "Every swipe pushes the stick at least this far, past the game's own stick deadzone. 22 % suits GTA V; use 0–5 % if the game's aim deadzone is 0.") {
            Segmented(listOf("0 %" to 0f, "5 %" to 0.05f, "12 %" to 0.12f, "22 %" to 0.22f, "35 %" to 0.35f), listOf(0f, 0.05f, 0.12f, 0.22f, 0.35f).minByOrNull { kotlin.math.abs(it - e.antiDeadzone) } ?: 0.22f) { v -> onEdit(null) { it.copy(antiDeadzone = v) } }
        }
    }
    if (e.zone == io.github.f_e_n_y_x.nebula.controls.ZoneType.FLOATING_STICK) {
        ToggleRow("Show the ring", "Draw the stick where your thumb is while you hold it.", e.showRing) { v -> onEdit(null) { it.copy(showRing = v) } }
        Field("Dead zone") {
            SliderField(
                label = "Dead zone", value = e.deadzone * 100f, range = 0f..50f, step = 1f, unit = "%",
                onValueChange = { v -> onEdit("deadzone") { it.copy(deadzone = v / 100f) } },
            )
        }
    } else {
        Field("Sensitivity", if (e.zone == io.github.f_e_n_y_x.nebula.controls.ZoneType.CAMERA_STICK) "How fast a drag reaches a full stick push." else "Mouse movement per finger movement.") {
            SliderField(
                label = "Sensitivity", value = e.sensitivity * 100f, range = 10f..400f, step = 10f, unit = "%",
                onValueChange = { v -> onEdit("sens") { it.copy(sensitivity = v / 100f) } },
            )
        }
        Field("Acceleration", "100% is linear. Higher makes slow drags finer and flicks faster.") {
            SliderField(
                label = "Acceleration", value = e.acceleration * 100f, range = 50f..250f, step = 10f, unit = "%",
                onValueChange = { v -> onEdit("accel") { it.copy(acceleration = v / 100f) } },
            )
        }
        ToggleRow("Invert Y", "Drag up to look down, like a flight stick.", e.invertY) { v -> onEdit(null) { it.copy(invertY = v) } }
    }
    ToggleRow(
        "Keep with controller",
        "Stays active when a physical controller hides the other on-screen controls; its stick joins the controller's (move with the pad, aim with your thumb).",
        e.keepWithController,
    ) { v -> onEdit(null) { it.copy(keepWithController = v) } }
}

private val WASD = listOf(0x57, 0x53, 0x41, 0x44).map { Binding.Key(it) }
private val ARROWS = listOf(0x26, 0x28, 0x25, 0x27).map { Binding.Key(it) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionFields(e: ControlElement, onEdit: (String?, (ControlElement) -> ControlElement) -> Unit) {
    val s = Nebula.scale
    Field("Directions") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            SmallAction("Gamepad D-pad", null, { onEdit(null) { it.copy(bindings = PadFlags.DPAD.map { f -> Binding.Pad(f) }) } })
            SmallAction("WASD", null, { onEdit(null) { it.copy(bindings = WASD) } })
            SmallAction("Arrow keys", null, { onEdit(null) { it.copy(bindings = ARROWS) } })
        }
        listOf("Up", "Down", "Left", "Right").forEachIndexed { i, name ->
            BindingRow(name, e.bindings.getOrElse(i) { Binding.None }) { b ->
                onEdit(null) { el -> el.copy(bindings = List(4) { j -> if (j == i) b else el.bindings.getOrElse(j) { Binding.None } }) }
            }
        }
    }
}

/** Auto-sprint and run lock for a move stick, as in mobile shooters. */
@Composable
private fun MoveFields(e: ControlElement, onEdit: (String?, (ControlElement) -> ControlElement) -> Unit) {
    Field("Auto-sprint", "Held while you push the stick forward past its ring: L3 on a gamepad, Shift on a keyboard. None turns it off.") {
        BindingRow("Sprint", e.sprint) { b -> onEdit(null) { it.copy(sprint = b) } }
    }
    if (e.sprint != Binding.None) {
        Field("Sprint starts at") {
            SliderField(
                label = "Sprint starts at", value = e.sprintAt * 100f, range = 100f..200f, step = 5f, unit = "% of the ring",
                onValueChange = { v -> onEdit("sprintAt") { it.copy(sprintAt = v / 100f) } },
            )
        }
    }
    ToggleRow(
        "Run lock",
        "Drag up to the lock above the stick and let go to keep running (and sprinting); touch the stick again to stop.",
        e.runLock,
    ) { v -> onEdit(null) { it.copy(runLock = v) } }
}

/** What the element is for; fire and aim buttons can gate the gyro ("While aiming or firing"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleField(e: ControlElement, onEdit: (String?, (ControlElement) -> ControlElement) -> Unit) {
    val s = Nebula.scale
    Field("Used for", "Fire and Aim count as aiming for the gyro's \"While aiming or firing\". The others only describe the layout when you share it.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(6)), verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
            io.github.f_e_n_y_x.nebula.controls.ElementRole.entries.filter { it != io.github.f_e_n_y_x.nebula.controls.ElementRole.MOVE }.forEach { r ->
                Chip(r.label, r == e.role) { onEdit(null) { it.copy(role = r) } }
            }
        }
    }
}

@Composable
private fun PressModeField(e: ControlElement, onEdit: (String?, (ControlElement) -> ControlElement) -> Unit) {
    Field("Behaviour", when (e.mode) {
        PressMode.TOGGLE -> "Tap once to hold it down, tap again to let go."
        PressMode.MIXED -> "A quick tap keeps it held until the next tap; a long press holds it only while your finger stays (the usual aim button in mobile shooters)."
        PressMode.HOLD -> "Held while your finger is on it."
    }) {
        Segmented(PressMode.entries.map { it.label to it }, e.mode) { m -> onEdit(null) { it.copy(mode = m) } }
    }
    if (e.kind == ElementKind.BUTTON || e.kind == ElementKind.TRIGGER) {
        Field("Fire and look", "Pressed at once; drag the same finger to aim too, as fast as the look area (the right fire button in mobile shooters, or an eye button for free look).") {
            Segmented(listOf("Off" to false, "On" to true), e.lookThrough) { v -> onEdit(null) { it.copy(lookThrough = v) } }
        }
    }
}

@Composable
private fun ShapeField(e: ControlElement, onEdit: (String?, (ControlElement) -> ControlElement) -> Unit) {
    Field("Shape") {
        Segmented(ElementShape.entries.map { it.label to it }, e.shape) { sh ->
            onEdit(null) { if (sh == ElementShape.ROUND) it.copy(shape = sh, height = it.width) else it.copy(shape = sh) }
        }
    }
}

// ---------------------------------------------------------------- binding picker

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BindingPicker(title: String, current: Binding, allowNone: Boolean, onDismiss: () -> Unit, onPick: (Binding) -> Unit) {
    val s = Nebula.scale
    var tab by remember { mutableStateOf(if (current is Binding.Key) 1 else if (current is Binding.Mouse || current is Binding.Wheel) 2 else 0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(s.dp(18))
        Column(
            Modifier.widthIn(max = s.dp(560)).fillMaxWidth(0.94f).fillMaxHeight(0.86f)
                .background(NebulaColors.raised, shape).border(1.dp, NebulaColors.border, shape).padding(s.dp(18)),
            verticalArrangement = Arrangement.spacedBy(s.dp(12)),
        ) {
            PanelHeader("Sends", title, onDismiss)
            Segmented(listOf("Gamepad" to 0, "Keyboard" to 1, "Mouse" to 2), tab) { tab = it }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(s.dp(12))) {
                @Composable
                fun chips(items: List<Binding>) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                        items.forEach { b -> Chip(b.describe(), b == current) { onPick(b) } }
                    }
                }
                when (tab) {
                    0 -> {
                        chips(listOf(PadFlags.A, PadFlags.B, PadFlags.X, PadFlags.Y).map { Binding.Pad(it) })
                        chips(listOf(Binding.Pad(PadFlags.LB), Binding.Pad(PadFlags.RB), Binding.Trigger(Side.LEFT), Binding.Trigger(Side.RIGHT)))
                        chips(listOf(PadFlags.BACK, PadFlags.START, PadFlags.GUIDE, PadFlags.LS_CLK, PadFlags.RS_CLK).map { Binding.Pad(it) })
                        chips(PadFlags.DPAD.map { Binding.Pad(it) })
                        chips(listOf(PadFlags.PADDLE1, PadFlags.PADDLE2, PadFlags.PADDLE3, PadFlags.PADDLE4, PadFlags.TOUCHPAD, PadFlags.MISC).map { Binding.Pad(it) })
                    }
                    1 -> VirtualKeys.pickerGroups.forEach { (group, keys) ->
                        Text(group, style = Nebula.type.label, color = NebulaColors.textMuted)
                        chips(keys.map { Binding.Key(it) })
                    }
                    else -> {
                        chips(MouseKey.entries.map { Binding.Mouse(it) })
                        chips(listOf(Binding.Wheel(true), Binding.Wheel(false)))
                    }
                }
            }
            if (allowNone) NebulaButton("Nothing", onClick = { onPick(Binding.None) }, style = ButtonStyle.Secondary)
        }
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    Box(
        modifier.heightIn(min = s.dp(40)).widthIn(min = s.dp(44)).nebulaClickable(shape, onClick, role = Role.RadioButton)
            .background(if (selected) NebulaColors.accent else NebulaColors.surface, shape)
            .border(1.dp, if (selected) NebulaColors.accentText else NebulaColors.controlBorder, shape)
            .padding(horizontal = s.dp(12), vertical = s.dp(9)),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Nebula.type.label, color = if (selected) Color.White else NebulaColors.text) }
}

// ---------------------------------------------------------------- element library

private data class LibraryItem(val kind: ElementKind, val icon: ImageVector, val help: String)

private val libraryItems = listOf(
    LibraryItem(ElementKind.BUTTON, Icons.Rounded.Circle, "A gamepad button, PC key or mouse button. Hold or toggle."),
    LibraryItem(ElementKind.STICK, Icons.Rounded.ControlCamera, "Left or right analog stick, or four direction keys. Can float."),
    LibraryItem(ElementKind.DPAD, Icons.Rounded.Games, "Four directions with diagonals: D-pad, WASD or arrows."),
    LibraryItem(ElementKind.TRIGGER, Icons.Rounded.Crop75, "LT or RT, fully pressed while held."),
    LibraryItem(ElementKind.ZONE, Icons.Rounded.PanTool, "A screen area for camera look (stick or mouse) or a floating joystick. Can keep working with a controller."),
    LibraryItem(ElementKind.TOUCHPAD, Icons.Rounded.TouchApp, "A region that moves the PC's mouse; tap to click."),
    LibraryItem(ElementKind.COMBO, Icons.Rounded.Link, "Several buttons or keys pressed together."),
    LibraryItem(ElementKind.MACRO, Icons.Rounded.Repeat, "A timed sequence of presses, played once per tap."),
)

@Composable
internal fun ElementLibrary(onPick: (ElementKind) -> Unit, onClose: () -> Unit) {
    val s = Nebula.scale
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(s.dp(18)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        PanelHeader("Add", "Controls", onClose)
        libraryItems.forEach { item ->
            val shape = RoundedCornerShape(s.dp(12))
            Row(
                Modifier.fillMaxWidth().nebulaClickable(shape, { onPick(item.kind) }).background(NebulaColors.surface, shape)
                    .padding(horizontal = s.dp(14), vertical = s.dp(12)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(s.dp(40)).background(NebulaColors.accentTint, RoundedCornerShape(s.dp(10))), contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(22)))
                }
                Spacer(Modifier.width(s.dp(12)))
                Column(Modifier.weight(1f)) {
                    Text(item.kind.label, style = Nebula.type.bodyStrong, color = NebulaColors.text)
                    Text(item.help, style = Nebula.type.label, color = NebulaColors.textMuted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- profiles

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfilesPanel(
    store: ControlsStore,
    session: EditSession,
    gameKey: String?,
    gameName: String?,
    orientation: LayoutOrientation,
    onOpen: (ControlsProfile) -> Unit,
    onReplaceLayout: (List<io.github.f_e_n_y_x.nebula.controls.ControlElement>) -> Unit,
    onChanged: () -> Unit,
    onClose: () -> Unit,
) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val data by store.data.collectAsState()
    val lib = remember(data) { store.library(data) }
    val current = session.base
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf<ControlsProfile?>(null) }
    var importPreview by remember { mutableStateOf<ProfileImport.Outcome?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var linkToFetch by remember { mutableStateOf<String?>(null) }

    fun open(p: ControlsProfile) { if (session.dirty && p.id != current.id) pendingOpen = p else onOpen(p) }

    /** Parses and validates; nothing is saved until the preview's Add. */
    fun importText(text: String) {
        val link = io.github.f_e_n_y_x.nebula.controls.LayoutLink.urlOf(text.trim())
        if (link != null) { linkToFetch = link; return }
        val dm = ctx.resources.displayMetrics
        try {
            importPreview = ProfileImport.parse(text, CrownImport.Basis(dm.widthPixels, dm.heightPixels, dm.density), "p-" + UUID.randomUUID().toString().take(12), System.currentTimeMillis())
        } catch (e: ControlsFormatException) {
            importError = e.message
        }
    }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            if (text == null || text.length > 4_000_000) importError = "Couldn't read that file" else importText(text)
        }
    }

    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(s.dp(18)), verticalArrangement = Arrangement.spacedBy(s.dp(16))) {
        PanelHeader("Profiles", current.name, onClose)

        val suggested = io.github.f_e_n_y_x.nebula.controls.DefaultProfiles.suggestedFor(gameName)?.let { lib.find(it) }
        if (suggested != null && gameKey != null && lib.assignedTo(gameKey) != suggested.id) {
            Field("Suggested for ${gameName ?: "this game"}", "${suggested.name}: plays with touch alone; a floating move stick, fire-and-look triggers, swipe right to look.") {
                SmallAction("Use ${suggested.name}", null, {
                    store.update { l -> l.assign(gameKey, suggested.id) }
                    open(suggested)
                    onChanged()
                })
            }
        }

        if (gameKey != null) {
            val assigned = lib.assignedTo(gameKey)
            val default = lib.resolve(null)
            Field(
                "${gameName ?: "This game"} uses",
                if (assigned == null) "Following the default (${default.name})." else "Its own profile; other games keep the default.",
            ) {
                Segmented(listOf("This profile" to true, "Default" to false), assigned == current.id) { mine ->
                    store.update { l -> l.assign(gameKey, if (mine) current.id else null) }
                    onChanged()
                }
            }
        }

        val legacy = remember { io.github.f_e_n_y_x.nebula.settings.LegacyPrefs(ctx) }
        var outside by remember(current.id) { mutableStateOf(io.github.f_e_n_y_x.nebula.controls.OutsideTouch.read(legacy, current)) }
        Field("Touches outside controls", outside.help) {
            Segmented(io.github.f_e_n_y_x.nebula.controls.OutsideTouch.entries.map { it.label to it }, outside) { v ->
                outside = v
                io.github.f_e_n_y_x.nebula.controls.OutsideTouch.write(legacy, current.id, v)
            }
        }
        var lookOut by remember(current.id) { mutableStateOf(io.github.f_e_n_y_x.nebula.controls.LookOutput.read(legacy, current)) }
        Field("Look with", lookOut.help + " Used by the Look area and by fire-and-look buttons.") {
            Segmented(io.github.f_e_n_y_x.nebula.controls.LookOutput.entries.map { it.label to it }, lookOut) { v ->
                lookOut = v
                io.github.f_e_n_y_x.nebula.controls.LookOutput.write(legacy, current.id, v)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            if (!current.isBuiltIn) SmallAction("Rename", null, { renaming = true })
            SmallAction("Duplicate", Icons.Rounded.ContentCopy, {
                var copy: ControlsProfile? = null
                store.update { l -> l.add(session.build().copy(name = "${current.name} copy", origin = null)).also { copy = it.second }.first }
                // The copy holds the unsaved edits; the original stays as last saved.
                copy?.let { onOpen(it) }
            })
            if (lib.resolve(null).id != current.id) SmallAction("Make default", null, { store.update { it.setDefault(current.id) }; onChanged() })
            SmallAction("Share", Icons.Rounded.Share, { sharing = true })
            if (orientation == LayoutOrientation.PORTRAIT) SmallAction("Copy landscape layout", null, { onReplaceLayout(current.landscape) })
            SmallAction("Reset to standard", null, {
                val std = DefaultProfiles.standard(store.standardOptions())
                onReplaceLayout(std.layout(orientation))
            })
            if (!current.isBuiltIn) SmallAction("Delete", Icons.Rounded.Delete, { confirmDelete = true }, danger = true)
        }

        Text("All profiles", style = Nebula.type.label, color = NebulaColors.textSecondary)
        lib.all.forEach { p ->
            val shape = RoundedCornerShape(s.dp(12))
            val on = p.id == current.id
            Row(
                Modifier.fillMaxWidth().nebulaClickable(shape, { open(p) }, role = Role.RadioButton)
                    .background(if (on) NebulaColors.accentTint else NebulaColors.surface, shape)
                    .border(1.dp, if (on) NebulaColors.accentText else Color.Transparent, shape)
                    .padding(horizontal = s.dp(14), vertical = s.dp(11)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = Nebula.type.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val tags = buildList {
                        if (p.isBuiltIn) add("Built in")
                        if (p.origin == "crown") add("From V+")
                        if (lib.resolve(null).id == p.id) add("Default")
                        if (gameKey != null && lib.assignedTo(gameKey) == p.id) add("This game")
                        val games = data.games.count { it.value == p.id }
                        if (games > 0 && !(gameKey != null && games == 1 && lib.assignedTo(gameKey) == p.id)) add("$games game${if (games == 1) "" else "s"}")
                        add("${p.landscape.size} controls")
                    }
                    Text(tags.joinToString(" · "), style = Nebula.type.label, color = NebulaColors.textMuted)
                }
            }
        }

        Field("Layout library", "Ready-made and shared layouts, by game. Each one is checked and previewed before it's added.") {
            NebulaButton("Browse layouts", onClick = { browsing = true }, style = ButtonStyle.Secondary, modifier = Modifier.testTag("browse-layouts"))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            SmallAction("New profile", null, {
                var added: ControlsProfile? = null
                store.update { l -> l.add(DefaultProfiles.standard(store.standardOptions()).copy(name = "New controls", origin = null)).also { added = it.second }.first }
                added?.let { open(it) }
            })
            SmallAction("Import file", Icons.Rounded.FileUpload, { openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) })
            SmallAction("Paste", null, {
                val clip = ctx.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip
                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                if (text.isNullOrBlank()) importError = "The clipboard is empty" else importText(text)
            })
            SmallAction("Scan QR", Icons.Rounded.QrCodeScanner, { scanning = true })
        }
        Text(
            "Import takes Nebula layout files, share codes and links, older Nebula profiles and V+ Crown exports (.crown.json or .mdat). Group buttons, wheel pads and V+-only actions are left out; you'll see which.",
            style = Nebula.type.label, color = NebulaColors.textMuted,
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(current.name) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = NebulaColors.raised,
            title = { Text("Rename profile", style = Nebula.type.heading, color = NebulaColors.text) },
            text = { TextInput(name, { name = it.take(40) }, "Profile name") },
            confirmButton = {
                NebulaButton("Rename", onClick = {
                    renaming = false
                    val next = store.update { it.rename(current.id, name) }
                    next.find(current.id)?.let { session.base = session.base.copy(name = it.name) }
                    onChanged()
                })
            },
            dismissButton = { NebulaButton("Cancel", onClick = { renaming = false }, style = ButtonStyle.Ghost) },
            shape = RoundedCornerShape(s.dp(18)),
        )
    }
    if (confirmDelete) {
        NebulaConfirmDialog(
            title = "Delete ${current.name}?",
            text = "Games that use it go back to the default profile. This can't be undone.",
            confirm = "Delete",
            onConfirm = {
                confirmDelete = false
                val next = store.update { it.delete(current.id) }
                session.open(next.resolve(gameKey))
                onChanged()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    pendingOpen?.let { p ->
        NebulaConfirmDialog(
            title = "Switch without saving?",
            text = "Your changes to ${current.name} will be lost.",
            confirm = "Switch",
            onConfirm = { pendingOpen = null; onOpen(p) },
            onDismiss = { pendingOpen = null },
        )
    }
    importPreview?.let { r ->
        LayoutPreviewDialog(r, onAdd = { p ->
            var added: ControlsProfile? = null
            store.update { l -> l.add(p).also { added = it.second }.first }
            importPreview = null
            added?.let { a ->
                android.widget.Toast.makeText(ctx, "Added ${a.name}", android.widget.Toast.LENGTH_SHORT).show()
                open(a)
            }
        }, onDismiss = { importPreview = null })
    }
    if (sharing) {
        ShareLayoutDialog(session.build(), onMeta = { m -> session.base = session.base.copy(meta = m) }, onDismiss = { sharing = false })
    }
    if (browsing) {
        LayoutBrowserDialog(store, onAdded = { a -> browsing = false; open(a) }, onDismiss = { browsing = false })
    }
    if (scanning) {
        QrScanDialog(onCode = { code -> scanning = false; importText(code) }, onDismiss = { scanning = false })
    }
    linkToFetch?.let { url ->
        LaunchedEffect(url) {
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { fetcher(ctx).fetch(url, io.github.f_e_n_y_x.nebula.controls.LayoutFile.MAX_BYTES).body }
            }
            linkToFetch = null
            r.onSuccess { importText(it) }.onFailure { e -> importError = (e as? ControlsFormatException)?.message ?: "Couldn't download the layout: ${e.message ?: e.javaClass.simpleName}" }
        }
    }
    importError?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { importError = null },
            containerColor = NebulaColors.raised,
            title = { Text("Couldn't import", style = Nebula.type.heading, color = NebulaColors.text) },
            text = { Text(msg, style = Nebula.type.body, color = NebulaColors.textSecondary) },
            confirmButton = { NebulaButton("OK", onClick = { importError = null }) },
            shape = RoundedCornerShape(s.dp(18)),
        )
    }
}
