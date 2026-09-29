package io.github.f_e_n_y_x.nebula.controls

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/**
 * The online layout library: an `index.json` (format `nebula-layout-index`) listing layout files
 * that live next to it, as in the public `nebula-layouts` repository. Each entry carries enough
 * to show and search it (name, game, target, a small [LibraryEntry.preview] of the controls)
 * without downloading the layout; the download is checked against [LibraryEntry.sha256] and
 * then validated like any imported file ([LayoutFile.parse]).
 */
data class LibraryEntry(
    val id: String,
    val meta: LayoutMeta,
    /** The layout file, relative to the index: `layouts/<game>/…` or `layouts/genre-<genre>/…`. */
    val path: String,
    val size: Int,
    val sha256: String?,
    val controls: Int,
    /** Landscape controls for the thumbnail: kind, centre and size (zones as shares, else dp). */
    val preview: List<PreviewBox>,
    /** A layout set's layout names (format 2); empty for a single layout. */
    val layouts: List<String> = emptyList(),
) {
    val gameName: String get() = meta.game?.name ?: "Any game"

    /** A genre template (no game), as opposed to a layout made for one game. */
    val isTemplate: Boolean get() = meta.game == null

    /** The genre a `layouts/genre-<genre>/` folder names, if the file is in one. */
    val folderGenre: String? get() = path.split('/').getOrNull(1)?.takeIf { it.startsWith(LayoutGenres.FOLDER_PREFIX) }?.removePrefix(LayoutGenres.FOLDER_PREFIX)?.ifBlank { null }

    /** Its genres: the folder's, then the genre tags, in the tags' order. */
    val genres: List<String> get() = (listOfNotNull(folderGenre) + meta.tags.filter { it in LayoutGenres.ALL }).distinct()
}

data class PreviewBox(val kind: ElementKind, val x: Float, val y: Float, val w: Float, val h: Float, val round: Boolean = true)

/** A heading in Browse layouts: one genre's templates, or one game's layouts. */
data class LibrarySection(val title: String, val subtitle: String, val template: Boolean, val entries: List<LibraryEntry>)

data class LibraryIndex(val entries: List<LibraryEntry>, val skipped: Int, val updated: String?) {
    /** The genres that have layouts, in [LayoutGenres.ALL] order, then any others by name. */
    fun genres(): List<String> = entries.flatMap { it.genres }.distinct().sortedWith(LayoutGenres.ORDER)

    /**
     * Entries matching [query] (name, game, author, description, tags or genre) and [genre]
     * (null for all): genre templates first, one section per genre, then one section per game.
     */
    fun browse(query: String = "", genre: String? = null): List<LibrarySection> {
        val q = query.trim().lowercase()
        val hits = entries.filter { e ->
            (genre == null || genre in e.genres) && (
                q.isEmpty() || listOf(e.meta.name, e.meta.author, e.gameName, e.meta.description, e.meta.target.label, e.meta.tags.joinToString(" "), e.genres.joinToString(" ") { LayoutGenres.label(it) })
                    .any { q in it.lowercase() }
                )
        }
        val (templates, games) = hits.partition { it.isTemplate }
        val templateSections = templates.groupBy { (genre?.takeIf { g -> g in it.genres } ?: it.genres.firstOrNull()) ?: "" }.toList()
            .sortedWith(compareBy(LayoutGenres.ORDER) { it.first })
            .map { (g, list) ->
                if (g.isEmpty()) LibrarySection("Templates", "For any game", true, list)
                else LibrarySection("${LayoutGenres.label(g)} templates", "For any ${LayoutGenres.noun(g)}", true, list)
            }
        val gameSections = games.groupBy { it.gameName }.toList().sortedBy { it.first.lowercase() }.map { (game, list) ->
            LibrarySection(game, list.flatMap { it.genres }.distinct().sortedWith(LayoutGenres.ORDER).joinToString(" · ") { LayoutGenres.label(it) }, false, list)
        }
        return templateSections + gameSections
    }
}

/**
 * The layout library's genre conventions (the nebula-layouts README): genre tags, and genre
 * templates kept in `layouts/genre-<genre>/`. A genre outside [ALL] is allowed; it sorts last.
 */
object LayoutGenres {
    const val FOLDER_PREFIX = "genre-"
    val ALL = listOf("shooter", "racing", "action-adventure", "platformer", "fighting", "sports", "rpg", "strategy", "desktop")
    val ORDER: Comparator<String> = compareBy<String>({ ALL.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it })

