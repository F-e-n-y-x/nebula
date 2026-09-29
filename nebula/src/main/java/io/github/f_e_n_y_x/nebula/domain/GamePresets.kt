package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * The stream size a game preset asks for. "This screen" choices are kept as a share of the
 * device's screen, so one preset suits a phone and a tablet alike; standard and custom sizes are
 * kept as pixels.
 */
sealed interface PresetResolution {
    /** This device's full screen. */
    data object Native : PresetResolution

    /** A share of this device's screen with its exact aspect ratio (see [ResolutionOptions.SCREEN_SCALES]). */
    data class Screen(val percent: Int) : PresetResolution

    /** A fixed size in pixels (720p, 1080p, a custom size…). */
    data class Fixed(val width: Int, val height: Int) : PresetResolution

    /** Stored form: "native", "screen:75" or "1920x1080". */
    fun encode(): String = when (this) {
        Native -> "native"
        is Screen -> "screen:$percent"
        is Fixed -> "${width}x$height"
    }

    /** The size to stream at on a screen of [device] (width, height as the device reports it). */
    fun resolve(device: Pair<Int, Int>): Resolution = when (this) {
        Native -> Resolution(device.first, device.second)
        is Screen -> {
            val screen = Resolution(device.first, device.second)
            val factor = ResolutionOptions.SCREEN_SCALES.firstOrNull { it.first == percent }?.second ?: (percent / 100.0)
            if (percent >= 100 || screen.width <= 0 || screen.height <= 0) screen else ResolutionOptions.scaled(screen, factor)
        }
        is Fixed -> Resolution(width, height)
    }

    /** Short label for chips: "Native", "75%", "1080p" or "2560×1080". */
    fun label(): String = when (this) {
        Native -> "Native"
        is Screen -> "$percent%"
        is Fixed -> when {
            width * 9 == height * 16 && height in STANDARD_HEIGHTS -> if (height == 2160) "4K" else "${height}p"
            else -> "$width×$height"
        }
    }

    companion object {
        private val STANDARD_HEIGHTS = setOf(720, 1080, 1440, 2160)

        /** Parses [encode]'s output; null for anything else. */
        fun decode(raw: String?): PresetResolution? {
            val s = raw?.trim()?.lowercase() ?: return null
            if (s == "native") return Native
            if (s.startsWith("screen:")) {
                val pct = s.removePrefix("screen:").toIntOrNull() ?: return null
                return if (pct in 10..100) (if (pct == 100) Native else Screen(pct)) else null
            }
            val parts = s.split('x')
            if (parts.size != 2) return null
            val w = parts[0].toIntOrNull() ?: return null
            val h = parts[1].toIntOrNull() ?: return null
            return if (w in 64..8192 && h in 64..8192) Fixed(w, h) else null
        }
    }
}

/** Upscaler modes a preset can pick (the ids [io.github.fenyx.nebula.engine.framegen.UpscalerMode] uses). */
object PresetUpscaler {
    const val OFF = "off"
    val ALL = listOf(OFF, "sharpen", "sgsr1", "fsr1")

    fun label(id: String): String = when (id) {
        OFF -> "Off"
        "sharpen" -> "Sharpen"
        "sgsr1" -> "SGSR"
        "fsr1" -> "FSR 1"
        else -> id
    }
}

/**
 * Stream settings kept for one game on this device. Every field is optional: null means "use the
 * global setting". The on-screen controls choice is not here: it is the game's assignment in
 * [io.github.f_e_n_y_x.nebula.controls.ControlsStore], which the Game settings sheet edits too.
 */
