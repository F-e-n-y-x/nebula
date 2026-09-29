package io.github.f_e_n_y_x.nebula.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the About card shows. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val release: Release, val apk: ReleaseAsset) : UpdateState
    data class Downloading(val release: Release, val done: Long, val total: Long) : UpdateState
    data class Verifying(val release: Release) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Manual update flow for Settings → About: check, download, verify (SHA-256, package, signer,
 * version), then install through the system installer. Nothing downloads without a tap.
 */
class UpdateViewModel(
    context: Context,
    private val currentVersion: String,
    private val client: UpdateClient = UpdateClient(),
) : ViewModel() {
    private val app = context.applicationContext
    val settings = UpdateSettings(app).also { it.forgetLegacyToken() }
    private val dir = File(app.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Check when About opens, at most every six hours. */
    fun checkIfStale() {
        if (_state.value == UpdateState.Idle && System.currentTimeMillis() - settings.lastCheckMs > STALE_MS) check()
    }

    fun check() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { client.fetchReleases() }
            settings.lastCheckMs = System.currentTimeMillis()
            _state.value = when (result) {
                is CheckResult.Ok -> {
                    val next = Releases.newest(result.releases, currentVersion, settings.includePrereleases)
                    val apk = next?.let { Releases.pickApk(it.assets) }
                    when {
                        next == null -> UpdateState.UpToDate(currentVersion)
                        apk == null -> UpdateState.Error("${next.tag} has no APK attached.")
                        else -> UpdateState.Available(next, apk)
                    }
                }
                CheckResult.NotFound -> UpdateState.Error("Couldn't check: GitHub can't find the F-e-n-y-x/nebula releases.")
                CheckResult.RateLimited -> UpdateState.Error("Couldn't check: GitHub rate limit reached. Try again later.")
                is CheckResult.Failed -> UpdateState.Error("Couldn't check for updates (${result.message}).")
            }
        }
    }

    fun download() {
        val s = _state.value as? UpdateState.Available ?: return
        val release = s.release
        _state.value = UpdateState.Downloading(release, 0, s.apk.size)
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { fetchAndVerify(release, s.apk) }
        }
    }

    private fun fetchAndVerify(release: Release, apk: ReleaseAsset): UpdateState {
        val fromFile = Releases.pickChecksumAsset(release.assets, apk)?.let { a ->
            client.fetchText(a)?.let { Releases.parseChecksumFile(it, apk.name) }
        }
        val expected = when (val e = Releases.expectedSha256(Releases.digestHex(apk), fromFile)) {
            is Releases.Expected.Hash -> e.hex
            Releases.Expected.Missing -> return UpdateState.Error("${release.tag} has no SHA-256 checksum, so it won't be installed.")
            Releases.Expected.Conflict -> return UpdateState.Error("${release.tag}'s checksums disagree, so it won't be installed.")
        }
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // one update at a time; drop older downloads
        val file = File(dir, "nebula-${release.version}.apk")
        val actual = try {
            client.download(apk, file) { done, total -> _state.value = UpdateState.Downloading(release, done, total) }
        } catch (e: IOException) {
            return UpdateState.Error("Download failed: ${e.message}")
        }
        _state.value = UpdateState.Verifying(release)
        if (!actual.equals(expected, ignoreCase = true)) {
            file.delete()
            return UpdateState.Error("The download's SHA-256 doesn't match the release, so it was deleted.")
        }
        return when (val v = ApkVerifier.verify(app, file)) {
            is ApkVerifier.Result.Ok -> UpdateState.Ready(release, file)
            is ApkVerifier.Result.Rejected -> { file.delete(); UpdateState.Error(v.reason) }
        }
    }

    /** Hand the verified APK to the installer, or send the user to "Install unknown apps" first. */
    fun install(context: Context) {
        val s = _state.value as? UpdateState.Ready ?: return
        if (!UpdateInstaller.canInstall(context)) {
            UpdateInstaller.openInstallPermissionSettings(context)
            return
        }
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { runCatching { UpdateInstaller.install(app, s.file) }.exceptionOrNull() }
            if (error != null) _state.value = UpdateState.Error("Couldn't start the installer: ${error.message}")
        }
    }

    fun setIncludePrereleases(on: Boolean) {
        settings.includePrereleases = on
        _state.value = UpdateState.Idle
    }

    private companion object {
        const val STALE_MS = 6L * 60 * 60 * 1000
    }
}
