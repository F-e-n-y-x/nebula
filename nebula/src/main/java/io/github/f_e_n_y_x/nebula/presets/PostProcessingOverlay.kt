package io.github.f_e_n_y_x.nebula.presets

import android.content.Context
import android.content.SharedPreferences
import io.github.f_e_n_y_x.nebula.domain.GamePreset
import io.github.fenyx.nebula.engine.framegen.FramegenDll
import io.github.fenyx.nebula.engine.framegen.FramegenKeys
import org.json.JSONObject

/** A key-value view of the settings the engine reads at stream start (V+'s default preferences). */
interface SettingValues {
    fun get(key: String): Any?
    fun contains(key: String): Boolean
    /** Stores [value] (Boolean, Int or String); null removes the key. */
    fun put(key: String, value: Any?)
}

/** Where the overlay notes what it replaced, so a crash mid-stream can be undone at the next start. */
interface OverlayJournal {
    fun read(): String?
    fun write(value: String?)
}

/**
 * Frame generation and the upscaler are read by the engine from the global settings when a stream
 * starts, and changed live from the stream menu. A game preset therefore puts its values into
 * those settings for the length of the stream ([apply]) and puts the global values back afterwards
 * ([finish]). A value the user changed during the stream is returned by [finish], so it can be kept
 * for that game rather than lost.
 */
class PostProcessingOverlay(private val values: SettingValues, private val journal: OverlayJournal) {

    /** What [finish] undid: the game it was for, and the preset keys changed during the stream. */
    data class Finished(val hostId: String, val gameId: String, val changedDuringStream: Map<String, Any?>)

    /**
     * Puts [overrides] into the settings for a stream of [gameId] on [hostId]. An overlay left
     * over from an earlier stream is undone first. Returns the token [finish] takes, or null when
     * there was nothing to apply.
     */
    fun apply(hostId: String, gameId: String, overrides: Map<String, Any>, token: String = java.util.UUID.randomUUID().toString()): String? {
        finish()
        if (overrides.isEmpty()) return null
        val keys = JSONObject()
        overrides.forEach { (k, v) ->
            keys.put(k, JSONObject().put("had", values.contains(k)).put("old", values.get(k) ?: JSONObject.NULL).put("applied", v))
        }
        // Journal first: if the process dies between the two, the next start still restores.
        journal.write(JSONObject().put("token", token).put("host", hostId).put("game", gameId).put("keys", keys).toString())
        overrides.forEach { (k, v) -> values.put(k, v) }
        return token
    }

    /**
     * Puts the global values back; null when no overlay was active. With a [token], only the
     * overlay [apply] returned it for is undone (a stream that already gave way to a newer one
     * leaves the newer overlay alone).
     */
    fun finish(token: String? = null): Finished? {
        val raw = journal.read() ?: return null
        val o = runCatching { JSONObject(raw) }.getOrNull()
        if (token != null && o != null && o.optString("token") != token) return null
        journal.write(null)
        if (o == null) return null
        val keys = o.optJSONObject("keys") ?: JSONObject()
        val changed = mutableMapOf<String, Any?>()
        keys.keys().forEach { k ->
            val entry = keys.optJSONObject(k) ?: return@forEach
            val applied = entry.opt("applied")
            val live = values.get(k)
            if (live != applied) changed[k] = live
            val old = entry.opt("old").takeUnless { it == JSONObject.NULL }
            values.put(k, if (entry.optBoolean("had")) old else null)
        }
        return Finished(o.optString("host"), o.optString("game"), changed)
    }

    companion object {
        /** The settings a preset sets for the stream. */
        fun overridesFor(preset: GamePreset): Map<String, Any> = buildMap {
            preset.frameGen?.let { put(FramegenKeys.ENABLED, it) }
            preset.upscaler?.let { put(FramegenKeys.UPSCALER, it) }
        }

        /** [preset] with the values the user chose during the stream ([Finished.changedDuringStream]). */
        fun keepChanges(preset: GamePreset, changed: Map<String, Any?>): GamePreset {
            var p = preset
            if (FramegenKeys.ENABLED in changed && p.frameGen != null) p = p.copy(frameGen = changed[FramegenKeys.ENABLED] as? Boolean ?: false)
            if (FramegenKeys.UPSCALER in changed && p.upscaler != null) {
                (changed[FramegenKeys.UPSCALER] as? String)?.let { p = p.copy(upscaler = it) }
            }
            return p
        }

        fun create(context: Context): PostProcessingOverlay {
            val prefs = FramegenDll.prefs(context)
            val journal = context.applicationContext.getSharedPreferences("nebula_preset_overlay", Context.MODE_PRIVATE)
            return PostProcessingOverlay(SharedPreferenceValues(prefs), object : OverlayJournal {
                override fun read(): String? = journal.getString("active", null)
                override fun write(value: String?) {
                    journal.edit().apply { if (value == null) remove("active") else putString("active", value) }.commit()
                }
            })
        }
    }
}

/** [SettingValues] over SharedPreferences (writes are committed at once: a stream starts right after). */
class SharedPreferenceValues(private val prefs: SharedPreferences) : SettingValues {
    override fun get(key: String): Any? = prefs.all[key]
    override fun contains(key: String): Boolean = prefs.contains(key)
    override fun put(key: String, value: Any?) {
        prefs.edit().apply {
            when (value) {
                null -> remove(key)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                else -> putString(key, value.toString())
            }
        }.commit()
    }
}
