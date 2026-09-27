package io.github.f_e_n_y_x.nebula.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Snapshot of the PC keyboard's settings. */
data class KeyboardConfig(
    /** 1..10, as V+ stores it (alpha = value / 10). */
    val opacity: Int,
    /** Keyboard height in px, or -1 for automatic. */
    val heightPx: Int,
    val offsetX: Float,
    val offsetY: Float,
    val mini: Boolean,
    val page: String,
)

/**
 * The on-screen PC keyboard's settings, in V+'s own "keyboard_settings" file and keys so they carry
 * over (keyboard_opacity 0–10, keyboard_height, keyboard_x/y, keyboard_is_mini).
 */
class KeyboardSettings(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read() = KeyboardConfig(
        opacity = prefs.getInt(KEY_OPACITY, 10).coerceIn(1, 10),
        heightPx = prefs.getInt(KEY_HEIGHT, -1),
        offsetX = prefs.getFloat(KEY_X, 0f),
        offsetY = prefs.getFloat(KEY_Y, 0f),
        mini = prefs.getBoolean(KEY_IS_MINI, false),
        page = prefs.getString(KEY_PAGE, "FULL") ?: "FULL",
    )

    fun setOpacity(v: Int) = prefs.edit().putInt(KEY_OPACITY, v.coerceIn(1, 10)).apply()
    fun setHeight(px: Int) = prefs.edit().putInt(KEY_HEIGHT, px).apply()
    fun setOffset(x: Float, y: Float) = prefs.edit().putFloat(KEY_X, x).putFloat(KEY_Y, y).apply()
    fun setMini(mini: Boolean) = prefs.edit().putBoolean(KEY_IS_MINI, mini).apply()
    fun setPage(page: String) = prefs.edit().putString(KEY_PAGE, page).putBoolean(KEY_IS_MINI, page == "MINI").apply()
    fun resetPlacement() = prefs.edit().remove(KEY_X).remove(KEY_Y).remove(KEY_HEIGHT).apply()

    fun changes(): Flow<String?> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> trySend(key) }
        trySend(null)
        prefs.registerOnSharedPreferenceChangeListener(l)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    companion object {
        const val FILE = "keyboard_settings"
        const val KEY_OPACITY = "keyboard_opacity"
        const val KEY_HEIGHT = "keyboard_height"
        const val KEY_X = "keyboard_x"
        const val KEY_Y = "keyboard_y"
        const val KEY_IS_MINI = "keyboard_is_mini"
        const val KEY_PAGE = "nebula_keyboard_page"
    }
}

/** Opacity (percent) shared by the stream's overlays; the stream menu's transparency control sets it. */
const val OVERLAY_OPACITY_KEY = "nebula_overlay_opacity"
const val DEFAULT_OVERLAY_OPACITY = 90

/**
 * Sets every overlay's opacity at once: the mouse bar and float ball ([OVERLAY_OPACITY_KEY]),
 * V+'s on-screen controls (seekbar_osc_opacity) and the PC keyboard (keyboard_opacity, in tenths).
 */
fun setOverlayOpacity(context: Context, percent: Int) {
    val p = percent.coerceIn(10, 100)
    LegacyPrefs(context).prefs.edit().putInt(OVERLAY_OPACITY_KEY, p).putInt("seekbar_osc_opacity", p).apply()
    KeyboardSettings(context).setOpacity((p + 5) / 10)
}

fun overlayOpacity(context: Context): Int =
    (LegacyPrefs(context).prefs.all[OVERLAY_OPACITY_KEY] as? Int ?: DEFAULT_OVERLAY_OPACITY).coerceIn(10, 100)
