package io.github.f_e_n_y_x.nebula.update

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Updater settings in their own private SharedPreferences file (the app has allowBackup=false).
 *
 * The GitHub token is encrypted with an AES-GCM key that lives in the Android Keystore and never
 * leaves it, so the preferences file alone (a backup, a rooted copy) does not reveal the token. The
 * token is never logged and is only shown masked.
 */
class UpdateSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nebula_updater", Context.MODE_PRIVATE)

    var includePrereleases: Boolean
        get() = prefs.getBoolean(KEY_PRE, false)
        set(v) { prefs.edit().putBoolean(KEY_PRE, v).apply() }

    var lastCheckMs: Long
        get() = prefs.getLong(KEY_LAST, 0)
        set(v) { prefs.edit().putLong(KEY_LAST, v).apply() }

    val hasToken: Boolean get() = prefs.contains(KEY_TOKEN)

    /** The decrypted token, or null when unset or unreadable (for example after a Keystore reset). */
    fun token(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, IV_LEN)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
        }.getOrNull()
    }

    /** Store [token] encrypted; blank clears it. */
    fun setToken(token: String?) {
        val t = token?.trim().orEmpty()
        if (t.isEmpty()) {
            prefs.edit().remove(KEY_TOKEN).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(t.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_TOKEN, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEY_PRE = "include_prereleases"
        const val KEY_LAST = "last_check_ms"
        const val KEY_TOKEN = "github_token_enc"
        const val ALIAS = "nebula_updater_token"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}

/** `ghp_…abcd` style mask: never more than the last four characters. */
fun maskToken(token: String?): String = when {
    token.isNullOrEmpty() -> ""
    token.length <= 8 -> "••••"
    else -> "••••••••" + token.takeLast(4)
}
