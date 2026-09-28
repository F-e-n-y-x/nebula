package io.github.f_e_n_y_x.nebula.update

import org.json.JSONArray
import org.json.JSONObject

/** One file attached to a GitHub release. */
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    /** `sha256:<hex>` as GitHub reports it for assets uploaded since mid-2025, or null. */
    val digest: String?,
    /** `https://api.github.com/repos/<repo>/releases/assets/<id>`; fetch with `Accept: application/octet-stream`. */
    val apiUrl: String,
)

/** A published Nebula release (`nebula-vX.Y.Z[-pre]`). */
data class Release(
    val tag: String,
    val version: Semver,
    val name: String,
    val notes: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
)

/** Pure parsing and selection of GitHub release data; no network, no Android APIs. */
object Releases {
    const val TAG_PREFIX = "nebula-v"
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    /**
     * Parse the `GET /repos/{owner}/{repo}/releases` array. Drafts and tags that are not
     * `nebula-vX.Y.Z[-pre]` are dropped; malformed entries are skipped instead of failing the list.
     */
    fun parse(json: String): List<Release> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::parseOne) }
    }

    private fun parseOne(o: JSONObject): Release? {
        if (o.optBoolean("draft", false)) return null
        val tag = str(o, "tag_name")
        if (!tag.startsWith(TAG_PREFIX)) return null
        val version = Semver.parse(tag) ?: return null
        val assets = o.optJSONArray("assets")?.let { a ->
            (0 until a.length()).mapNotNull { j ->
                val x = a.optJSONObject(j) ?: return@mapNotNull null
                val id = x.optLong("id", -1)
                val name = str(x, "name")
                val url = str(x, "url")
                if (id < 0 || name.isEmpty() || !url.startsWith("https://api.github.com/")) return@mapNotNull null
                ReleaseAsset(id, name, x.optLong("size", -1), str(x, "digest").ifEmpty { null }, url)
            }
        }.orEmpty()
        return Release(
            tag = tag, version = version,
            name = str(o, "name").ifEmpty { tag },
            notes = str(o, "body"),
            htmlUrl = str(o, "html_url"),
            // A pre-release is either flagged on GitHub or carries a semver pre-release suffix.
            prerelease = o.optBoolean("prerelease", false) || version.isPrerelease,
            assets = assets,
        )
    }

    /** A string field; JSON null and non-strings read as "" (org.json would give "null"). */
    private fun str(o: JSONObject, key: String): String = (o.opt(key) as? String).orEmpty()

    /** The newest release strictly newer than [currentVersion], or null. Unparseable current → null. */
    fun newest(releases: List<Release>, currentVersion: String, includePrereleases: Boolean): Release? {
        val current = Semver.parse(currentVersion) ?: return null
        return releases
            .filter { includePrereleases || !it.prerelease }
            .filter { it.version > current }
            .maxByOrNull { it.version }
    }

    /** The APK to install: `.apk`, never a debug build; prefer a name containing "release". */
    fun pickApk(assets: List<ReleaseAsset>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) && !it.name.contains("debug", ignoreCase = true) }
        return apks.firstOrNull { it.name.contains("release", ignoreCase = true) } ?: apks.firstOrNull()
    }

    /** The checksum file for [apk]: `<apk>.sha256` first, then `SHA256SUMS`. */
    fun pickChecksumAsset(assets: List<ReleaseAsset>, apk: ReleaseAsset): ReleaseAsset? =
        assets.firstOrNull { it.name.equals("${apk.name}.sha256", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.equals("SHA256SUMS", ignoreCase = true) || it.name.equals("SHA256SUMS.txt", ignoreCase = true) }

    /** Hex digest from GitHub's `sha256:<hex>` asset field, or null. */
    fun digestHex(asset: ReleaseAsset): String? {
        val d = asset.digest ?: return null
        if (!d.startsWith("sha256:", ignoreCase = true)) return null
        return d.substring(7).lowercase().takeIf { SHA256_HEX.matches(it) }
    }

    /**
     * Hash for [fileName] from a checksum file: `sha256sum` lines (`<hex>  name` or `<hex> *name`),
     * or a file that holds only the hash.
     */
    fun parseChecksumFile(text: String, fileName: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        for (line in lines) {
            val parts = line.split(Regex("\\s+"), limit = 2)
            val hex = parts[0].lowercase()
            if (!SHA256_HEX.matches(hex)) continue
            if (parts.size == 1) {
                if (lines.size == 1) return hex
                continue
            }
            if (parts[1].removePrefix("*").trim() == fileName) return hex
        }
        return null
    }

    /** Outcome of combining the two possible hash sources. */
    sealed interface Expected {
        data class Hash(val hex: String) : Expected
        data object Missing : Expected
        data object Conflict : Expected
    }

    /** Both sources must agree; one is enough; none means the release can't be verified. */
    fun expectedSha256(fromDigest: String?, fromFile: String?): Expected = when {
        fromDigest != null && fromFile != null -> if (fromDigest.equals(fromFile, ignoreCase = true)) Expected.Hash(fromDigest.lowercase()) else Expected.Conflict
        fromDigest != null -> Expected.Hash(fromDigest.lowercase())
        fromFile != null -> Expected.Hash(fromFile.lowercase())
        else -> Expected.Missing
    }

    /** First lines of the release notes for the About card. */
    fun notesExcerpt(notes: String, maxChars: Int = 400): String {
        val t = notes.replace("\r\n", "\n").trim()
        return if (t.length <= maxChars) t else t.take(maxChars).trimEnd() + "…"
    }
}

/**
 * Signing-certificate comparison for the downloaded APK against the installed app, on SHA-256
 * digests of each certificate (lowercase hex). Sets must be equal and non-empty; a rotated key
 * (signing lineage) is accepted by the platform itself only through the installer, so we require
 * the exact current signer set here and let a key rotation be installed by hand.
 */
object SignerMatch {
    fun same(installed: Set<String>, candidate: Set<String>): Boolean =
        installed.isNotEmpty() && candidate.isNotEmpty() && installed.map { it.lowercase() }.toSet() == candidate.map { it.lowercase() }.toSet()
}
