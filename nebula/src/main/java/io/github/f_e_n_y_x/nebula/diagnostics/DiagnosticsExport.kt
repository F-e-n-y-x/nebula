package io.github.f_e_n_y_x.nebula.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.BuildConfig
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Builds the diagnostics zip and hands text or files to other apps. */
object DiagnosticsExport {
    /** Which files the zip holds, in order; the UI lists them. */
    val CONTENTS = listOf(
        "logcat.txt" to "Nebula's log for the chosen window",
        "connection-timings.txt" to "Every connect stage, last 10 attempts",
        "sessions.txt" to "The last ${SessionHistory.KEEP} stream summaries",
        "capability-report.txt" to "This device, its decoders and the paired PCs",
        "controllers.txt" to "Connected controllers and their axis layouts",
        "stick-calibration.txt" to "Saved per-controller calibrations",
        "settings.txt" to "Stream settings, without keys or certificates",
    )

    /**
     * Writes the zip into the cache and returns it. [minutes] limits the log to that window (the
     * system keeps only a few MB of log anyway).
     */
    suspend fun buildZip(context: Context, container: AppContainer, history: SessionHistory, minutes: Int): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // one export at a time
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "nebula-diagnostics-$stamp.zip")
        val report = runCatching { ReportBuilder.build(CapabilityCollector(context, container).collect(probeHosts = false)).toText() }
            .getOrElse { "Couldn't build the report: $it" }
        val settings = settingsText(context, container)
        val parts = linkedMapOf(
            "README.txt" to readme(minutes),
            "logcat.txt" to logcat(minutes),
            "connection-timings.txt" to ConnectionTimeline.shared.describe(),
            "sessions.txt" to history.describe(),
            "capability-report.txt" to report,
            "controllers.txt" to ControllerInfo.describeAll(),
            "stick-calibration.txt" to StickCalibrations.describe(),
            "settings.txt" to settings,
        )
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            parts.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        file
    }

    /** The app's own log lines since [minutes] ago; apps only ever see their own. */
    fun logcat(minutes: Int): String {
        val since = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(System.currentTimeMillis() - minutes * 60_000L))
        return runCatching {
            val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", since).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor()
            text.ifBlank { "(no log lines in the last $minutes minutes)" }
        }.getOrElse { "Couldn't read the log: $it" }
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Nebula diagnostics ${BuildConfig.VERSION_NAME}")
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share diagnostics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Copies the zip to Downloads (Android 10+, no permission needed); returns where it went. */
    suspend fun saveToDownloads(context: Context, file: File): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val r = context.contentResolver
        val uri: Uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        r.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        r.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        "Downloads/${file.name}"
    }

    fun copyText(context: Context, label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareText(context: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Stream settings and V+'s preferences, minus anything that could identify or unlock a host. */
    private suspend fun settingsText(context: Context, container: AppContainer): String = buildString {
        runCatching { append("Stream settings: ").append(container.prefs.streamSettings.first()).append("\n\n") }
        val all = LegacyPrefs(context).prefs.all.toSortedMap()
        all.forEach { (k, v) -> append(k).append(" = ").append(if (isSecret(k)) "(hidden)" else v.toString()).append('\n') }
    }

    /** Keys that hold pairing material or identities. */
    fun isSecret(key: String): Boolean = SECRET.containsMatchIn(key.lowercase())

    private val SECRET = Regex("(cert|private|secret|token|passw|pairing|identity|uniqueid|unique_id|serial|mac_?addr|pin$|_pin_|^pin_)")

    private fun readme(minutes: Int) = """
        Nebula ${BuildConfig.VERSION_NAME} diagnostics
        Created ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())} on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})
        Log window: last $minutes minutes. Pairing keys, certificates and host secrets are never included.
    """.trimIndent() + "\n"

    private fun authority(context: Context) = context.packageName + ".diagnostics"

    private const val DIR = "diagnostics"
}