data class GamePreset(
    val resolution: PresetResolution? = null,
    val fps: Int? = null,
    val bitrateKbps: Int? = null,
    val codec: VideoCodec? = null,
    val displayMode: DisplayMode? = null,
    /** Frame generation on or off for this game. */
    val frameGen: Boolean? = null,
    /** Upscaler mode id ([PresetUpscaler.ALL]). */
    val upscaler: String? = null,
) {
    val isEmpty: Boolean get() = this == NONE

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        resolution?.let { put("resolution", it.encode()) }
        fps?.let { put("fps", it) }
        bitrateKbps?.let { put("bitrate_kbps", it) }
        codec?.let { put("codec", it.name) }
        displayMode?.let { put("display_mode", it.name) }
        frameGen?.let { put("frame_gen", it) }
        upscaler?.let { put("upscaler", it) }
    }

    companion object {
        val NONE = GamePreset()
        const val MIN_FPS = 10
        const val MAX_FPS = 240
        const val MIN_BITRATE_KBPS = 500
        const val MAX_BITRATE_KBPS = 500_000

        /** Reads [toJson]'s output leniently: unknown or out-of-range values mean "use the global setting". */
        fun fromJson(raw: String?): GamePreset {
            if (raw.isNullOrBlank()) return NONE
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return NONE
            fun int(key: String, range: IntRange): Int? =
                if (o.has(key) && !o.isNull(key)) o.optInt(key, Int.MIN_VALUE).takeIf { it in range } else null
            return GamePreset(
                resolution = PresetResolution.decode(o.optString("resolution", "").ifEmpty { null }),
                fps = int("fps", MIN_FPS..MAX_FPS),
                bitrateKbps = int("bitrate_kbps", MIN_BITRATE_KBPS..MAX_BITRATE_KBPS),
                codec = o.optString("codec", "").let { c -> VideoCodec.entries.firstOrNull { it.name == c } },
                displayMode = o.optString("display_mode", "").let { m -> DisplayMode.entries.firstOrNull { it.name == m } },
                frameGen = if (o.has("frame_gen") && !o.isNull("frame_gen")) o.optBoolean("frame_gen") else null,
                upscaler = o.optString("upscaler", "").takeIf { it in PresetUpscaler.ALL },
            )
        }
    }
}

/** Where an effective setting comes from, shown next to it ("from Game settings"). */
enum class SettingSource {
    /** This game's preset. */
    GAME,
    /** "Use for this game from now on" in the stream menu (resolution and frame rate only). */
    STREAM_MENU,
    /** Settings › Stream (or the frame generation / upscaler settings). */
    GLOBAL,
}

data class Sourced<T>(val value: T, val source: SettingSource)

/** What a game streams with on this device, and why. */
data class EffectiveStream(
    val resolution: Sourced<Resolution>,
    val fps: Sourced<Int>,
    val bitrateKbps: Sourced<Int>,
    val codec: Sourced<VideoCodec>,
) {
    /** Global settings with this game's values filled in; the resolution is always a real size. */
    fun applyTo(settings: StreamSettings): StreamSettings =
        settings.copy(resolution = resolution.value, fps = fps.value, bitrateKbps = bitrateKbps.value, codec = codec.value)

    val videoMode: VideoMode get() = VideoMode(resolution.value.width, resolution.value.height, fps.value)
}

/**
 * The precedence rules for per-game settings: this game's preset, then (for size and frame rate)
 * a mode saved from the stream menu, then the global settings. Pure, so it is unit tested.
 */
object GamePresets {
    fun effective(global: StreamSettings, savedForGame: VideoMode?, preset: GamePreset, device: Pair<Int, Int>): EffectiveStream {
        val resolution = when {
            preset.resolution != null -> Sourced(preset.resolution.resolve(device), SettingSource.GAME)
            savedForGame != null -> Sourced(savedForGame.resolution, SettingSource.STREAM_MENU)
            else -> {
                val r = global.resolution
                Sourced(if (r.width > 0 && r.height > 0) r else Resolution(device.first, device.second), SettingSource.GLOBAL)
            }
        }
        val fps = when {
            preset.fps != null -> Sourced(preset.fps, SettingSource.GAME)
            savedForGame != null -> Sourced(savedForGame.fps, SettingSource.STREAM_MENU)
            else -> Sourced(global.fps, SettingSource.GLOBAL)
        }
        return EffectiveStream(
            resolution = resolution,
            fps = fps,
            bitrateKbps = preset.bitrateKbps?.let { Sourced(it, SettingSource.GAME) } ?: Sourced(global.bitrateKbps, SettingSource.GLOBAL),
            codec = preset.codec?.let { Sourced(it, SettingSource.GAME) } ?: Sourced(global.codec, SettingSource.GLOBAL),
        )
    }

    /**
     * The chips shown on the details page ("1440p · 120 fps · FG"): only what the preset sets,
     * plus the on-screen controls layout name when one is assigned to the game.
     */
    fun chips(preset: GamePreset, controlsName: String? = null): List<String> = buildList {
        preset.resolution?.let { add(it.label()) }
        preset.fps?.let { add("$it fps") }
        preset.bitrateKbps?.let { add(formatMbps(it)) }
        preset.codec?.let { add(codecLabel(it)) }
        preset.displayMode?.let { add(if (it == DisplayMode.VIRTUAL) "Virtual" else "Mirror") }
        preset.frameGen?.let { add(if (it) "FG" else "FG off") }
        preset.upscaler?.let { add(if (it == PresetUpscaler.OFF) "No upscaler" else PresetUpscaler.label(it)) }
        controlsName?.let { add(it) }
    }

