package io.github.f_e_n_y_x.nebula.data.engine

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import io.github.fenyx.nebula.engine.ArtKind
import io.github.fenyx.nebula.engine.NebulaEngine
import okio.Buffer
import java.io.IOException

/**
 * Loads host artwork through the engine (which authenticates with the client certificate and
 * caches on disk), so screens can pass plain `nebula-art://` / `nebula-shot://` strings to Coil.
 */
class EngineArtFetcher(private val engine: NebulaEngine, private val uri: android.net.Uri, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val host = uri.host ?: throw IOException("No host in $uri")
        val bytes = when (uri.scheme) {
            ART -> {
                val (app, kind) = uri.pathSegments.takeIf { it.size == 2 } ?: throw IOException("Bad art uri $uri")
                engine.loadArt(host, app, ArtKind.valueOf(kind.uppercase()))
            }
            else -> engine.loadScreenshot(host, uri.getQueryParameter("path") ?: throw IOException("Bad screenshot uri $uri"))
        } ?: throw IOException("The host has no artwork for $uri")
        return SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), mimeType = null, dataSource = DataSource.NETWORK)
    }

    class Factory(private val engine: NebulaEngine) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == ART || data.scheme == SHOT) EngineArtFetcher(engine, android.net.Uri.parse(data.toString()), options) else null
    }

    companion object {
        private const val ART = "nebula-art"
        private const val SHOT = "nebula-shot"

        fun artUri(hostId: String, appId: String, kind: ArtKind): String =
            android.net.Uri.Builder().scheme(ART).authority(hostId).appendPath(appId).appendPath(kind.wire).build().toString()

        fun screenshotUri(hostId: String, path: String): String =
            android.net.Uri.Builder().scheme(SHOT).authority(hostId).appendQueryParameter("path", path).build().toString()
    }
}
