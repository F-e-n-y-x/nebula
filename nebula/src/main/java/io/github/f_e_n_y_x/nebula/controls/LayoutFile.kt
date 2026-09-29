package io.github.f_e_n_y_x.nebula.controls

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/** The game a shared layout is for. [steamAppId] is optional (GTA V is 271590). */
data class GameRef(val name: String, val steamAppId: Int? = null)

/** What a layout sends: gamepad (XInput), keyboard and mouse, or both. */
enum class LayoutTarget(val id: String, val label: String) {
    XINPUT("xinput", "Controller (XInput)"), KBM("kbm", "Keyboard + mouse"), MIXED("mixed", "Controller + keyboard");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }

        /** From the bindings: pad buttons and triggers are XInput, keys and mouse buttons KB+M. */
        fun detect(elements: List<ControlElement>, look: LookOutput? = null): LayoutTarget {
            val all = elements.flatMap { e -> e.bindings + e.click + e.sprint + e.steps.map { it.binding } }
            val pad = all.any { it is Binding.Pad || it is Binding.Trigger } ||
                elements.any { (it.kind == ElementKind.STICK || it.kind == ElementKind.ZONE) && it.stick != StickOutput.KEYS && it.zone != ZoneType.CAMERA_MOUSE }
            val kbm = all.any { it is Binding.Key || it is Binding.Mouse || it is Binding.Wheel } ||
                elements.any { it.kind == ElementKind.TOUCHPAD || ((it.kind == ElementKind.STICK || it.kind == ElementKind.ZONE) && it.stick == StickOutput.KEYS) || (it.kind == ElementKind.ZONE && it.zone == ZoneType.CAMERA_MOUSE) }
            return when {
                pad && kbm -> MIXED
                kbm -> KBM
                else -> XINPUT
            }
        }
    }
}

enum class DeviceClass(val id: String, val label: String) {
    PHONE("phone", "Phone"), TABLET("tablet", "Tablet"), ANY("any", "Any");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
        /** Android's usual split: 600 dp smallest width and up is a tablet. */
        fun forSmallestWidth(dp: Int) = if (dp >= 600) TABLET else PHONE
    }
}

/**
 * The metadata of a shared layout. [aspect] is the landscape controls area's width / height the
 * author laid it out on (a phone is about 2.1, a tablet about 1.5); positions are shares of the
 * area anyway, so it's only a hint for the preview and the fit-to-screen step.
 */
data class LayoutMeta(
    val name: String,
    val author: String = "",
    val game: GameRef? = null,
    val target: LayoutTarget = LayoutTarget.XINPUT,
    val device: DeviceClass = DeviceClass.PHONE,
    val aspect: Float? = null,
    val description: String = "",
    val tags: List<String> = emptyList(),
)

/**
 * The shareable layout file (`"format": "nebula-layout", "version": 1`): one profile's layouts
 * plus [LayoutMeta] and the profile's touch settings, as data only. The JSON Schema lives in the
 * layout library (`schema/nebula-layout-1.schema.json`); [parse] enforces the same rules and more
 * (unique ids, macro length), strictly: an unknown key, kind, binding or out-of-range number
 * rejects the whole file instead of being dropped, because a layout from the internet must never
 * be half-understood. Nothing in a layout can run code: bindings are button, key and mouse names.
 *
 * Written compactly: fields at their default are left out (readers fill them in), so small
 * layouts fit a QR code as a [shareCode].
 */
object LayoutFile {
    const val FORMAT = "nebula-layout"
    const val VERSION = 1
    /** Largest layout file accepted, in bytes (a full 64-control layout is ~20 KB). */
    const val MAX_BYTES = 256 * 1024
    const val MAX_ELEMENTS = 64
    const val MAX_NAME = 60
    const val MAX_AUTHOR = 40
    const val MAX_DESCRIPTION = 500
    const val MAX_GAME = 80
    const val MAX_LABEL = 24
    const val MAX_TAGS = 8
    /** Prefix of the compressed share code (QR codes, chat messages). */
    const val CODE_PREFIX = "NEBL1:"
    /** Share codes up to this length go in one QR code (version 40-L holds 2953 bytes). */
    const val MAX_QR_CHARS = 2200

