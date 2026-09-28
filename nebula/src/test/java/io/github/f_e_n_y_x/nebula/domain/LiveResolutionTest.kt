package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import io.github.f_e_n_y_x.nebula.ui.StreamViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private val P1080 = VideoMode(1920, 1080, 60)
private val P1440 = VideoMode(2560, 1440, 120)
private val WIDE = VideoMode(5120, 2880, 60)

class LiveResolutionMachineTest {
    private fun reduce(s: SwitchState, e: SwitchEvent) = LiveResolutionMachine.reduce(s, e)

    @Test fun requestStartsASwitchFromTheCurrentMode() {
        assertEquals(SwitchState.Switching(P1080, P1440), reduce(SwitchState.Streaming(P1080), SwitchEvent.Request(P1440)))
    }

    @Test fun requestingTheCurrentModeIsANoOp() {
        val s = SwitchState.Streaming(P1080)
        assertSame(s, reduce(s, SwitchEvent.Request(P1080)))
    }

    @Test fun connectedFinishesTheSwitch() {
        assertEquals(SwitchState.Streaming(P1440), reduce(SwitchState.Switching(P1080, P1440), SwitchEvent.Connected))
    }

    @Test fun failureRollsBackToThePreviousMode() {
        assertEquals(
            SwitchState.RollingBack(failed = P1440, to = P1080, reason = "no"),
            reduce(SwitchState.Switching(P1080, P1440), SwitchEvent.Failed("no")),
        )
    }

    @Test fun rollbackThatConnectsIsBackAtThePreviousMode() {
        assertEquals(SwitchState.Streaming(P1080), reduce(SwitchState.RollingBack(P1440, P1080, "no"), SwitchEvent.Connected))
    }

    @Test fun rollbackThatFailsLosesTheStreamWithBothReasons() {
        val lost = reduce(SwitchState.RollingBack(P1440, P1080, "Encoder refused."), SwitchEvent.Failed("Host gone.")) as SwitchState.Lost
        assertTrue(lost.reason.contains("Encoder refused."))
        assertTrue(lost.reason.contains("Host gone."))
        assertTrue(lost.reason.contains(P1080.label))
    }

    @Test fun requestsDuringASwitchAreIgnored() {
        val switching = SwitchState.Switching(P1080, P1440)
        assertSame(switching, reduce(switching, SwitchEvent.Request(WIDE)))
        val back = SwitchState.RollingBack(P1440, P1080, "x")
        assertSame(back, reduce(back, SwitchEvent.Request(WIDE)))
    }

    @Test fun streamingIgnoresStrayConnectionEvents() {
        val s = SwitchState.Streaming(P1080)
        assertSame(s, reduce(s, SwitchEvent.Connected))
        assertSame(s, reduce(s, SwitchEvent.Failed("late")))
    }

    @Test fun lostIsTerminal() {
        val lost = SwitchState.Lost("gone")
        listOf(SwitchEvent.Request(P1440), SwitchEvent.Connected, SwitchEvent.Failed("x")).forEach { assertSame(lost, reduce(lost, it)) }
    }

