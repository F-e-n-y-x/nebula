package io.github.fenyx.nebula.engine.internal

import io.github.fenyx.nebula.engine.AppDetails
import io.github.fenyx.nebula.engine.ArtKind
import io.github.fenyx.nebula.engine.NovaCapabilities
import io.github.fenyx.nebula.engine.NovaDisplayMode
import org.json.JSONArray
import org.json.JSONObject

/** One entry of GET /nova/v1/apps. */
data class NovaApp(
    val id: String,
    /** GameStream app id this entry corresponds to, when the host reports it. */
    val gameStreamId: Int?,
    val name: String,
    val running: Boolean,
    val art: Set<ArtKind>,
    val lastPlayed: Long?,
    val playtimeSeconds: Long?,
    val modeDefault: NovaDisplayMode?,
)

/**
 * Paths and JSON parsers for the Nova /nova/v1 extension API. Paths are unencoded path segments;
 * NvHTTP.novaGet encodes them.
 */
object NovaApi {
    const val CAPABILITIES = "nova/v1/capabilities"
    const val APPS = "nova/v1/apps"

    fun details(novaId: String) = "nova/v1/apps/$novaId/details"
    fun art(novaId: String, kind: ArtKind) = "nova/v1/apps/$novaId/art/${kind.wire}"

    /** Screenshot paths come from the host already relative to the Nova root. */
    fun screenshot(path: String) = "nova/v1/" + path.trimStart('/').removePrefix("nova/v1/")

    /** Null unless the body is a Nova capabilities document. */
    fun parseCapabilities(body: String): NovaCapabilities? {
        val json = JSONObject(body)
        if (!json.optBoolean("nova")) return null
        return NovaCapabilities(json.optString("version"), json.optJSONArray("features").strings().toSet())
    }

    fun parseApps(body: String): List<NovaApp> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { parseApp(arr.getJSONObject(it)) }
    }

    fun parseApp(o: JSONObject): NovaApp {
        val has = o.optJSONObject("has") ?: JSONObject()
        return NovaApp(
            id = o.optString("id"),
            gameStreamId = o.optInt("appid", 0).takeIf { it != 0 },
            name = o.optString("name"),
            running = o.optBoolean("running"),
            art = ArtKind.entries.filter { has.optBoolean(it.wire) }.toSet(),
            lastPlayed = o.optLong("last_played", 0L).takeIf { it > 0 },
            playtimeSeconds = o.optLong("playtime_s", 0L).takeIf { it > 0 },
            modeDefault = NovaDisplayMode.fromWire(o.optString("mode_default")),
        )
    }

    fun parseDetails(body: String): AppDetails {
        val o = JSONObject(body)
        val session = o.optJSONObject("last_session")
        return AppDetails(
            description = o.optString("description").ifEmpty { null },
            genres = o.optJSONArray("genres").strings(),
            developer = o.optString("developer").ifEmpty { null },
            publisher = o.optString("publisher").ifEmpty { null },
            releaseDate = o.optString("release_date").ifEmpty { null },
            screenshots = o.optJSONArray("screenshots").strings(),
            metacritic = o.optInt("metacritic", 0).takeIf { it > 0 },
            lastPlayed = o.optLong("last_played", 0L).takeIf { it > 0 },
            playtimeSeconds = o.optLong("playtime_s", 0L).takeIf { it > 0 },
            lastSession = session?.let {
                AppDetails.LastSession(
                    device = it.optString("device").ifEmpty { null },
                    resolution = it.optString("resolution").ifEmpty { null },
                    fps = it.optInt("fps", 0).takeIf { f -> f > 0 },
                    codec = it.optString("codec").ifEmpty { null },
                )
            },
        )
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