    private val TOP_KEYS = setOf("\$schema", "format", "version", "meta", "settings", "landscape", "portrait")
    private val META_KEYS = setOf("name", "author", "game", "target", "device", "aspect", "description", "tags")
    private val GAME_KEYS = setOf("name", "steamAppId")
    private val SETTINGS_KEYS = setOf("outside", "look")
    private val ELEMENT_KEYS = setOf(
        "id", "kind", "x", "y", "w", "h", "label", "opacity", "mode", "shape", "bindings", "stick", "click", "floating",
        "deadzone", "sensitivity", "steps", "tint", "zone", "acceleration", "invertY", "showRing", "keepWithController",
        "lookThrough", "antiDeadzone", "sprint", "sprintAt", "runLock", "role", "group",
    )
    private val STEP_KEYS = setOf("binding", "holdMs", "gapMs")
    private val ID = Regex("^[A-Za-z0-9_.:-]{1,40}$")
    private val TAG = Regex("^[a-z0-9][a-z0-9-]{0,23}$")

    data class Parsed(val profile: ControlsProfile, val meta: LayoutMeta)

    // ---------------------------------------------------------------- writing

    /** The file for [p]; [meta] defaults to what the profile carries, else its name. */
    fun encode(p: ControlsProfile, meta: LayoutMeta = p.meta ?: LayoutMeta(p.name, target = LayoutTarget.detect(p.landscape, p.look)), pretty: Boolean = true): String {
        val o = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("meta", metaToJson(meta))
        val settings = JSONObject()
        p.outside?.let { settings.put("outside", it.id) }
        p.look?.let { settings.put("look", it.id) }
        if (settings.length() > 0) o.put("settings", settings)
        o.put("landscape", JSONArray().apply { p.landscape.forEach { put(compact(it)) } })
        p.portrait?.let { list -> o.put("portrait", JSONArray().apply { list.forEach { put(compact(it)) } }) }
        return if (pretty) o.toString(2) else o.toString()
    }

    fun metaToJson(m: LayoutMeta): JSONObject = JSONObject().apply {
        put("name", m.name)
        if (m.author.isNotBlank()) put("author", m.author)
        m.game?.let { g -> put("game", JSONObject().put("name", g.name).apply { g.steamAppId?.let { put("steamAppId", it) } }) }
        put("target", m.target.id)
        put("device", m.device.id)
        m.aspect?.let { put("aspect", Math.round(it * 100) / 100.0) }
        if (m.description.isNotBlank()) put("description", m.description)
        if (m.tags.isNotEmpty()) put("tags", JSONArray(m.tags))
    }

