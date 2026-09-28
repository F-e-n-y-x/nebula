package io.github.f_e_n_y_x.nebula.controls

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Converts V+ Crown profiles to Nebula profiles.
 *
 * Crown exports come in two wrappers, both handled here:
 *  - a share bundle (`.crown.json`, kind `crown-profile-bundle`), whose `profile.payload` is the
 *    raw export plus its SHA-256, a name and the `layoutBasis` screen it was made on;
 *  - a legacy `.mdat` payload: `{version, settings, elements, md5}` where settings and elements
 *    are JSON strings of V+'s SQLite rows and md5 = MD5(version + settings + elements).
 *
 * Element rows carry pixel positions and sizes for the screen they were made on; they become
 * shares of the screen and dp. Key values use V+'s codes: `k<Android keycode>`, `g<pad flag>`,
 * `m<mouse button>`, `LS`/`RS`, `lt`/`rt`, `SU`/`SD`. Elements Nebula can't run (group buttons,
 * wheel pads, stat readouts, V+-only actions) are left out and listed in [Result.skipped].
 */
object CrownImport {
    const val BUNDLE_KIND = "crown-profile-bundle"

    /** The screen a layout was made on: pixels and density (px per dp). */
    data class Basis(val widthPx: Int, val heightPx: Int, val density: Float) {
        val landscape get() = widthPx >= heightPx
    }

    data class Skipped(val what: String, val reason: String)

    data class Result(
        val profile: ControlsProfile,
        val skipped: List<Skipped>,
        /** The bundle's basis, or the fallback used when the file doesn't say. */
        val basis: Basis,
        val basisFromFile: Boolean,
    ) {
        val importedCount get() = profile.landscape.size + (profile.portrait?.size ?: 0)
    }

    /** True when [root] looks like either Crown wrapper. */
    fun looksLikeCrown(root: JSONObject): Boolean =
        root.optString("kind") == BUNDLE_KIND || (root.has("version") && root.has("elements") && root.has("settings"))