    @Test fun onlySwitchingAndRollingBackAreBusy() {
        assertFalse(SwitchState.Streaming(P1080).busy)
        assertTrue(SwitchState.Switching(P1080, P1440).busy)
        assertTrue(SwitchState.RollingBack(P1440, P1080, "").busy)
        assertFalse(SwitchState.Lost("").busy)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveResolutionSwitcherTest {
    /** A fake connection: each attempt takes [latencyMs] and succeeds unless its mode is in [refuse]. */
    private class FakeHost(val latencyMs: Long = 1_800, val refuse: Set<VideoMode> = emptySet()) {
        val attempts = mutableListOf<VideoMode>()
        suspend fun reconnect(m: VideoMode): Result<Unit> {
            attempts += m
            delay(latencyMs)
            return if (m in refuse) Result.failure(IllegalStateException("Refused ${m.label}.")) else Result.success(Unit)
        }
    }

    private fun TestScope.switcher(host: FakeHost, timeoutMs: Long = 10_000) =
        LiveResolutionSwitcher(P1080, host::reconnect, clock = { testScheduler.currentTime }, attemptTimeoutMs = timeoutMs)

    @Test fun switchesAndReportsTheTimeItTook() = runTest {
        val host = FakeHost(latencyMs = 1_800)
        val sw = switcher(host)
        val outcome = sw.switchTo(P1440)
        assertEquals(SwitchOutcome.Switched(P1440, 1_800), outcome)
        assertEquals(SwitchState.Streaming(P1440), sw.state.value)
        assertEquals(P1440, sw.mode)
        assertEquals(listOf(P1440), host.attempts)
    }

    @Test fun showsSwitchingWhileTheReconnectRuns() = runTest(UnconfinedTestDispatcher()) {
        val sw = switcher(FakeHost())
        val seen = mutableListOf<SwitchState>()
        val watch = launch { sw.state.collect { seen += it } }
        val job = async { sw.switchTo(P1440) }
        assertEquals(SwitchState.Switching(P1080, P1440), sw.state.value)
        assertNull(sw.mode)
        job.await()
        yield()
        watch.cancel()
        assertEquals(listOf(SwitchState.Streaming(P1080), SwitchState.Switching(P1080, P1440), SwitchState.Streaming(P1440)), seen)
    }

    @Test fun aRefusedModeRollsBackToThePreviousOne() = runTest {
        val host = FakeHost(latencyMs = 1_000, refuse = setOf(WIDE))
        val sw = switcher(host)
        val outcome = sw.switchTo(WIDE) as SwitchOutcome.RolledBack
        assertEquals(WIDE, outcome.attempted)
        assertEquals(P1080, outcome.restored)
        assertEquals("Refused ${WIDE.label}.", outcome.reason)
        assertEquals(2_000, outcome.elapsedMs)
        assertEquals(listOf(WIDE, P1080), host.attempts)
        assertEquals(SwitchState.Streaming(P1080), sw.state.value)
    }

    @Test fun rollbackPassesThroughRollingBack() = runTest(UnconfinedTestDispatcher()) {
        val sw = switcher(FakeHost(refuse = setOf(WIDE)))
        val seen = mutableListOf<SwitchState>()
        val watch = launch { sw.state.collect { seen += it } }
        sw.switchTo(WIDE)
        yield()
        watch.cancel()
        assertEquals(
            listOf(
                SwitchState.Streaming(P1080),
                SwitchState.Switching(P1080, WIDE),
                SwitchState.RollingBack(WIDE, P1080, "Refused ${WIDE.label}."),
                SwitchState.Streaming(P1080),
            ),
            seen,
        )
    }

    @Test fun whenTheRollbackFailsTooTheStreamIsLost() = runTest {
        val sw = switcher(FakeHost(refuse = setOf(WIDE, P1080)))
        val outcome = sw.switchTo(WIDE)
        assertTrue(outcome is SwitchOutcome.Lost)
        assertTrue(sw.state.value is SwitchState.Lost)
        // Nothing more can be switched once lost.
        assertEquals(SwitchOutcome.Busy, sw.switchTo(P1440))
    }

    @Test fun anAttemptThatHangsTimesOutAndRollsBack() = runTest {
        var calls = 0
        val sw = LiveResolutionSwitcher(
            P1080,
            reconnect = { if (calls++ == 0) awaitCancellation() else Result.success(Unit) },
            clock = { testScheduler.currentTime },
            attemptTimeoutMs = 5_000,
        )
        val outcome = sw.switchTo(P1440) as SwitchOutcome.RolledBack
        assertTrue(outcome.reason, outcome.reason.contains("5 s"))
        assertEquals(5_000, outcome.elapsedMs)
        assertEquals(SwitchState.Streaming(P1080), sw.state.value)
    }

    // The owner's S25 against a Virtual display: stopping the old connection, a new surface,
    // /resume and the RTSP handshake took longer than the old 10 s budget, so the attempt was cut
    // off mid-handshake. The default budget leaves room for a slow host.
    @Test fun aSlowButHealthyReconnectIsNotCutOff() = runTest {
        val host = FakeHost(latencyMs = 14_000)
        val sw = LiveResolutionSwitcher(P1080, host::reconnect, clock = { testScheduler.currentTime })
        assertEquals(SwitchOutcome.Switched(P1440, 14_000), sw.switchTo(P1440))
        assertEquals(listOf(P1440), host.attempts)
        assertTrue(LiveResolutionSwitcher.DEFAULT_ATTEMPT_TIMEOUT_MS >= 20_000)
    }

    @Test fun anExceptionFromTheConnectionCountsAsAFailure() = runTest {
        var calls = 0
        val sw = LiveResolutionSwitcher(
            P1080,
            reconnect = { if (calls++ == 0) throw IllegalArgumentException("Surface gone.") else Result.success(Unit) },
            clock = { testScheduler.currentTime },
        )
        val outcome = sw.switchTo(P1440) as SwitchOutcome.RolledBack
        assertEquals("Surface gone.", outcome.reason)
    }

    @Test fun choosingTheCurrentModeDoesNothing() = runTest {
        val host = FakeHost()
        val sw = switcher(host)
        assertEquals(SwitchOutcome.Unchanged, sw.switchTo(P1080))
        assertTrue(host.attempts.isEmpty())
    }

    @Test fun aSecondRequestDuringASwitchIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        val attempts = mutableListOf<VideoMode>()
        val sw = LiveResolutionSwitcher(P1080, reconnect = { attempts += it; gate.await(); Result.success(Unit) }, clock = { testScheduler.currentTime })
        val first = async { sw.switchTo(P1440) }
        runCurrent()
        assertEquals(SwitchOutcome.Busy, sw.switchTo(WIDE))
        gate.complete(Unit)
        assertEquals(P1440, (first.await() as SwitchOutcome.Switched).mode)
        assertEquals(listOf(P1440), attempts)
    }

    @Test fun switchingBackAndForthKeepsTrackOfTheMode() = runTest {
        val sw = switcher(FakeHost(latencyMs = 500))
        sw.switchTo(P1440)
        sw.switchTo(P1080)
        assertEquals(SwitchState.Streaming(P1080), sw.state.value)
    }
}

class ResolutionOptionsTest {
    private val phone = Resolution(2340, 1080)

