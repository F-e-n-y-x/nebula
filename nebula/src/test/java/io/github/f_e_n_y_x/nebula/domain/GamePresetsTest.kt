package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamePresetsTest {
    private val phone = 3120 to 1440
    private val global = StreamSettings(resolution = Resolution(1920, 1080), fps = 60, bitrateKbps = 30_000, codec = VideoCodec.AUTO)

    @Test fun resolutionsRoundTripAndFollowTheScreen() {
        listOf(PresetResolution.Native, PresetResolution.Screen(75), PresetResolution.Fixed(2560, 1440)).forEach {
            assertEquals(it, PresetResolution.decode(it.encode()))
        }
        assertEquals(PresetResolution.Native, PresetResolution.decode("screen:100"))
        assertNull(PresetResolution.decode("screen:5"))
        assertNull(PresetResolution.decode("10x10"))
        assertNull(PresetResolution.decode("wide"))
        assertEquals(Resolution(3120, 1440), PresetResolution.Native.resolve(phone))
        // 75% keeps the 13:6 shape exactly, on a phone and on a tablet alike.
        assertEquals(Resolution(2340, 1080), PresetResolution.Screen(75).resolve(phone))
        val tablet = PresetResolution.Screen(50).resolve(2560 to 1600)
        assertEquals(1280 to 800, tablet.width to tablet.height)
        assertEquals("1440p", PresetResolution.Fixed(2560, 1440).label())
        assertEquals("4K", PresetResolution.Fixed(3840, 2160).label())
        assertEquals("2560×1080", PresetResolution.Fixed(2560, 1080).label())
        assertEquals("67%", PresetResolution.Screen(67).label())
    }

    @Test fun presetsRoundTripThroughJsonAndIgnoreBadValues() {
        val p = GamePreset(PresetResolution.Screen(80), 120, 45_000, VideoCodec.HEVC, DisplayMode.MIRROR, frameGen = true, upscaler = "fsr1")
        assertEquals(p, GamePreset.fromJson(p.toJson().toString()))
        assertEquals(GamePreset.NONE, GamePreset.fromJson(null))
        assertEquals(GamePreset.NONE, GamePreset.fromJson("not json"))
        val bad = GamePreset.fromJson("""{"fps":5000,"bitrate_kbps":1,"codec":"VP9","display_mode":"TV","upscaler":"magic","resolution":"huge","frame_gen":false}""")
        assertEquals(GamePreset(frameGen = false), bad)
        assertTrue(GamePreset.NONE.isEmpty)
    }

    @Test fun thePresetWinsThenTheStreamMenuThenSettings() {
        val saved = VideoMode(2560, 1440, 90)
        // Nothing for the game: Settings.
        var e = GamePresets.effective(global, null, GamePreset.NONE, phone)
        assertEquals(Resolution(1920, 1080), e.resolution.value)
        assertEquals(SettingSource.GLOBAL, e.resolution.source)
        // "Use for this game" from the stream menu.
        e = GamePresets.effective(global, saved, GamePreset.NONE, phone)
        assertEquals(Resolution(2560, 1440), e.resolution.value)
        assertEquals(SettingSource.STREAM_MENU, e.fps.source)
        assertEquals(90, e.fps.value)
        // The preset sets size and bitrate; the frame rate still comes from the stream menu.
        e = GamePresets.effective(global, saved, GamePreset(resolution = PresetResolution.Screen(75), bitrateKbps = 50_000), phone)
        assertEquals(Resolution(2340, 1080), e.resolution.value)
        assertEquals(SettingSource.GAME, e.resolution.source)
        assertEquals(SettingSource.STREAM_MENU, e.fps.source)
        assertEquals(50_000, e.bitrateKbps.value)
        assertEquals(SettingSource.GLOBAL, e.codec.source)
        val applied = e.applyTo(global)
        assertEquals(Resolution(2340, 1080), applied.resolution)
        assertEquals(VideoMode(2340, 1080, 90), e.videoMode)
        // Native in Settings becomes this device's size.
        assertEquals(Resolution(3120, 1440), GamePresets.effective(StreamSettings(), null, GamePreset.NONE, phone).resolution.value)
    }

    @Test fun chipsShowOnlyWhatThePresetSets() {
        assertEquals(emptyList<String>(), GamePresets.chips(GamePreset.NONE))
        assertEquals(
            listOf("1440p", "120 fps", "40 Mbps", "HEVC", "Mirror", "FG", "FSR 1", "Driving"),
            GamePresets.chips(GamePreset(PresetResolution.Fixed(2560, 1440), 120, 40_000, VideoCodec.HEVC, DisplayMode.MIRROR, true, "fsr1"), "Driving"),
        )
        assertEquals("25.5 Mbps", GamePresets.formatMbps(25_500))
    }

    @Test fun aStreamMenuChoiceUpdatesAPresetThatSetsTheSize() {
        val mode = VideoMode(1920, 1080, 120)
        assertEquals(GamePreset(bitrateKbps = 1000), GamePresets.withStreamMenuChoice(GamePreset(bitrateKbps = 1000), mode))
        assertEquals(
            GamePreset(resolution = PresetResolution.Fixed(1920, 1080), fps = null),
            GamePresets.withStreamMenuChoice(GamePreset(resolution = PresetResolution.Screen(75)), mode),
        )
        assertEquals(GamePreset(fps = 120), GamePresets.withStreamMenuChoice(GamePreset(fps = 60), mode))
    }

    @Test fun aPresetDisplayModeWinsOverTheRememberedOne() {
        val g = Game("gta5", "h", "GTA V", GameKind.GAME, GameArt(), hostDefaultMode = DisplayMode.VIRTUAL)
        assertEquals(DisplayMode.MIRROR, ResolvePlayModeUseCase.resolve(g, DisplayMode.VIRTUAL, StreamSettings(), DisplayMode.MIRROR))
        assertEquals(DisplayMode.VIRTUAL, ResolvePlayModeUseCase.resolve(g, DisplayMode.VIRTUAL, StreamSettings(), null))
    }

    @Test fun hostProfileChipsAndChanges() {
        assertEquals(emptyList<String>(), HostGameProfile().chips())
        assertTrue(HostGameProfile().isDefault)
        assertEquals(
            listOf("60 fps cap", "FSR 2", "CAS", "≤ 40 Mbps", "Max power"),
            HostGameProfile(fpsCap = 60, fsr = 2, sharpening = true, bitrateKbps = 40_000, power = HostPower.PERFORMANCE).chips(),
        )
        assertEquals("power" to "balanced", HostProfileChange.Power(HostPower.BALANCED).let { it.key to it.value })
        assertEquals("bitrate_kbps" to 0, HostProfileChange.BitrateKbps(0).let { it.key to it.value })
    }
}