    /**
     * Parses [text]; [fallback] is this device's screen, used for legacy payloads that don't say
     * which screen they came from (V+ exports on the same phone). [newId] and [now] make the new
     * profile's id and timestamps.
     */
    fun import(text: String, fallback: Basis, newId: String, now: Long = 0): Result {
        val root = try {
            JSONObject(text.trim())
        } catch (e: JSONException) {
            throw ControlsFormatException("The file isn't valid JSON")
        }
        var name: String? = null
        var basis: Basis? = null
        val payloadText: String
        if (root.optString("kind") == BUNDLE_KIND) {
            if (root.optInt("schemaVersion", -1) != 1) throw ControlsFormatException("Unsupported Crown bundle version")
            val profile = root.optJSONObject("profile") ?: throw ControlsFormatException("The Crown bundle has no profile")
            payloadText = profile.optString("payload")
            val sha = profile.optString("payloadSha256")
            if (payloadText.isBlank() || sha.isBlank()) throw ControlsFormatException("The Crown bundle is incomplete")
            if (!hex("SHA-256", payloadText).equals(sha, ignoreCase = true)) throw ControlsFormatException("The Crown bundle's checksum doesn't match; the file was changed or damaged")
            name = profile.optString("name").ifBlank { root.optString("name") }.ifBlank { null }
            basis = root.optJSONObject("layoutBasis")?.let { b ->
                val w = b.optInt("widthPx"); val h = b.optInt("heightPx")
                val d = b.optDouble("density", 0.0).toFloat().takeIf { it > 0f } ?: (b.optInt("densityDpi", 0) / 160f).takeIf { it > 0f }
                if (w > 0 && h > 0 && d != null) Basis(w, h, d) else null
            }
        } else {
            payloadText = text
        }
        val payload = try {
            JSONObject(payloadText.trim())
        } catch (e: JSONException) {
            throw ControlsFormatException("The Crown payload isn't valid JSON")
        }
        if (!payload.has("version") || !payload.has("settings") || !payload.has("elements")) {
            throw ControlsFormatException("This isn't a Crown profile (no version, settings or elements)")
        }
        val version = payload.optInt("version", -1)
        val settingsText = rawString(payload, "settings")
        val elementsText = rawString(payload, "elements")
        val md5 = payload.optString("md5")
        if (md5.isNotBlank() && !hex("MD5", "$version$settingsText$elementsText").equals(md5, ignoreCase = true)) {
            throw ControlsFormatException("The Crown profile's checksum doesn't match; the file was changed or damaged")
        }
        val settings = runCatching { JSONObject(settingsText) }.getOrNull()
        val rows = try {
            JSONArray(elementsText)
        } catch (e: JSONException) {
            throw ControlsFormatException("The Crown profile's elements are damaged")
        }

        val b = basis ?: fallback
        val skipped = mutableListOf<Skipped>()
        val elements = mutableListOf<Pair<Int, ControlElement>>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val converted = convert(row, b, skipped, i)
            if (converted != null) elements += (row.long("element_layer")?.toInt() ?: 50) to converted
        }
        // Crown draws higher layers on top; Nebula draws later elements on top.
        val ordered = elements.sortedBy { it.first }.map { it.second }
        val profileName = name ?: settings?.optString("config_name")?.takeIf { it.isNotBlank() && it != "default" } ?: "Crown profile"
        val profile = ControlsProfile(
            id = newId,
            name = profileName,
            // A portrait-made layout is also the landscape starting point until edited there.
            landscape = ordered,
            portrait = if (b.landscape) null else ordered,
            createdAtMs = now,
            updatedAtMs = now,
            origin = "crown",
        )
        return Result(profile, skipped, b, basis != null)
    }

    private fun rawString(o: JSONObject, key: String): String = when (val v = o.opt(key)) {
        is String -> v
        null -> ""
        else -> v.toString()
    }

    private fun JSONObject.long(key: String): Long? = when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    private fun JSONObject.str(key: String): String? = optString(key, "").takeIf { it.isNotBlank() && it != "null" }

    private fun typeName(t: Int) = when (t) {
        0 -> "Button"; 1 -> "Toggle button"; 2 -> "Movable button"; 3 -> "Combo button"; 4 -> "Group button"
        20 -> "D-pad"; 30 -> "Analog stick"; 31 -> "Digital stick"; 32 -> "Invisible analog stick"; 33 -> "Invisible digital stick"
        50 -> "Performance readout"; 54 -> "Wheel pad"
        else -> "Element type $t"
    }

    private fun convert(row: JSONObject, b: Basis, skipped: MutableList<Skipped>, index: Int): ControlElement? {
        val type = row.long("element_type")?.toInt() ?: run { skipped += Skipped("Element ${index + 1}", "It has no type"); return null }
        val text = row.str("element_text").orEmpty()
        val what = typeName(type) + (if (text.isNotBlank()) " “$text”" else "")
        val id = "crown-" + (row.long("element_id") ?: index.toLong())
        val wPx = (row.long("element_width") ?: 100).toFloat()
        val hPx = (row.long("element_height") ?: 100).toFloat()
        val x = (row.long("element_central_x") ?: (b.widthPx / 2).toLong()).toFloat() / b.widthPx
        val y = (row.long("element_central_y") ?: (b.heightPx / 2).toLong()).toFloat() / b.heightPx
        val opacity = row.long("element_opacity")?.takeIf { it in 1..100 }?.let { it / 100f }
            ?: row.long("element_color")?.let { ((it ushr 24) and 0xFF).toFloat() / 255f }?.takeIf { it > 0f }
            ?: 1f
        val radiusPx = (row.long("element_radius") ?: 0).toFloat()
        val shape = when {
            radiusPx >= minOf(wPx, hPx) / 2 * 0.9f -> ElementShape.ROUND
            radiusPx > 0 -> ElementShape.PILL
            else -> ElementShape.SQUARE
        }
        val notes = mutableListOf<String>()
        fun bind(key: String): Binding = mapValue(row.str(key), notes)
        val base = ControlElement(
            id = id, kind = ElementKind.BUTTON, x = x, y = y, width = wPx / b.density, height = hPx / b.density,
            label = text, opacity = opacity, shape = shape,
        )
        val sense = (row.long("element_sense") ?: 30).toFloat()
        val deadzone = (sense / (minOf(wPx, hPx) / 2f).coerceAtLeast(1f)).coerceIn(0f, 0.5f)
        val out = when (type) {
            0, 1 -> base.copy(bindings = listOf(bind("element_value")), mode = if (type == 1) PressMode.TOGGLE else PressMode.HOLD)
            2 -> {
                val trackpad = row.str("extra_attributes")?.let { runCatching { JSONObject(it).optBoolean("isTrackpadMode", false) }.getOrNull() } == true
                if (trackpad) {
                    base.copy(kind = ElementKind.TOUCHPAD, click = bind("element_value"), sensitivity = ((row.long("element_sense") ?: 100) / 100f).coerceIn(0.1f, 5f))
                } else {
                    base.copy(bindings = listOf(bind("element_value")))
                }
            }
            3 -> {
                val keys = listOf("element_value", "element_up_value", "element_down_value", "element_left_value", "element_right_value")
                    .map { bind(it) }.filter { it != Binding.None }
                if (keys.isEmpty()) { skipped += Skipped(what, "None of its keys work in Nebula"); return null }
                base.copy(kind = ElementKind.COMBO, bindings = keys)
            }
            20 -> base.copy(
                kind = ElementKind.DPAD, shape = ElementShape.ROUND,
                bindings = listOf(bind("element_up_value"), bind("element_down_value"), bind("element_left_value"), bind("element_right_value")),
            )
            30, 32 -> {
                val v = row.str("element_value")
                val stick = when (v) {
                    "LS" -> StickOutput.LEFT
                    "RS" -> StickOutput.RIGHT
                    else -> { skipped += Skipped(what, "Its stick output “$v” isn't a gamepad stick"); return null }
                }
                base.copy(
                    kind = ElementKind.STICK, shape = ElementShape.ROUND, stick = stick, click = bind("element_middle_value"),
                    floating = type == 32 || row.long("element_mode") == 1L, deadzone = minOf(deadzone, 0.3f),
                    label = text.ifBlank { if (stick == StickOutput.LEFT) "LS" else "RS" },
                    opacity = if (type == 32) ControlElement.MIN_OPACITY else opacity,
                )
            }
            31, 33 -> base.copy(
                kind = ElementKind.STICK, shape = ElementShape.ROUND, stick = StickOutput.KEYS,
                bindings = listOf(bind("element_up_value"), bind("element_down_value"), bind("element_left_value"), bind("element_right_value")),
                click = bind("element_middle_value"), floating = type == 33, deadzone = deadzone,
                opacity = if (type == 33) ControlElement.MIN_OPACITY else opacity,
            )
            4 -> { skipped += Skipped(what, "Group buttons (show/hide other buttons) aren't in Nebula yet"); return null }
            50 -> { skipped += Skipped(what, "Nebula has its own performance overlay"); return null }
            54 -> { skipped += Skipped(what, "Wheel pads aren't in Nebula yet"); return null }
            else -> { skipped += Skipped(what, "Nebula doesn't know this element type"); return null }
        }
        if (out.kind == ElementKind.BUTTON && out.binding == Binding.None) {
            skipped += Skipped(what, notes.firstOrNull() ?: "It isn't bound to anything Nebula can send")
            return null
        }
        return out.clampedSize()
    }

    /** V+ value code → binding; unsupported codes become [Binding.None] with a note. */
    internal fun mapValue(v: String?, notes: MutableList<String> = mutableListOf()): Binding {
        if (v == null || v == "null" || v == "-1") return Binding.None
        Regex("k(\\d+)").matchEntire(v)?.let { m ->
            val vk = VirtualKeys.fromAndroidKeyCode(m.groupValues[1].toInt())
            if (vk == null) notes += "Android key ${m.groupValues[1]} has no PC key"
            return vk?.let { Binding.Key(it) } ?: Binding.None
        }
        Regex("g(\\d+)").matchEntire(v)?.let { return Binding.Pad(it.groupValues[1].toInt()) }
        Regex("m(\\d+)").matchEntire(v)?.let { m ->
            return when (m.groupValues[1].toInt()) {
                1 -> Binding.Mouse(MouseKey.LEFT); 2 -> Binding.Mouse(MouseKey.MIDDLE); 3 -> Binding.Mouse(MouseKey.RIGHT)
                4 -> Binding.Mouse(MouseKey.BACK); 5 -> Binding.Mouse(MouseKey.FORWARD)
                else -> Binding.None
            }
        }
        return when (v) {
            "lt" -> Binding.Trigger(Side.LEFT)
            "rt" -> Binding.Trigger(Side.RIGHT)
            "SU" -> Binding.Wheel(true)
            "SD" -> Binding.Wheel(false)
            else -> { notes += "V+ action “$v” isn't in Nebula"; Binding.None }
        }
    }

    private fun hex(algorithm: String, text: String): String =
        MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
