package io.github.f_e_n_y_x.nebula.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.settings.SettingKind
import io.github.f_e_n_y_x.nebula.settings.SettingSpec
import io.github.f_e_n_y_x.nebula.settings.legacySettings
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.SliderField
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import org.json.JSONObject

/** Keys that have a native control elsewhere in Settings, so they aren't listed twice. */
/** Frame generation's V+ keys are shown by framegen/FramegenSettingsSection.kt. */
internal val nativeKeys = setOf(
    "list_resolution", "list_fps", "seekbar_bitrate_kbps", "video_format", "checkbox_stretch_video",
    "pref_framegen_pick_lossless_dll", "checkbox_framegen_enabled", "checkbox_framegen_adaptive_enabled", "list_framegen_quality_preset",
    "pref_framegen_selftest", "seekbar_framegen_internal_width", "seekbar_framegen_slow_threshold_ms", "checkbox_framegen_present_real_first",
)

/** Actions from V+ that need screens not yet rebuilt; listed honestly instead of hidden. */
private val laterActions = mapOf(
    "capability_diagnostic" to "The video capability report is being rebuilt.",
    "use_external_display" to "External display output is being rebuilt.",
    "game_menu_cards" to "Configure the stream menu from inside a stream.",
    "list_perf_overlay_orientation" to "Set from the performance overlay while streaming.",
    "list_perf_overlay_position" to "Set from the performance overlay while streaming.",
    "perf_overlay_display_items" to "Set from the performance overlay while streaming.",
    "controller_diagnostic" to "The controller test screen is being rebuilt.",
    "controller_mouse_settings" to "Gamepad mouse settings are being rebuilt.",
    "stick_calibration" to "Stick calibration is being rebuilt.",
    "config_sync_select_directory" to "Use Export and Import to move settings between devices.",
    "crown_config_management" to "Not part of Nebula.",
    "check_for_updates" to "Nebula updates come from your own builds.",
    "pref_developer_unlock" to "Everything is unlocked in Nebula.",
    "sponsor_panel" to "Not part of Nebula.",
    "local_image_picker" to "Nebula uses game art instead of a background image.",
    "reset_background_image" to "Nebula uses game art instead of a background image.",
    "about_us" to "See About.",
    "documentation_handbook" to "See About.",
    "open_source_notices" to "See About.",
)

/** How the stream picture fills the screen. Stored as [SCALE_MODE_KEY] in V+'s preferences. */
enum class ScaleMode(val id: String) { FIT("fit"), FILL("fill"), STRETCH("stretch") }

const val SCALE_MODE_KEY = "nebula_scale_mode"
const val SCALE_VIRTUAL_KEY = "nebula_scale_virtual"

fun scaleModeOf(prefs: LegacyPrefs): ScaleMode {
    val id = prefs.prefs.all[SCALE_MODE_KEY] as? String
    return ScaleMode.entries.firstOrNull { it.id == id }
        ?: if (prefs.prefs.getBoolean("checkbox_stretch_video", false)) ScaleMode.STRETCH else ScaleMode.FIT
}

