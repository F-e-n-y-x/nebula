package io.github.f_e_n_y_x.nebula.update

import android.content.Context
import java.security.KeyStore

/** Updater settings in their own private SharedPreferences file (the app has allowBackup=false). */
class UpdateSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nebula_updater", Context.MODE_PRIVATE)

    var includePrereleases: Boolean
        get() = prefs.getBoolean(KEY_PRE, false)
        set(v) { prefs.edit().putBoolean(KEY_PRE, v).apply() }

    var lastCheckMs: Long
        get() = prefs.getLong(KEY_LAST, 0)
        set(v) { prefs.edit().putLong(KEY_LAST, v).apply() }

    /** Releases are public now: drop a GitHub token saved by 0.3.0, and its Keystore key. */
    fun forgetLegacyToken() {
        if (prefs.contains(KEY_TOKEN)) prefs.edit().remove(KEY_TOKEN).apply()
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        }
    }

    private companion object {
        const val KEY_PRE = "include_prereleases"
        const val KEY_LAST = "last_check_ms"
        const val KEY_TOKEN = "github_token_enc" // written by 0.3.0 only
        const val ALIAS = "nebula_updater_token"
    }
}