    /** Only the fields that differ from a fresh element of the same kind (plus id, kind and place). */
    private fun compact(e: ControlElement): JSONObject {
        val full = ProfileJson.elementToJson(e)
        val base = ProfileJson.elementToJson(ControlElement(e.id, e.kind, e.x, e.y, e.width, e.height))
        val keep = setOf("id", "kind", "x", "y", "w", "h")
        val out = JSONObject()
        for (k in full.keys()) {
            val v = full.get(k)
            if (k in keep || !same(v, base.opt(k))) out.put(k, round(v))
        }
        return out
    }

    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONArray && b is JSONArray -> a.toString() == b.toString()
        a is Number && b is Number -> Math.abs(a.toDouble() - b.toDouble()) < 1e-6
        else -> a == b
    }

    /** Four decimals are plenty for shares of the screen and keep QR codes small. */
    private fun round(v: Any): Any = if (v is Double || v is Float) Math.round((v as Number).toDouble() * 10000) / 10000.0 else v

    /** The compressed one-line form: [CODE_PREFIX] + base64url(deflate(minified JSON)). */
    fun shareCode(p: ControlsProfile, meta: LayoutMeta = p.meta ?: LayoutMeta(p.name, target = LayoutTarget.detect(p.landscape, p.look))): String {
        val bytes = encode(p, meta, pretty = false).toByteArray(Charsets.UTF_8)
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(bytes); d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    fun fitsQr(code: String) = code.length <= MAX_QR_CHARS

    /** The JSON inside a share code, inflated with the same size cap as a file (no zip bombs). */
    fun decodeShareCode(code: String): String {
        val body = code.trim().removePrefix(CODE_PREFIX)
        if (body.length > MAX_BYTES) throw ControlsFormatException("That share code is too long")
        val raw = try { Base64.getUrlDecoder().decode(body) } catch (e: IllegalArgumentException) { throw ControlsFormatException("That share code is damaged") }
        val inf = Inflater()
        inf.setInput(raw)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) throw ControlsFormatException("That share code is damaged")
                out.write(buf, 0, n)
                if (out.size() > MAX_BYTES) throw ControlsFormatException("That layout is larger than ${MAX_BYTES / 1024} KB")
            }
        } catch (e: java.util.zip.DataFormatException) {
            throw ControlsFormatException("That share code is damaged")
        } finally {
            inf.end()
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun looksLikeShareCode(text: String) = text.trim().startsWith(CODE_PREFIX)

    fun looksLikeLayout(text: String): Boolean {
        val t = text.trim()
        return looksLikeShareCode(t) || (t.startsWith("{") && t.contains("\"$FORMAT\""))
    }

    // ---------------------------------------------------------------- reading

    /** A layout file or share code, validated strictly. The profile gets [newId] and origin "library". */
    fun parse(text: String, newId: String = "p-import", now: Long = 0L): Parsed {
        val json = if (looksLikeShareCode(text)) decodeShareCode(text) else text
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw ControlsFormatException("That layout is larger than ${MAX_BYTES / 1024} KB")
        val root = try {
            JSONTokener(json.trim()).nextValue() as? JSONObject ?: throw ControlsFormatException("That isn't a Nebula layout (not a JSON object)")
        } catch (e: JSONException) {
            throw ControlsFormatException("That isn't a Nebula layout (not valid JSON)")
        }
        if (root.opt("format") != FORMAT) throw ControlsFormatException("That isn't a Nebula layout (\"format\" must be \"$FORMAT\")")
        val version = root.opt("version")
        if (version !is Int) throw ControlsFormatException("The layout has no version")
        if (version > VERSION) throw ControlsFormatException("This layout was made by a newer Nebula; update Nebula to import it")
        if (version < 1) throw ControlsFormatException("Unknown layout version $version")
        unknownKeys(root, TOP_KEYS, "the file")
        val meta = parseMeta(root.opt("meta") as? JSONObject ?: throw ControlsFormatException("meta: missing"))
        val settings = root.opt("settings")?.let { it as? JSONObject ?: throw ControlsFormatException("settings: must be an object") }
        var outside: OutsideTouch? = null
        var look: LookOutput? = null
        if (settings != null) {
            unknownKeys(settings, SETTINGS_KEYS, "settings")
            settings.opt("outside")?.let { v -> outside = OutsideTouch.of(v as? String) ?: throw ControlsFormatException("settings.outside: unknown value $v") }
            settings.opt("look")?.let { v -> look = LookOutput.of(v as? String) ?: throw ControlsFormatException("settings.look: unknown value $v") }
        }
        val landscape = parseElements(root.opt("landscape"), "landscape", required = true)
        val portrait = root.opt("portrait")?.let { parseElements(it, "portrait", required = false) }
        val profile = ControlsProfile(
            id = newId, name = meta.name, landscape = landscape, portrait = portrait,
            createdAtMs = now, updatedAtMs = now, origin = "library", outside = outside, look = look, meta = meta,
        )
        return Parsed(profile, meta)
    }

    fun parseMeta(m: JSONObject): LayoutMeta {
        unknownKeys(m, META_KEYS, "meta")
        val name = text(m, "name", "meta.name", MAX_NAME, required = true)
        val gameObj = m.opt("game")?.let { it as? JSONObject ?: throw ControlsFormatException("meta.game: must be an object") }
        val game = gameObj?.let { g ->
            unknownKeys(g, GAME_KEYS, "meta.game")
            val appId = g.opt("steamAppId")?.let { v ->
                (v as? Int)?.takeIf { it in 1..99_999_999 } ?: throw ControlsFormatException("meta.game.steamAppId: must be a whole number from 1 to 99999999")
            }
            GameRef(text(g, "name", "meta.game.name", MAX_GAME, required = true), appId)
        }
        val target = m.opt("target")?.let { v -> LayoutTarget.of(v as? String) ?: throw ControlsFormatException("meta.target: unknown value $v") } ?: LayoutTarget.XINPUT
        val device = m.opt("device")?.let { v -> DeviceClass.of(v as? String) ?: throw ControlsFormatException("meta.device: unknown value $v") } ?: DeviceClass.ANY
        val aspect = m.opt("aspect")?.let { number(it, "meta.aspect", 0.3, 4.0).toFloat() }
        val tags = m.opt("tags")?.let { v ->
            val a = v as? JSONArray ?: throw ControlsFormatException("meta.tags: must be a list")
            if (a.length() > MAX_TAGS) throw ControlsFormatException("meta.tags: at most $MAX_TAGS")
            (0 until a.length()).map { i -> (a.opt(i) as? String)?.takeIf { TAG.matches(it) } ?: throw ControlsFormatException("meta.tags[$i]: lower-case letters, digits and dashes, up to 24") }
        }.orEmpty()
        return LayoutMeta(
            name = name, author = text(m, "author", "meta.author", MAX_AUTHOR), game = game, target = target, device = device,
            aspect = aspect, description = text(m, "description", "meta.description", MAX_DESCRIPTION, multiline = true), tags = tags,
        )
    }

    private fun parseElements(v: Any?, path: String, required: Boolean): List<ControlElement> {
        if (v == null) { if (required) throw ControlsFormatException("$path: missing") else return emptyList() }
        val a = v as? JSONArray ?: throw ControlsFormatException("$path: must be a list")
        if (required && a.length() == 0) throw ControlsFormatException("$path: the layout has no controls")
        if (a.length() > MAX_ELEMENTS) throw ControlsFormatException("$path: at most $MAX_ELEMENTS controls")
        val ids = HashSet<String>()
        return (0 until a.length()).map { i ->
            val p = "$path[$i]"
            val o = a.opt(i) as? JSONObject ?: throw ControlsFormatException("$p: must be an object")
            element(o, p).also { if (!ids.add(it.id)) throw ControlsFormatException("$p.id: \"${it.id}\" is used twice") }
        }
    }

    /** One element, every field checked before [ProfileJson.elementFromJson] fills in defaults. */
    private fun element(o: JSONObject, p: String): ControlElement {
        unknownKeys(o, ELEMENT_KEYS, p)
        val id = (o.opt("id") as? String)?.takeIf { ID.matches(it) } ?: throw ControlsFormatException("$p.id: letters, digits and _ . : - only, up to 40")
        val kindId = o.opt("kind") as? String ?: throw ControlsFormatException("$p.kind: missing")
        val kind = ElementKind.entries.firstOrNull { it.id == kindId } ?: throw ControlsFormatException("$p.kind: unknown control \"$kindId\"")
        number(o.opt("x") ?: throw ControlsFormatException("$p.x: missing"), "$p.x", 0.0, 1.0)
        number(o.opt("y") ?: throw ControlsFormatException("$p.y: missing"), "$p.y", 0.0, 1.0)
        val sizeRange = if (kind == ElementKind.ZONE) ControlElement.MIN_ZONE.toDouble()..1.0 else ControlElement.MIN_SIZE_DP.toDouble()..ControlElement.MAX_SIZE_DP.toDouble()
        number(o.opt("w") ?: throw ControlsFormatException("$p.w: missing"), "$p.w", sizeRange.start, sizeRange.endInclusive)
        number(o.opt("h") ?: throw ControlsFormatException("$p.h: missing"), "$p.h", sizeRange.start, sizeRange.endInclusive)
        o.opt("label")?.let { if (it !is String) throw ControlsFormatException("$p.label: must be text") }
        text(o, "label", "$p.label", MAX_LABEL)
        o.opt("opacity")?.let { number(it, "$p.opacity", ControlElement.MIN_OPACITY.toDouble(), 1.0) }
        enumId(o, "mode", p, PressMode.entries.map { it.id })
        enumId(o, "shape", p, ElementShape.entries.map { it.id })
        enumId(o, "stick", p, StickOutput.entries.map { it.id })
        enumId(o, "zone", p, ZoneType.entries.map { it.id })
        enumId(o, "role", p, ElementRole.entries.map { it.id })
        o.opt("group")?.let { v ->
            if (v !is String || !ProfileJson.GROUP_ID.matches(v)) throw ControlsFormatException("$p.group: letters, digits and _ . : - only, up to 40")
        }
        o.opt("bindings")?.let { v ->
            val a = v as? JSONArray ?: throw ControlsFormatException("$p.bindings: must be a list")
            val max = if (kind == ElementKind.COMBO) 5 else 4
            if (a.length() > max) throw ControlsFormatException("$p.bindings: at most $max")
            for (i in 0 until a.length()) binding(a.opt(i), "$p.bindings[$i]")
        }
        o.opt("click")?.let { binding(it, "$p.click") }
        o.opt("sprint")?.let { binding(it, "$p.sprint") }
        for (b in listOf("floating", "invertY", "showRing", "keepWithController", "lookThrough", "runLock")) {
            o.opt(b)?.let { if (it !is Boolean) throw ControlsFormatException("$p.$b: must be true or false") }
        }
        o.opt("deadzone")?.let { number(it, "$p.deadzone", 0.0, 0.9) }
        o.opt("sensitivity")?.let { number(it, "$p.sensitivity", 0.1, 5.0) }
        o.opt("acceleration")?.let { number(it, "$p.acceleration", 0.5, 2.5) }
        o.opt("antiDeadzone")?.let { number(it, "$p.antiDeadzone", 0.0, 0.45) }
        o.opt("sprintAt")?.let { number(it, "$p.sprintAt", 1.0, 2.0) }
        o.opt("tint")?.let { v ->
            if (v !is String || !Regex("^#[0-9A-Fa-f]{8}$").matches(v)) throw ControlsFormatException("$p.tint: must look like #AARRGGBB")
        }
        o.opt("steps")?.let { v ->
            val a = v as? JSONArray ?: throw ControlsFormatException("$p.steps: must be a list")
            if (a.length() > 16) throw ControlsFormatException("$p.steps: at most 16")
            var total = 0.0
            for (i in 0 until a.length()) {
                val s = a.opt(i) as? JSONObject ?: throw ControlsFormatException("$p.steps[$i]: must be an object")
                unknownKeys(s, STEP_KEYS, "$p.steps[$i]")
                binding(s.opt("binding") ?: throw ControlsFormatException("$p.steps[$i].binding: missing"), "$p.steps[$i].binding")
                total += s.opt("holdMs")?.let { number(it, "$p.steps[$i].holdMs", 10.0, 5000.0) } ?: 60.0
                total += s.opt("gapMs")?.let { number(it, "$p.steps[$i].gapMs", 0.0, 5000.0) } ?: 40.0
            }
            if (total > 20_000) throw ControlsFormatException("$p.steps: a macro may last at most 20 seconds")
        }
        return ProfileJson.elementFromJson(o) ?: throw ControlsFormatException("$p: couldn't be read")
    }

    private fun unknownKeys(o: JSONObject, allowed: Set<String>, where: String) {
        val bad = o.keys().asSequence().filter { it !in allowed }.toList()
        if (bad.isNotEmpty()) throw ControlsFormatException("$where: unknown field${if (bad.size > 1) "s" else ""} ${bad.take(3).joinToString { "\"${it.take(30)}\"" }}")
    }

    private fun enumId(o: JSONObject, key: String, p: String, ids: List<String>) {
        val v = o.opt(key) ?: return
        if (v !is String || v !in ids) throw ControlsFormatException("$p.$key: unknown value ${v.toString().take(30)}")
    }

    private fun number(v: Any, path: String, min: Double, max: Double): Double {
        val d = (v as? Number)?.toDouble() ?: throw ControlsFormatException("$path: must be a number")
        if (d.isNaN() || d.isInfinite() || d < min || d > max) throw ControlsFormatException("$path: must be between ${fmt(min)} and ${fmt(max)}")
        return d
    }

    private fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

    /** A binding token must be exactly one Nebula knows (`pad:4096`, `key:65`, `mouse:left`, `rt`, `wheel:up`, `none`). */
    private fun binding(v: Any, path: String) {
        val t = (v as? String)?.trim()?.lowercase() ?: throw ControlsFormatException("$path: must be text")
        if (t == "none") return
        val b = Binding.parse(t)
        val ok = when (b) {
            Binding.None -> false
            is Binding.Pad -> b.flag in PadFlags.names.keys
            else -> true
        }
        if (!ok) throw ControlsFormatException("$path: unknown binding \"${t.take(30)}\"")
    }

    private fun text(o: JSONObject, key: String, path: String, max: Int, required: Boolean = false, multiline: Boolean = false): String {
        val v = o.opt(key)
        if (v == null) { if (required) throw ControlsFormatException("$path: missing") else return "" }
        val s = v as? String ?: throw ControlsFormatException("$path: must be text")
        if (required && s.isBlank()) throw ControlsFormatException("$path: missing")
        if (s.length > max) throw ControlsFormatException("$path: at most $max characters")
        if (s.any { it.isISOControl() && !(multiline && it == '\n') } || s.any { it == '‮' || it == '‭' }) throw ControlsFormatException("$path: has control characters")
        return s.trim()
    }

    /** File name for a layout: `<name>.nebula-layout.json`. */
    fun fileName(meta: LayoutMeta): String {
        val stem = slug(meta.name).ifBlank { "layout" }
        return "$stem.nebula-layout.json"
    }

    /** Lower-case, dash-separated, safe in paths and URLs. */
    fun slug(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48)
}