    fun label(genre: String): String = when (genre) {
        "rpg" -> "RPG"
        "" -> "Other"
        else -> genre.replaceFirstChar { it.uppercase() }
    }

    /** "For any <noun>": a game of that genre. */
    fun noun(genre: String): String = when (genre) {
        "shooter", "platformer" -> genre
        "racing", "fighting", "sports", "strategy", "action-adventure" -> "${genre.replace('-', ' ')} game"
        "rpg" -> "RPG"
        "desktop" -> "desktop app"
        else -> "${genre.replace('-', ' ')} game"
    }
}

/** Where a shared layout goes in the library, following its folder and file naming. */
object LibraryPaths {
    /** Short folder names for games whose full name makes a long slug. */
    private val GAME_FOLDERS = mapOf(271590 to "gta-v")

    /**
     * `layouts/<game>/<name>.json` for a game layout (the "<Game> · " name prefix dropped:
     * "GTA V · touch controls" → `layouts/gta-v/touch-controls.json`), or
     * `layouts/genre-<genre>/<name>.json` for a template, by its first genre tag.
     */
    fun pathFor(meta: LayoutMeta): String {
        val game = meta.game
        val (prefix, rest) = meta.name.split(" · ", limit = 2).let { if (it.size == 2) it[0] to it[1] else null to meta.name }
        val folder = if (game != null) {
            game.steamAppId?.let(GAME_FOLDERS::get) ?: prefix?.let(LayoutFile::slug)?.ifBlank { null } ?: LayoutFile.slug(game.name)
        } else {
            LayoutGenres.FOLDER_PREFIX + (meta.tags.firstOrNull { it in LayoutGenres.ALL } ?: "other")
        }
        val file = LayoutFile.slug(if (game != null && prefix != null) rest else meta.name)
        return "layouts/${folder.ifBlank { "other" }.take(48).trim('-')}/${file.ifBlank { "layout" }}.json"
    }
}

object LayoutIndex {
    const val FORMAT = "nebula-layout-index"
    const val VERSION = 1
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_ENTRIES = 5000
    private val PATH = Regex("^layouts/[a-z0-9][a-z0-9-]{0,47}/[a-z0-9][a-z0-9.-]{0,63}\\.json$")
    private val ID = Regex("^[a-z0-9][a-z0-9/_.-]{0,95}$")
    private val SHA = Regex("^[0-9a-f]{64}$")

    /** Parses an index; broken entries are skipped (counted in [LibraryIndex.skipped]), a broken index throws. */
    fun parse(text: String): LibraryIndex {
        if (text.length > MAX_BYTES) throw ControlsFormatException("The library index is too large")
        val root = try {
            JSONTokener(text.trim()).nextValue() as? JSONObject ?: throw ControlsFormatException("The library index isn't a JSON object")
        } catch (e: JSONException) {
            throw ControlsFormatException("The library index isn't valid JSON")
        }
        if (root.opt("format") != FORMAT) throw ControlsFormatException("That isn't a Nebula layout library index")
        val v = root.opt("version") as? Int ?: throw ControlsFormatException("The library index has no version")
        if (v > VERSION) throw ControlsFormatException("The library needs a newer Nebula")
        val list = root.optJSONArray("layouts") ?: throw ControlsFormatException("The library index lists no layouts")
        var skipped = 0
        val seen = HashSet<String>()
        val out = ArrayList<LibraryEntry>()
        for (i in 0 until minOf(list.length(), MAX_ENTRIES)) {
            val e = (list.opt(i) as? JSONObject)?.let { runCatching { entry(it) }.getOrNull() }
            if (e == null || !seen.add(e.id)) { skipped++; continue }
            out += e
        }
        skipped += maxOf(0, list.length() - MAX_ENTRIES)
        return LibraryIndex(out, skipped, root.optString("updated").ifBlank { null })
    }

