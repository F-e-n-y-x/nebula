package io.github.f_e_n_y_x.nebula.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Checks a downloaded APK against the running app before it is handed to the installer. */
object ApkVerifier {
    sealed interface Result {
        data class Ok(val versionName: String, val versionCode: Long) : Result
        data class Rejected(val reason: String) : Result
    }

    fun verify(context: Context, apk: File): Result {
        val pm = context.packageManager
        val own = context.packageName
        val candidate = archiveInfo(pm, apk) ?: return Result.Rejected("The download isn't a valid APK.")
        if (candidate.packageName != own) {
            return Result.Rejected(
                if (own.endsWith(".debug")) "This is the debug build of Nebula; release updates install over the release app only."
                else "The APK is for ${candidate.packageName}, not Nebula ($own).",
            )
        }
        val installed = runCatching { installedInfo(pm, own) }.getOrNull() ?: return Result.Rejected("Couldn't read Nebula's own signature.")
        val mine = signerDigests(installed)
        val theirs = signerDigests(candidate)
        if (!SignerMatch.same(mine, theirs)) {
            return Result.Rejected("The update is signed with a different key than this copy of Nebula, so it was not installed.")
        }
        val newCode = versionCode(candidate)
        val curCode = versionCode(installed)
        val newer = newCode > curCode || (newCode == curCode && isNewer(candidate.versionName, installed.versionName))
        if (!newer) return Result.Rejected("The APK (${candidate.versionName}, code $newCode) is not newer than the installed ${installed.versionName} (code $curCode).")
        return Result.Ok(candidate.versionName.orEmpty(), newCode)
    }

    @Suppress("DEPRECATION")
    private fun flags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(flags().toLong()))
        else pm.getPackageArchiveInfo(apk.path, flags())

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, pkg: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags().toLong()))
        else pm.getPackageInfo(pkg, flags())

    /** SHA-256 of each certificate that signs the APK contents (the current signer set, not the rotation history). */
    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val sigs: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            info.signatures ?: emptyArray()
        }
        return sigs.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
}

/** Hands a verified APK to the system installer through a PackageInstaller session. */
object UpdateInstaller {
    private val _events = MutableStateFlow<String?>(null)

    /** The last installer message (failure or cancel), for the About card. */
    val events: StateFlow<String?> = _events

    internal fun post(message: String?) { _events.value = message }

    /** Whether the user allowed Nebula to install apps ("Install unknown apps"). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Open the "Install unknown apps" switch for Nebula. */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Stream [apk] into a new install session and commit it; the system asks the user to confirm. */
    fun install(context: Context, apk: File) {
        _events.value = null
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("nebula.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, 64 * 1024)
                    session.fsync(out)
                }
            }
            // Explicit intent to our own non-exported receiver. It must be MUTABLE on API 31+ because
            // the installer fills in EXTRA_STATUS / EXTRA_INTENT; the explicit component means nobody
            // else can redirect it.
            val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}

/** Receives PackageInstaller status; not exported, so only the system (through our PendingIntent) reaches it. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return UpdateInstaller.post("The installer didn't open.")
                // The receiver is not exported, so this intent can only come from the system installer.
                // Defence in depth: if the target resolves (the manifest <queries> make the installer
                // visible), it must be a system app.
                val target = confirm.resolveActivity(context.packageManager)
                if (target != null) {
                    val system = runCatching {
                        context.packageManager.getApplicationInfo(target.packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
                    }.getOrDefault(false)
                    if (!system) return UpdateInstaller.post("Refused an installer screen that isn't from the system.")
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }.onFailure { UpdateInstaller.post("Couldn't open the installer.") }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstaller.post(null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateInstaller.post("Install cancelled.")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                UpdateInstaller.post("Install failed ($status)" + (msg?.let { ": $it" } ?: ""))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_INTENT)
}
