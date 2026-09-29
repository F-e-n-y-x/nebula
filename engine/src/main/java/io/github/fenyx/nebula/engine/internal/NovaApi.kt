package io.github.fenyx.nebula.engine.internal

import io.github.fenyx.nebula.engine.AppDetails
import io.github.fenyx.nebula.engine.HostCommand
import io.github.fenyx.nebula.engine.HostCommandList
import io.github.fenyx.nebula.engine.ArtKind
import io.github.fenyx.nebula.engine.NovaCapabilities
import io.github.fenyx.nebula.engine.NovaDisplayMode
import io.github.fenyx.nebula.engine.RunningApp
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
    const val COMMANDS = "nova/v1/commands"
    const val RUNNING = "nova/v1/running"

    fun details(novaId: String) = "nova/v1/apps/$novaId/details"
    fun art(novaId: String, kind: ArtKind) = "nova/v1/apps/$novaId/art/${kind.wire}"

    /** Screenshot paths come from the host already relative to the Nova root. */
    fun screenshot(path: String) = "nova/v1/" + path.trimStart('/').removePrefix("nova/v1/")

    /** Null unless the body is a Nova capabilities document. */
    fun parseCapabilities(body: String): NovaCapabilities? {
        val json = JSONObject(body)
        if (!json.optBoolean("nova")) return null
        return NovaCapabilities(
            version = json.optString("version"),
            features = json.optJSONArray("features").strings().toSet(),
            permissions = json.optJSONArray("permissions")?.strings()?.toSet(),
        )
    }

    /**
     * GET /nova/v1/commands: `{"allowed":bool,"commands":[{id,name,icon,confirm,scope,app,runnable,running,last_run}]}`.
     * A bare array is accepted too. Entries without an id or name are skipped; ids are de-duplicated.
     */
    fun parseCommands(body: String): HostCommandList {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return HostCommandList.Empty
        if (trimmed.startsWith("[")) return HostCommandList(null, commandsFrom(JSONArray(trimmed), appNovaId = null))
        val o = JSONObject(trimmed)
        val allowed = if (o.has("allowed") && !o.isNull("allowed")) o.optBoolean("allowed") else null
        val list = o.optJSONArray("commands")?.let { commandsFrom(it, appNovaId = null) }.orEmpty()
        return HostCommandList(allowed, if (allowed == false) emptyList() else list)
    }

    /**
     * An app's Foundation `SuperCmds` value from /applist: a JSON array of `{id, name}` (ids may be
     * numbers). "null", blank or malformed text means none.
     */
    fun parseSuperCmds(raw: String?, appNovaId: String? = null): List<HostCommand> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null" || !text.startsWith("[")) return emptyList()
        return try {
            commandsFrom(JSONArray(text), appNovaId)
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }

    private fun commandsFrom(arr: JSONArray, appNovaId: String?): List<HostCommand> {
        val seen = HashSet<String>()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.opt("id")?.takeUnless { it == JSONObject.NULL }?.toString()?.trim().orEmpty()
            val name = o.optString("name").trim()
            if (id.isEmpty() || name.isEmpty() || !seen.add(id)) return@mapNotNull null
            val scopeGlobal = o.optString("scope") == "global"
            HostCommand(
                id = id,
                name = name,
                // Hosts that don't say get a confirmation, to be safe.
                confirm = if (o.has("confirm") && !o.isNull("confirm")) o.optBoolean("confirm", true) else true,
                appNovaId = if (scopeGlobal) null else o.optString("app").takeIf { it.isNotBlank() && it != "null" } ?: appNovaId,
                icon = o.optString("icon").takeIf { it.isNotBlank() && it != "null" },
                runnable = o.optBoolean("runnable", true),
                running = o.optBoolean("running", false),
            )
        }
    }

    /**
     * GET /nova/v1/running: `{running, app:{id,name,index}, since, display, connected_clients}`.
     * Returns null when nothing runs. `app.id` may be the GameStream id (a number) or Nova's own id;
     * numeric ids become [RunningApp.appId], others [RunningApp.novaId].
     */
    fun parseRunning(body: String): RunningApp? {
        val o = JSONObject(body.trim().ifEmpty { "{}" })
        val app = o.optJSONObject("app")
        if (!o.optBoolean("running", app != null) || app == null) return null
        val rawId = app.opt("id")?.takeUnless { it == JSONObject.NULL }?.toString()?.trim().orEmpty()
        val numeric = rawId.toIntOrNull()?.takeIf { it > 0 }
        return RunningApp(
            appId = numeric?.toString(),
            novaId = rawId.takeIf { it.isNotEmpty() && numeric == null },
            name = app.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" },
            sinceEpochS = o.optLong("since", 0L).takeIf { it > 0 },
            display = NovaDisplayMode.fromWire(o.optString("display")),
            connectedClients = if (o.has("connected_clients") && !o.isNull("connected_clients")) o.optInt("connected_clients", 0) else null,
            fromNova = true,
        )
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