    private fun entry(o: JSONObject): LibraryEntry {
        val id = (o.opt("id") as? String)?.takeIf { ID.matches(it) } ?: throw ControlsFormatException("bad id")
        val path = (o.opt("path") as? String)?.takeIf { PATH.matches(it) && ".." !in it } ?: throw ControlsFormatException("bad path")
        val size = (o.opt("size") as? Int)?.takeIf { it in 1..LayoutFile.MAX_BYTES } ?: throw ControlsFormatException("bad size")
        val sha = (o.opt("sha256") as? String)?.lowercase()?.also { if (!SHA.matches(it)) throw ControlsFormatException("bad sha256") }
        val metaJson = JSONObject()
        for (k in listOf("name", "author", "game", "target", "device", "aspect", "description", "tags")) o.opt(k)?.let { metaJson.put(k, it) }
        val meta = LayoutFile.parseMeta(metaJson)
        val preview = o.optJSONArray("preview")?.let(::previewOf).orEmpty()
        val layouts = o.optJSONArray("layouts")?.let { a -> (0 until minOf(a.length(), LayoutFile.MAX_LAYOUTS)).mapNotNull { (a.opt(it) as? String)?.take(LayoutFile.MAX_LAYOUT_NAME)?.takeIf { n -> n.isNotBlank() && n.none(Char::isISOControl) } } }.orEmpty()
        return LibraryEntry(id, meta, path, size, sha, (o.opt("controls") as? Int)?.coerceIn(0, 999) ?: preview.size, preview, layouts)
    }

    private fun previewOf(a: JSONArray): List<PreviewBox> = (0 until minOf(a.length(), LayoutFile.MAX_ELEMENTS)).mapNotNull { i ->
        val r = a.optJSONArray(i) ?: return@mapNotNull null
        val kind = ElementKind.entries.firstOrNull { it.id == r.optString(0) } ?: return@mapNotNull null
        fun f(j: Int) = r.optDouble(j, Double.NaN).toFloat().takeIf { it.isFinite() }
        val x = f(1)?.coerceIn(0f, 1f) ?: return@mapNotNull null
        val y = f(2)?.coerceIn(0f, 1f) ?: return@mapNotNull null
        val w = f(3)?.coerceIn(0f, 400f) ?: return@mapNotNull null
        val h = f(4)?.coerceIn(0f, 400f) ?: return@mapNotNull null
        PreviewBox(kind, x, y, w, h, r.optString(5, "round") != "square")
    }

    /** The preview rows the index carries for [elements] (the validator script writes the same). */
    fun previewOf(elements: List<ControlElement>): List<PreviewBox> = elements.map {
        PreviewBox(it.kind, it.x, it.y, it.width, it.height, it.shape != ElementShape.SQUARE)
    }

    fun resolve(indexUrl: String, path: String): String = URI(indexUrl).resolve(path).toString()

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/**
 * Where layouts may be downloaded from: https only. Debug builds also allow plain http to a
 * private LAN address (the owner's sample library at http://192.168.10.10:8765), never in release.
 */
class UrlPolicy(private val allowLanHttp: Boolean) {
    /** The URL if allowed, else an exception with a message for the user. */
    fun check(url: String): URL {
        val uri = try { URI(url.trim()) } catch (e: Exception) { throw ControlsFormatException("That isn't a web address") }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: throw ControlsFormatException("That address has no host")
        if (uri.rawUserInfo != null) throw ControlsFormatException("Addresses with a user name aren't allowed")
        when (scheme) {
            "https" -> Unit
            "http" -> if (!allowLanHttp || !isPrivateHost(host)) throw ControlsFormatException("Layouts can only be downloaded over https")
            else -> throw ControlsFormatException("Layouts can only be downloaded over https")
        }
        return uri.toURL()
    }

