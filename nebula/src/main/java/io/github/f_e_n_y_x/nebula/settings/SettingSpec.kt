package io.github.f_e_n_y_x.nebula.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One V+ preference: same key, same stored type, so the streaming engine reads it unchanged. */
data class SettingSpec(
    val key: String,
    val group: String,
    val section: String?,
    val title: String,
    val summary: String?,
    val dependsOn: String?,
    val kind: SettingKind,
)

data class Choice(val value: String?, val label: String?)

sealed interface SettingKind {
    data class Toggle(val default: Boolean) : SettingKind
    data class Pick(val default: String?, val choices: List<Choice>) : SettingKind
    /** Stored as the raw int; shown divided by [divisor] with [unit]. */
    data class Slider(val default: Int, val min: Int, val max: Int, val step: Int, val divisor: Int, val unit: String) : SettingKind
    data class Text(val default: String) : SettingKind
    /** A V+ screen or command rather than a stored value. */
    data class Action(val kind: String?) : SettingKind
}

/** Nebula's settings groups, in display order. */
enum class SettingsGroup(val id: String, val title: String, val summary: String) {
    Stream("stream", "Stream", "Resolution, frame rate, bitrate, codec, frame pacing"),
    Display("display", "Display", "Scaling, HDR, image position, rotation, picture-in-picture"),
    Audio("audio", "Audio & microphone", "Surround, host audio, microphone"),
    Input("input", "Touch, mouse & keyboard", "Trackpad or direct touch, mouse modes, shortcuts"),
    Gamepads("gamepads", "Gamepads", "Controllers, rumble, gyro, remapping"),
    Osc("osc", "On-screen controls", "Virtual gamepad buttons and layout"),
    FrameGen("framegen", "Frame generation", "Lossless Scaling engine, upscaling"),
    Host("host", "Host & network", "Packet size, host audio, Wake-on-LAN, VPN"),
    Interface("interface", "Performance overlay & menu", "Stats overlay, stream menu"),
    Advanced("advanced", "Advanced", "Backup, restore and everything else"),
}

/** Reads and writes V+'s default SharedPreferences, tolerating values stored under another type. */
class LegacyPrefs(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.let { it.getSharedPreferences(it.packageName + "_preferences", Context.MODE_PRIVATE) }

    fun bool(spec: SettingSpec, d: Boolean): Boolean = when (val v = prefs.all[spec.key]) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull() ?: d
        else -> d
    }

    fun string(spec: SettingSpec, d: String?): String? = when (val v = prefs.all[spec.key]) {
        null -> d
        else -> v.toString()
    }

    fun int(spec: SettingSpec, d: Int): Int = when (val v = prefs.all[spec.key]) {
        is Int -> v
        is Long -> v.toInt()
        is String -> v.toIntOrNull() ?: d
        is Float -> v.toInt()
        else -> d
    }

    fun put(key: String, value: Any?) {
        prefs.edit().apply {
            when (value) {
                null -> remove(key)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                else -> putString(key, value.toString())
            }
        }.apply()
    }

    /** Emits the changed key (or null once at start) whenever any preference changes. */
    fun changes(): Flow<String?> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> trySend(key) }
        trySend(null)
        prefs.registerOnSharedPreferenceChangeListener(l)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    /** True unless [spec] depends on a toggle that is off. */
    fun enabled(spec: SettingSpec): Boolean {
        val dep = spec.dependsOn ?: return true
        val depSpec = legacySettings.firstOrNull { it.key == dep }
        val d = (depSpec?.kind as? SettingKind.Toggle)?.default ?: false
        return when (val v = prefs.all[dep]) {
            is Boolean -> v
            is String -> v.isNotEmpty() && v != "false"
            null -> d
            else -> true
        }
    }
}

/** Case-insensitive search across titles, help text, section names and keys. */
fun searchSettings(query: String): List<SettingSpec> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return legacySettings.filter { s ->
        listOfNotNull(s.title, s.summary, s.section, s.key).any { it.lowercase().contains(q) }
    }
}
