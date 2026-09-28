package io.github.f_e_n_y_x.nebula.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.f_e_n_y_x.nebula.domain.PortraitStreaming
import io.github.f_e_n_y_x.nebula.domain.PreferencesRepository
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.LibraryOptions
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nebulaPrefs by preferencesDataStore(name = "nebula")

class PreferencesStore(context: Context) : PreferencesRepository {
    private val store = context.applicationContext.nebulaPrefs

    private object Keys {
        val width = intPreferencesKey("stream_width")
        val height = intPreferencesKey("stream_height")
        val fps = intPreferencesKey("stream_fps")
        val bitrate = intPreferencesKey("stream_bitrate_kbps")
        val codec = stringPreferencesKey("stream_codec")
        val defaultMode = stringPreferencesKey("stream_default_mode")
        val portrait = stringPreferencesKey("stream_portrait_mode")
        val lastHost = stringPreferencesKey("last_host")
        val showDetails = booleanPreferencesKey("library_show_details")
        val showPlaytime = booleanPreferencesKey("library_show_playtime")
        val dataSaver = booleanPreferencesKey("library_data_saver")
        val cacheLimit = intPreferencesKey("library_cache_limit_mb")
        fun mode(hostId: String, gameId: String) = stringPreferencesKey("mode_${hostId}_$gameId")
        fun videoMode(hostId: String, gameId: String) = stringPreferencesKey("video_mode_${hostId}_$gameId")
    }

    override val streamSettings: Flow<StreamSettings> = store.data.map { p ->
        val d = StreamSettings()
        StreamSettings(
            resolution = Resolution(p[Keys.width] ?: d.resolution.width, p[Keys.height] ?: d.resolution.height),
            fps = p[Keys.fps] ?: d.fps,
            bitrateKbps = p[Keys.bitrate] ?: d.bitrateKbps,
            codec = p[Keys.codec]?.let { runCatching { VideoCodec.valueOf(it) }.getOrNull() } ?: d.codec,
            defaultMode = p[Keys.defaultMode]?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() } ?: d.defaultMode,
            portraitStreaming = p[Keys.portrait]?.let { runCatching { PortraitStreaming.valueOf(it) }.getOrNull() } ?: d.portraitStreaming,
        )
    }

    override suspend fun updateStreamSettings(transform: (StreamSettings) -> StreamSettings) {
        store.edit { p ->
            val current = StreamSettings(
                Resolution(p[Keys.width] ?: 0, p[Keys.height] ?: 0),
                p[Keys.fps] ?: 60,
                p[Keys.bitrate] ?: 30_000,
                p[Keys.codec]?.let { runCatching { VideoCodec.valueOf(it) }.getOrNull() } ?: VideoCodec.AUTO,
                p[Keys.defaultMode]?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() } ?: DisplayMode.VIRTUAL,
                p[Keys.portrait]?.let { runCatching { PortraitStreaming.valueOf(it) }.getOrNull() } ?: PortraitStreaming.OFF,
            )
            val next = transform(current)
            p[Keys.width] = next.resolution.width
            p[Keys.height] = next.resolution.height
            p[Keys.fps] = next.fps
            p[Keys.bitrate] = next.bitrateKbps
            p[Keys.codec] = next.codec.name
            p[Keys.defaultMode] = next.defaultMode.name
            p[Keys.portrait] = next.portraitStreaming.name
        }
    }

    override fun modeFor(hostId: String, gameId: String): Flow<DisplayMode?> =
        store.data.map { p -> p[Keys.mode(hostId, gameId)]?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() } }

    override suspend fun setMode(hostId: String, gameId: String, mode: DisplayMode) {
        store.edit { it[Keys.mode(hostId, gameId)] = mode.name }
    }

    override fun videoModeFor(hostId: String, gameId: String): Flow<VideoMode?> =
        store.data.map { p -> VideoMode.decode(p[Keys.videoMode(hostId, gameId)]) }

    override suspend fun setVideoMode(hostId: String, gameId: String, mode: VideoMode?) {
        store.edit { p ->
            val key = Keys.videoMode(hostId, gameId)
            if (mode == null) p.remove(key) else p[key] = mode.encode()
            Unit
        }
    }

    override val libraryOptions: Flow<LibraryOptions> = store.data.map { p ->
        val d = LibraryOptions()
        LibraryOptions(
            showDetails = p[Keys.showDetails] ?: d.showDetails,
            showPlaytime = p[Keys.showPlaytime] ?: d.showPlaytime,
            dataSaver = p[Keys.dataSaver] ?: d.dataSaver,
            cacheLimitMb = p[Keys.cacheLimit] ?: d.cacheLimitMb,
        )
    }

    override suspend fun updateLibraryOptions(transform: (LibraryOptions) -> LibraryOptions) {
        store.edit { p ->
            val d = LibraryOptions()
            val next = transform(
                LibraryOptions(
                    p[Keys.showDetails] ?: d.showDetails, p[Keys.showPlaytime] ?: d.showPlaytime,
                    p[Keys.dataSaver] ?: d.dataSaver, p[Keys.cacheLimit] ?: d.cacheLimitMb,
                ),
            )
            p[Keys.showDetails] = next.showDetails
            p[Keys.showPlaytime] = next.showPlaytime
            p[Keys.dataSaver] = next.dataSaver
            p[Keys.cacheLimit] = next.cacheLimitMb
        }
    }

    override val lastHostId: Flow<String?> = store.data.map { it[Keys.lastHost] }

    override suspend fun setLastHost(hostId: String) {
        store.edit { it[Keys.lastHost] = hostId }
    }
}