    companion object {
        /** A literal private / loopback IPv4 address or localhost (no DNS lookups). */
        fun isPrivateHost(host: String): Boolean {
            if (host == "localhost") return true
            if (!Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) return false
            val b = host.split('.').map { it.toInt() }
            if (b.any { it > 255 }) return false
            return b[0] == 10 || b[0] == 127 || (b[0] == 192 && b[1] == 168) || (b[0] == 172 && b[1] in 16..31)
        }
    }
}

/**
 * Downloads a library index or a layout with a size cap, manual redirects (each hop re-checked
 * by [policy]) and, for indexes, an ETag cache in [cacheDir]: a 304 or a failed request falls
 * back to the cached copy. Blocking; call it off the main thread.
 */
class LayoutFetcher(
    private val policy: UrlPolicy,
    private val cacheDir: File?,
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    data class Result(val body: String, val fromCache: Boolean, val stale: Boolean = false)

    fun fetch(url: String, maxBytes: Int, cache: Boolean = false): Result {
        val key = if (cache && cacheDir != null) LayoutIndex.sha256(url.toByteArray()).take(20) else null
        val bodyFile = key?.let { File(cacheDir, "$it.json") }
        val etagFile = key?.let { File(cacheDir, "$it.etag") }
        val cached = bodyFile?.takeIf { it.isFile && it.length() <= maxBytes }?.readText()
        val etag = etagFile?.takeIf { it.isFile && cached != null }?.readText()?.trim()?.takeIf { it.isNotEmpty() && it.length < 200 }
        try {
            var target = policy.check(url)
            repeat(MAX_REDIRECTS + 1) {
                val c = connect(target)
                try {
                    c.instanceFollowRedirects = false
                    c.connectTimeout = TIMEOUT_MS
                    c.readTimeout = TIMEOUT_MS
                    c.setRequestProperty("Accept", "application/json")
                    c.setRequestProperty("User-Agent", "Nebula-layouts/1")
                    if (etag != null) c.setRequestProperty("If-None-Match", etag)
                    val code = c.responseCode
                    when {
                        code == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null -> return Result(cached, fromCache = true)
                        code in 300..399 -> {
                            val loc = c.getHeaderField("Location") ?: throw IOException("Redirect without a location")
                            target = policy.check(URI(target.toString()).resolve(loc).toString())
                            return@repeat
                        }
                        code != HttpURLConnection.HTTP_OK -> throw IOException("The server answered $code")
                    }
                    if (c.contentLengthLong > maxBytes) throw ControlsFormatException("That download is larger than ${maxBytes / 1024} KB")
                    val bytes = c.inputStream.use { readCapped(it, maxBytes) }
                    val body = bytes.toString(Charsets.UTF_8)
                    if (bodyFile != null && etagFile != null) {
                        runCatching {
                            cacheDir?.mkdirs()
                            bodyFile.writeText(body)
                            val tag = c.getHeaderField("ETag")
                            if (tag != null) etagFile.writeText(tag) else etagFile.delete()
                        }
                    }
                    return Result(body, fromCache = false)
                } finally {
                    c.disconnect()
                }
            }
            throw IOException("Too many redirects")
        } catch (e: ControlsFormatException) {
            throw e
        } catch (e: IOException) {
            if (cached != null) return Result(cached, fromCache = true, stale = true)
            throw e
        }
    }

    private fun readCapped(input: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw ControlsFormatException("That download is larger than ${max / 1024} KB")
        }
        return out.toByteArray()
    }

    /** A layout from the library: size-capped, hash-checked against the index, then validated. */
    fun layout(indexUrl: String, entry: LibraryEntry, newId: String, now: Long): LayoutFile.Parsed {
        val body = fetch(LayoutIndex.resolve(indexUrl, entry.path), LayoutFile.MAX_BYTES).body
        entry.sha256?.let { want ->
            if (LayoutIndex.sha256(body.toByteArray(Charsets.UTF_8)) != want) throw ControlsFormatException("The download doesn't match the library's checksum")
        }
        return LayoutFile.parse(body, newId, now)
    }

    companion object {
        const val MAX_REDIRECTS = 3
        const val TIMEOUT_MS = 10_000
    }
}

/** Where the library is. Debug builds may point it at a LAN copy ([LAN_SAMPLE]). */
object LibrarySource {
    const val DEFAULT_URL = "https://raw.githubusercontent.com/F-e-n-y-x/nebula-layouts/main/index.json"
    const val LAN_SAMPLE = "http://192.168.10.10:8765/nebula-layouts/index.json"
    const val REPO = "https://github.com/F-e-n-y-x/nebula-layouts"
    const val PREF_KEY = "nebula_layout_library_url"
}

/** `nebula://layout?url=https://…` links: the layout's address, or null for anything else. */
object LayoutLink {
    fun urlOf(uri: String?): String? {
        if (uri == null || uri.length > 2048) return null
        val u = try { URI(uri) } catch (e: Exception) { return null }
        if (u.scheme?.lowercase() != "nebula" || u.host?.lowercase() != "layout") return null
        val q = u.rawQuery ?: return null
        val v = q.split('&').firstOrNull { it.startsWith("url=") }?.removePrefix("url=") ?: return null
        return runCatching { java.net.URLDecoder.decode(v, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun of(url: String): String = "nebula://layout?url=" + java.net.URLEncoder.encode(url, "UTF-8")
}