    fun formatMbps(kbps: Int): String {
        val mbps = kbps / 1000.0
        return if (mbps == Math.floor(mbps)) "${mbps.toInt()} Mbps" else "%.1f Mbps".format(mbps)
    }

    fun codecLabel(codec: VideoCodec): String = when (codec) {
        VideoCodec.AUTO -> "Auto codec"
        VideoCodec.HEVC -> "HEVC"
        VideoCodec.H264 -> "H.264"
        VideoCodec.AV1 -> "AV1"
    }

    fun sourceLabel(source: SettingSource): String = when (source) {
        SettingSource.GAME -> "from Game settings"
        SettingSource.STREAM_MENU -> "saved from the stream menu"
        SettingSource.GLOBAL -> "from Settings"
    }

    /**
     * After "Use for this game from now on" in the stream menu: a preset that sets the size or
     * frame rate takes the new values, so the choice isn't hidden behind the preset.
     */
    fun withStreamMenuChoice(preset: GamePreset, mode: VideoMode): GamePreset =
        if (preset.resolution == null && preset.fps == null) preset
        else preset.copy(
            resolution = preset.resolution?.let { PresetResolution.Fixed(mode.width, mode.height) },
            fps = preset.fps?.let { mode.fps },
        )
}

/** Per-game presets on this device, keyed like the other per-game data ("hostId:gameId"). */
interface GamePresetRepository {
    fun observe(hostId: String, gameId: String): Flow<GamePreset>
    /** Every stored preset by "hostId:gameId". */
    fun observeAll(): Flow<Map<String, GamePreset>>
    suspend fun set(hostId: String, gameId: String, preset: GamePreset)
    suspend fun update(hostId: String, gameId: String, transform: (GamePreset) -> GamePreset)
}

/** Streaming power mode a game asks the PC for. */
enum class HostPower(val wire: String, val label: String) {
    DEFAULT("default", "Follow the PC's setting"),
    PERFORMANCE("performance", "Maximum performance"),
    BALANCED("balanced", "Don't change"),
}

/**
 * A game's settings on the PC (Nova's per-game performance profile): they apply whichever device
 * starts the game. Launch settings ([fpsCap], [fsr], [sharpening]) only reach the game when
 * [launchSettingsApply]; the bitrate limit and power mode always apply.
 */
data class HostGameProfile(
    val fpsCap: Int = 0,
    val fsr: Int = 0,
    val sharpening: Boolean = false,
    val bitrateKbps: Int = 0,
    val power: HostPower = HostPower.DEFAULT,
    val launchSettingsApply: Boolean = true,
    /** False when this device lacks the PC's "Change game settings" permission. */
    val canEdit: Boolean = true,
    val maxFpsCap: Int = 1000,
    val minBitrateKbps: Int = 500,
    val maxBitrateKbps: Int = 800_000,
) {
    val isDefault: Boolean get() = fpsCap <= 0 && fsr <= 0 && !sharpening && bitrateKbps <= 0 && power == HostPower.DEFAULT

    /** Short chips for the details page ("PC: 60 fps cap · FSR 2"). */
    fun chips(): List<String> = buildList {
        if (fpsCap > 0) add("$fpsCap fps cap")
        if (fsr > 0) add("FSR $fsr")
        if (sharpening) add("CAS")
        if (bitrateKbps > 0) add("≤ " + GamePresets.formatMbps(bitrateKbps))
        if (power == HostPower.PERFORMANCE) add("Max power")
        if (power == HostPower.BALANCED) add("Power unchanged")
    }
}

/** One change to a game's PC settings; the wire keys of POST /nova/v1/apps/<id>/profile. */
sealed interface HostProfileChange {
    val key: String
    val value: Any

    data class FpsCap(val fps: Int) : HostProfileChange { override val key = "fps_cap"; override val value: Any = fps }
    data class Fsr(val level: Int) : HostProfileChange { override val key = "fsr"; override val value: Any = level }
    data class Sharpening(val on: Boolean) : HostProfileChange { override val key = "vkbasalt"; override val value: Any = on }
    data class BitrateKbps(val kbps: Int) : HostProfileChange { override val key = "bitrate_kbps"; override val value: Any = kbps }
    data class Power(val mode: HostPower) : HostProfileChange { override val key = "power"; override val value: Any = mode.wire }
}