    @Test fun nativeFirstThenPresetsThenCustom() {
        val o = ResolutionOptions.sizes(phone, listOf(Resolution(3200, 1440), Resolution(1600, 720)), showLow = false)
        assertEquals(
            listOf(phone, Resolution(1280, 720), Resolution(1920, 1080), Resolution(2560, 1440), Resolution(3840, 2160), Resolution(1600, 720), Resolution(3200, 1440)),
            o.map { it.resolution },
        )
        assertEquals(ResolutionOption.Kind.NATIVE, o.first().kind)
        assertEquals("This device", o.first().label)
        assertEquals("2340×1080", o.first().size)
    }

    @Test fun eachSizeAppearsOnce() {
        // A 1080p TV: "This device" already is 1080p; a custom 720p duplicates the preset.
        val o = ResolutionOptions.sizes(Resolution(1920, 1080), listOf(Resolution(1280, 720), Resolution(1280, 720)), showLow = false)
        assertEquals(o.map { it.resolution }.distinct(), o.map { it.resolution })
        assertEquals(1, o.count { it.resolution == Resolution(1920, 1080) })
        assertEquals(ResolutionOption.Kind.NATIVE, o.first { it.resolution == Resolution(1920, 1080) }.kind)
    }

    @Test fun lowBandwidthPresetsOnlyWhenEnabled() {
        assertFalse(ResolutionOptions.sizes(phone, emptyList(), showLow = false).any { it.label == "360p" })
        val low = ResolutionOptions.sizes(phone, emptyList(), showLow = true)
        assertEquals(listOf("This device", "360p", "480p", "720p"), low.take(4).map { it.label })
    }

    @Test fun anUnlistedCurrentSizeIsStillOffered() {
        val o = ResolutionOptions.sizes(phone, emptyList(), showLow = false, current = Resolution(2000, 1000))
        assertEquals(Resolution(2000, 1000), o.last().resolution)
        assertEquals("Current", o.last().label)
        assertEquals(o.size, ResolutionOptions.sizes(phone, emptyList(), showLow = false, current = Resolution(1920, 1080)).size + 1)
    }

    @Test fun frameRatesIncludeTheCurrentAndScreenRate() {
        assertEquals(listOf(30, 60, 75, 90, 120, 144), ResolutionOptions.frameRates(current = 75, displayHz = 120))
        assertEquals(listOf(30, 60, 90, 120, 144, 165), ResolutionOptions.frameRates(current = 60, displayHz = 165))
    }

    @Test fun customSizesAreValidated() {
        assertNull(ResolutionOptions.validateCustom(2560, 1080))
        assertEquals("Enter a width and a height.", ResolutionOptions.validateCustom(null, 1080))
        assertTrue(ResolutionOptions.validateCustom(100, 1080)!!.startsWith("Width"))
        assertTrue(ResolutionOptions.validateCustom(1920, 5000)!!.startsWith("Height"))
        assertTrue(ResolutionOptions.validateCustom(1921, 1080)!!.contains("even"))
    }
}

class VideoModeTest {
    @Test fun encodesAndDecodes() {
        assertEquals("2560x1440x120", P1440.encode())
        assertEquals(P1440, VideoMode.decode(P1440.encode()))
        assertEquals("2560×1440@120", P1440.label)
    }

    @Test fun rejectsGarbage() {
        listOf(null, "", "1920x1080", "0x1080x60", "axbxc", "1920x1080x60x1").forEach { assertNull(it, VideoMode.decode(it)) }
    }

    @Test fun startingModePrefersTheGamesSavedMode() {
        val settings = StreamSettings(resolution = Resolution(1920, 1080), fps = 60)
        assertEquals(P1440, StreamViewModel.startingMode(settings, P1440, 2340 to 1080))
        assertEquals(P1080, StreamViewModel.startingMode(settings, null, 2340 to 1080))
        assertEquals(VideoMode(2340, 1080, 90), StreamViewModel.startingMode(StreamSettings(resolution = Resolution.Native, fps = 90), null, 2340 to 1080))
    }

    /** Regression (dev9.1): Settings at 120 fps must start the stream at 120, Virtual display or Mirror. */
    @Test fun settingsFrameRateReachesTheStartRequest() {
        val settings = StreamSettings(resolution = Resolution.Native, fps = 120)
        assertEquals(VideoMode(2340, 1080, 120), StreamViewModel.startingMode(settings, null, 2340 to 1080))
        // Only a mode the user saved for the game ("Use for this game") overrides it.
        assertEquals(VideoMode(2340, 1080, 60), StreamViewModel.startingMode(settings, VideoMode(2340, 1080, 60), 2340 to 1080))
    }
}