/** Every V+ preference for one [group], rendered with Nebula controls, grouped by V+ section. */
@Composable
internal fun LegacySettingsList(
    group: String,
    specs: List<SettingSpec> = legacySettings.filter { it.group == group && it.key !in nativeKeys },
    header: String? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { LegacyPrefs(ctx) }
    val tick by prefs.changes().collectAsState(initial = null)
    val s = Nebula.scale
    Column(Modifier.widthIn(max = s.dp(720)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
        header?.let { Text(it, style = Nebula.type.heading, color = NebulaColors.text, modifier = Modifier.padding(top = s.dp(12))) }
        var lastSection: String? = "\u0000"
        specs.forEach { spec ->
            if (spec.section != lastSection) {
                lastSection = spec.section
                spec.section?.let { SectionTitle(it, Modifier.padding(top = s.dp(14), bottom = s.dp(2))) }
            }
            androidx.compose.runtime.key(tick, spec.key) { LegacyRow(spec, prefs) }
        }
    }
}

@Composable
internal fun LegacyRow(spec: SettingSpec, prefs: LegacyPrefs) {
    val s = Nebula.scale
    val enabled = prefs.enabled(spec)
    val mod = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
    when (val k = spec.kind) {
        is SettingKind.Toggle -> ToggleRow(spec.title, spec.summary.orEmpty(), prefs.bool(spec, k.default)) { if (enabled) prefs.put(spec.key, it) }
        is SettingKind.Slider -> Column(mod.card(s).padding(horizontal = s.dp(16), vertical = s.dp(12))) {
            Text(spec.title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            spec.summary?.let { Text(it, style = Nebula.type.label, color = NebulaColors.textMuted) }
            Spacer(Modifier.height(s.dp(8)))
            val div = k.divisor.coerceAtLeast(1).toFloat()
            SliderField(
                label = spec.title,
                value = prefs.int(spec, k.default) / div,
                range = (k.min / div)..(k.max / div).coerceAtLeast(k.min / div + 1f),
                step = (k.step / div).coerceAtLeast(0.01f),
                unit = k.unit.trim(),
                onValueChange = { v -> if (enabled) prefs.put(spec.key, (v * div).toInt().coerceIn(k.min, k.max)) },
            )
        }
        is SettingKind.Pick -> PickRow(spec, k, prefs, enabled, mod)
        is SettingKind.Text -> TextRow(spec, k, prefs, enabled, mod)
        is SettingKind.Action -> ActionRow(spec, prefs, mod)
    }
}

private fun Modifier.card(s: io.github.f_e_n_y_x.nebula.ui.theme.NebulaScale) =
    this.background(NebulaColors.surface, RoundedCornerShape(s.dp(12)))

@Composable
private fun PickRow(spec: SettingSpec, k: SettingKind.Pick, prefs: LegacyPrefs, enabled: Boolean, mod: Modifier) {
    val s = Nebula.scale
    val current = prefs.string(spec, k.default)
    val label = k.choices.firstOrNull { it.value == current }?.label ?: current ?: "—"
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(s.dp(12))
    Row(
        mod.nebulaClickable(shape, { if (enabled) open = true }, role = Role.DropdownList).card(s).padding(horizontal = s.dp(16), vertical = s.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(spec.title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            spec.summary?.let { Text(it, style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 3) }
        }
        Spacer(Modifier.width(s.dp(12)))
        // The value never takes more than ~40% of the row, so the title keeps room to read.
        Text(label, style = Nebula.type.label, color = NebulaColors.accentText, maxLines = 2, modifier = Modifier.weight(0.65f, fill = false))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NebulaColors.textMuted, modifier = Modifier.size(s.dp(20)))
    }
    if (open) ChoiceDialog(spec.title, k.choices, current, onPick = { prefs.put(spec.key, it); open = false }, onDismiss = { open = false })
}

@Composable
internal fun ChoiceDialog(title: String, choices: List<io.github.f_e_n_y_x.nebula.settings.Choice>, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val first = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        titleContentColor = NebulaColors.text,
        title = { Text(title, style = Nebula.type.heading) },
        text = {
            Column(Modifier.heightIn(max = s.dp(420)).verticalScroll(rememberScrollState())) {
                choices.forEachIndexed { i, c ->
                    val on = c.value == current
                    val shape = RoundedCornerShape(s.dp(10))
                    Row(
                        Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                            .nebulaClickable(shape, { onPick(c.value) }, role = Role.RadioButton)
                            .padding(horizontal = s.dp(8), vertical = s.dp(12)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (on) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                            tint = if (on) NebulaColors.accentText else NebulaColors.textMuted, modifier = Modifier.size(s.dp(20)))
                        Spacer(Modifier.width(s.dp(12)))
                        Text(c.label ?: c.value ?: "—", style = Nebula.type.body, color = NebulaColors.text)
                    }
                }
            }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        },
        confirmButton = { NebulaButton("Close", onClick = onDismiss, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}

@Composable
private fun TextRow(spec: SettingSpec, k: SettingKind.Text, prefs: LegacyPrefs, enabled: Boolean, mod: Modifier) {
    val s = Nebula.scale
    var value by remember { mutableStateOf(prefs.string(spec, k.default).orEmpty()) }
    Column(mod.card(s).padding(horizontal = s.dp(16), vertical = s.dp(12))) {
        Text(spec.title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
        spec.summary?.let { Text(it, style = Nebula.type.label, color = NebulaColors.textMuted) }
        Spacer(Modifier.height(s.dp(8)))
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(s.dp(10))
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = { value = it; if (enabled) prefs.put(spec.key, it) }, singleLine = true, enabled = enabled,
            textStyle = Nebula.type.body.copy(color = NebulaColors.text),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(NebulaColors.accentText),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                .background(NebulaColors.bg, shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) NebulaColors.accentText else NebulaColors.controlBorder, shape)
                .padding(s.dp(12)),
        )
    }
}

@Composable
private fun ActionRow(spec: SettingSpec, prefs: LegacyPrefs, mod: Modifier) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val later = laterActions[spec.key]
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportSettings(ctx, prefs, it) }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importSettings(ctx, prefs, it) }
    }
    var confirmReset by remember { mutableStateOf(false) }
    var editResolutions by remember { mutableStateOf(false) }
    val action: (() -> Unit)? = when (spec.key) {
        "custom_resolutions" -> ({ editResolutions = true })
        "config_sync_export" -> ({ export.launch("nebula-settings.json") })
        "config_sync_import", "config_sync_import_external_snapshot" -> ({ import.launch(arrayOf("application/json", "text/*", "*/*")) })
        "reset_osc" -> ({ confirmReset = true })
        else -> null
    }
    val shape = RoundedCornerShape(s.dp(12))
    Row(
        mod.then(if (action != null) Modifier.nebulaClickable(shape, action, role = Role.Button) else Modifier)
            .card(s).padding(horizontal = s.dp(16), vertical = s.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(spec.title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            (if (action == null) later ?: spec.summary else spec.summary)?.let {
                Text(it, style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 3)
            }
        }
        if (action != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NebulaColors.textMuted, modifier = Modifier.size(s.dp(20)))
        }
    }
    if (editResolutions) CustomResolutionsDialog(onDismiss = { editResolutions = false })
    if (confirmReset) {
        io.github.f_e_n_y_x.nebula.ui.components.NebulaConfirmDialog(
            title = "Reset on-screen controls?",
            text = "Button positions, sizes and profiles go back to the defaults.",
            confirm = "Reset",
            onConfirm = {
                confirmReset = false
                val p = prefs.prefs
                p.edit().apply { p.all.keys.filter { it.startsWith("osc") || it.contains("virtual_controller") }.forEach { remove(it) } }.apply()
                ctx.getSharedPreferences("OSC", Context.MODE_PRIVATE).edit().clear().apply()
                Toast.makeText(ctx, "On-screen controls reset", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

private fun exportSettings(ctx: Context, prefs: LegacyPrefs, uri: Uri) {
    val json = JSONObject()
    prefs.prefs.all.forEach { (k, v) ->
        when (v) {
            is Set<*> -> json.put(k, org.json.JSONArray(v.toList()))
            else -> json.put(k, v)
        }
    }
    val custom = ctx.getSharedPreferences("custom_resolutions", Context.MODE_PRIVATE).getStringSet("custom_resolutions", null)
    if (custom != null) json.put("__custom_resolutions", org.json.JSONArray(custom.toList()))
    runCatching {
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
    }.onSuccess { Toast.makeText(ctx, "Settings exported", Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(ctx, "Couldn't export: ${it.message}", Toast.LENGTH_LONG).show() }
}

private fun importSettings(ctx: Context, prefs: LegacyPrefs, uri: Uri) {
    runCatching {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("empty file")
        val json = JSONObject(text)
        val e = prefs.prefs.edit()
        json.keys().forEach { k ->
            when (val v = json.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putInt(k, v.toInt())
                is Double -> e.putFloat(k, v.toFloat())
                is org.json.JSONArray -> {
                    val set = (0 until v.length()).map { v.getString(it) }.toSet()
                    if (k == "__custom_resolutions") {
                        ctx.getSharedPreferences("custom_resolutions", Context.MODE_PRIVATE).edit().putStringSet("custom_resolutions", set).apply()
                    } else {
                        e.putStringSet(k, set)
                    }
                }
                else -> e.putString(k, v.toString())
            }
        }
        e.apply()
    }.onSuccess { Toast.makeText(ctx, "Settings imported", Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(ctx, "Couldn't import: ${it.message}", Toast.LENGTH_LONG).show() }
}

private const val CUSTOM_RES_FILE = "custom_resolutions"

/** V+'s custom resolution list: "WxH" strings in their own preferences file. */
fun customResolutions(ctx: Context): List<Pair<Int, Int>> =
    ctx.getSharedPreferences(CUSTOM_RES_FILE, Context.MODE_PRIVATE).getStringSet(CUSTOM_RES_FILE, emptySet()).orEmpty()
        .mapNotNull { r -> r.split("x").takeIf { it.size == 2 }?.let { (w, h) -> w.toIntOrNull()?.let { wi -> h.toIntOrNull()?.let { wi to it } } } }
        .sortedWith(compareBy({ it.first }, { it.second }))

private fun saveCustomResolutions(ctx: Context, list: List<Pair<Int, Int>>) {
    ctx.getSharedPreferences(CUSTOM_RES_FILE, Context.MODE_PRIVATE).edit()
        .putStringSet(CUSTOM_RES_FILE, list.map { "${it.first}x${it.second}" }.toSet()).apply()
}

@Composable
internal fun CustomResolutionsDialog(onDismiss: () -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(customResolutions(ctx)) }
    var w by remember { mutableStateOf("") }
    var h by remember { mutableStateOf("") }
    val valid = (w.toIntOrNull() ?: 0) in 256..7680 && (h.toIntOrNull() ?: 0) in 256..4320
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        titleContentColor = NebulaColors.text,
        title = { Text("Custom resolutions", style = Nebula.type.heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                Text("They appear next to the built-in sizes under Resolution.", style = Nebula.type.label, color = NebulaColors.textMuted)
                list.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.first} × ${r.second}", style = Nebula.type.body, color = NebulaColors.text, modifier = Modifier.weight(1f))
                        NebulaButton("Remove", onClick = { list = list - r; saveCustomResolutions(ctx, list) }, style = ButtonStyle.Ghost)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                    NumberBox(w, "Width", Modifier.weight(1f)) { w = it }
                    Text("×", color = NebulaColors.textMuted)
                    NumberBox(h, "Height", Modifier.weight(1f)) { h = it }
                }
                NebulaButton("Add", onClick = {
                    if (valid) { list = (list + (w.toInt() to h.toInt())).distinct(); saveCustomResolutions(ctx, list); w = ""; h = "" }
                }, style = if (valid) ButtonStyle.Primary else ButtonStyle.Secondary)
            }
        },
        confirmButton = { NebulaButton("Done", onClick = onDismiss, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}

@Composable
private fun NumberBox(value: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    val s = Nebula.scale
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(s.dp(10))
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = { v -> onChange(v.filter(Char::isDigit).take(5)) }, singleLine = true,
        textStyle = Nebula.type.body.copy(color = NebulaColors.text),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(NebulaColors.accentText),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = modifier.onFocusChanged { focused = it.isFocused }
            .background(NebulaColors.bg, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) NebulaColors.accentText else NebulaColors.controlBorder, shape)
            .padding(s.dp(12)),
        decorationBox = { inner -> if (value.isEmpty()) Text(hint, style = Nebula.type.body, color = NebulaColors.textMuted); inner() },
    )
}
