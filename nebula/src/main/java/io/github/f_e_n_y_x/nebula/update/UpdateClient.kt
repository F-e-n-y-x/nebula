package io.github.f_e_n_y_x.nebula.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Result of asking GitHub for the release list. */
sealed interface CheckResult {
    data class Ok(val releases: List<Release>) : CheckResult
    /** 401/403/404: the repository is gone or not public. Never an "update available". */
    data object NotFound : CheckResult
    data object RateLimited : CheckResult
    data class Failed(val message: String) : CheckResult
}

/**
 * GitHub releases for the public `F-e-n-y-x/nebula` repository, read anonymously.
 * Asset downloads answer with a redirect to a signed objects.githubusercontent.com URL.
 */
class UpdateClient(
    private val http: OkHttpClient = defaultClient(),
    private val apiBase: String = "https://api.github.com",
    private val repo: String = "F-e-n-y-x/nebula",
    /** Tests use plain-HTTP mock servers; production refuses anything but HTTPS. */
    private val allowHttp: Boolean = false,
) {
    fun fetchReleases(): CheckResult {
        val url = "$apiBase/repos/$repo/releases?per_page=20"
        if (!urlAllowed(url)) return CheckResult.Failed("Refusing a non-HTTPS update URL")
        val request = apiRequest(url, "application/vnd.github+json")
        return try {
            http.newCall(request).execute().use { r ->
                when {
                    r.isSuccessful -> {
                        val body = r.body.string()
                        if (body.length > MAX_JSON) CheckResult.Failed("Release list too large") else CheckResult.Ok(Releases.parse(body))
                    }
                    r.code == 403 && r.header("x-ratelimit-remaining") == "0" -> CheckResult.RateLimited
                    r.code == 429 -> CheckResult.RateLimited
                    r.code in listOf(401, 403, 404) -> CheckResult.NotFound
                    else -> CheckResult.Failed("GitHub answered ${r.code}")
                }
            }
        } catch (e: IOException) {
            CheckResult.Failed(e.message ?: "Network error")
        }
    }

    /** A small text asset (a checksum file); null when missing or too large. */
    fun fetchText(asset: ReleaseAsset, maxBytes: Long = 64 * 1024): String? {
        if (!urlAllowed(asset.apiUrl)) return null
        return try {
            http.newCall(apiRequest(asset.apiUrl, "application/octet-stream")).execute().use { r ->
                if (!r.isSuccessful) return null
                val bytes = r.body.source().use { src ->
                    val buf = okio.Buffer()
                    while (buf.size <= maxBytes && src.read(buf, 8192) != -1L) Unit
                    if (buf.size > maxBytes) return null
                    buf.readByteArray()
                }
                String(bytes, Charsets.UTF_8)
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Stream [asset] into [dest] (through a `.part` file), hashing as it goes.
     *
     * @return The SHA-256 of what was written, lowercase hex.
     * @throws IOException on HTTP errors, size-limit breaches or I/O failures; [dest] is not left behind.
     */
    fun download(asset: ReleaseAsset, dest: File, maxBytes: Long = MAX_APK, onProgress: (Long, Long) -> Unit = { _, _ -> }): String {
        if (!urlAllowed(asset.apiUrl)) throw IOException("Refusing a non-HTTPS download URL")
        if (asset.size > maxBytes) throw IOException("Update is larger than ${maxBytes / (1024 * 1024)} MB")
        val part = File(dest.parentFile, dest.name + ".part")
        part.delete()
        try {
            http.newCall(apiRequest(asset.apiUrl, "application/octet-stream")).execute().use { r ->
                checkDownload(r)
                val total = r.body.contentLength().takeIf { it > 0 } ?: asset.size
                if (total > maxBytes) throw IOException("Update is larger than ${maxBytes / (1024 * 1024)} MB")
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                r.body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > maxBytes) throw IOException("Update is larger than ${maxBytes / (1024 * 1024)} MB")
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                            onProgress(done, total)
                        }
                    }
                }
                if (asset.size > 0 && done != asset.size) throw IOException("Download incomplete ($done of ${asset.size} bytes)")
                dest.delete()
                if (!part.renameTo(dest)) throw IOException("Couldn't save the update")
                return digest.digest().joinToString("") { "%02x".format(it) }
            }
        } finally {
            part.delete()
        }
    }

    private fun checkDownload(r: Response) {
        if (!r.isSuccessful) throw IOException("Download failed (${r.code})")
    }

    private fun apiRequest(url: String, accept: String): Request =
        Request.Builder().url(url)
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Nebula-Updater")
            .build()

    private fun urlAllowed(url: String) = url.startsWith("https://") || (allowHttp && url.startsWith("http://"))

    companion object {
        const val MAX_APK = 300L * 1024 * 1024
        private const val MAX_JSON = 4 * 1024 * 1024

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false) // never downgrade https → http
            .build()
    }
}
