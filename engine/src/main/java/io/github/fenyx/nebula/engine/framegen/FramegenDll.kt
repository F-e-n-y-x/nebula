package io.github.fenyx.nebula.engine.framegen

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.limelight.LimeLog
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Lossless.dll: the proprietary Lossless Scaling file frame generation extracts its shaders from.
 *
 * A private build carries it as assets/framegen/Lossless.dll (+ .sha256; see nebula/build.gradle),
 * a public build never does. On first run the bundled copy is staged into noBackupFilesDir (so it
 * is never part of a cloud backup) and V+'s staged-path preference is set. A DLL the user picks
 * with the system file picker always wins and survives updates; "Reset to built-in" goes back.
 */
object FramegenDll {
    const val SOURCE_BUILTIN = "builtin"
    const val SOURCE_PICKED = "picked"

    private const val ASSET = "framegen/Lossless.dll"
    /** Lossless.dll is a few MB; anything larger than this is not it. */
    private const val MAX_BYTES = 64L * 1024 * 1024

    fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.let { it.getSharedPreferences(it.packageName + "_preferences", Context.MODE_PRIVATE) }

    /** SHA-256 of the bundled DLL, or null when this build has none (every public build). */
    fun bundledSha(ctx: Context): String? = try {
        ctx.assets.open("$ASSET.sha256").bufferedReader().use { it.readText().trim() }.ifEmpty { null }
    } catch (e: IOException) {
        null
    }

    fun hasBundled(ctx: Context): Boolean = bundledSha(ctx) != null

    fun stagedFile(ctx: Context): File = File(File(ctx.noBackupFilesDir, "framegen"), "Lossless.dll")

    fun stagedPath(prefs: SharedPreferences): String? =
        prefs.getString(FramegenKeys.DLL_STAGED_PATH, null)?.takeIf { it.isNotBlank() && File(it).let { f -> f.isFile && f.length() > 0 } }

    fun isBuiltin(prefs: SharedPreferences) = prefs.getString(FramegenKeys.DLL_SOURCE, null) == SOURCE_BUILTIN

    /** SHA-256 of the staged DLL (cached in prefs), or null when none is staged. */
    fun stagedSha(prefs: SharedPreferences): String? {
        val path = stagedPath(prefs) ?: return null
        prefs.getString(FramegenKeys.DLL_SHA, null)?.let { return it }
        return File(path).inputStream().use(::sha256).also { prefs.edit().putString(FramegenKeys.DLL_SHA, it).apply() }
    }

    /**
     * Stages the bundled DLL unless the user picked one; re-stages when a newer build bundles a
     * different file. Returns true when it copied. Call off the main thread.
     */
    @Synchronized
    fun ensureStaged(ctx: Context, prefs: SharedPreferences = prefs(ctx)): Boolean {
        val sha = bundledSha(ctx) ?: return false
        val ready = stagedPath(prefs) != null
        val source = prefs.getString(FramegenKeys.DLL_SOURCE, null)
        // A DLL staged before sources were tracked (V+ import) has no source: it is the user's.
        if (ready && source != SOURCE_BUILTIN) return false
        if (ready && prefs.getString(FramegenKeys.DLL_BUILTIN_SHA, null) == sha) return false
        stage(ctx, prefs, ctx.assets.open(ASSET), SOURCE_BUILTIN, sha)
        LimeLog.info("Framegen: staged the bundled Lossless.dll (${sha.take(8)})")
        return true
    }

    /** Replaces whatever is staged with the bundled DLL. */
    @Synchronized
    fun resetToBuiltin(ctx: Context, prefs: SharedPreferences = prefs(ctx)): Boolean {
        val sha = bundledSha(ctx) ?: return false
        stage(ctx, prefs, ctx.assets.open(ASSET), SOURCE_BUILTIN, sha)
        return true
    }

    /**
     * Copies a DLL the user picked (SAF [uri]) into place. Throws IOException with a readable
     * message when the file isn't a Windows DLL or is implausibly large.
     */
    @Synchronized
    fun importPicked(ctx: Context, uri: Uri, prefs: SharedPreferences = prefs(ctx)) {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) throw IOException("That file is too large to be Lossless.dll.")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: throw IOException("Couldn't open the file.")
        if (bytes.size < 2 || bytes[0] != 'M'.code.toByte() || bytes[1] != 'Z'.code.toByte()) {
            throw IOException("That isn't a Windows DLL. Pick Lossless.dll from the Lossless Scaling folder.")
        }
        val sha = sha256(bytes.inputStream())
        stage(ctx, prefs, bytes.inputStream(), SOURCE_PICKED, sha)
    }

    /** "Built-in · 7.2 MB · sha256 626b196d", or null when nothing is staged. */
    fun describe(prefs: SharedPreferences): String? {
        val path = stagedPath(prefs) ?: return null
        val size = String.format(Locale.US, "%.1f MB", File(path).length() / (1024.0 * 1024.0))
        val source = if (isBuiltin(prefs)) "Built-in" else "Your file"
        return listOfNotNull(source, size, stagedSha(prefs)?.let { "sha256 ${it.take(8)}" }).joinToString(" · ")
    }

    private fun stage(ctx: Context, prefs: SharedPreferences, input: InputStream, source: String, sha: String) {
        val target = stagedFile(ctx)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "Lossless.dll.tmp")
        input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        prefs.edit()
            .putString(FramegenKeys.DLL_STAGED_PATH, target.absolutePath)
            .putString(FramegenKeys.DLL_SOURCE, source)
            .putString(FramegenKeys.DLL_SHA, sha)
            .apply { if (source == SOURCE_BUILTIN) putString(FramegenKeys.DLL_BUILTIN_SHA, sha) }
            .apply()
    }

    internal fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
