package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickResumeTest {
    private val atom = Host("uuid-atom", "atom", "192.168.10.10", HostStatus.OFFLINE, paired = true, isNova = true)
    private val den = Host("uuid-den", "den", "192.168.10.24", HostStatus.ONLINE, paired = true, isNova = false)
    private val stranger = Host("uuid-x", "x", "192.168.10.99", HostStatus.ONLINE, paired = false, isNova = false)

    private fun play(host: String, game: String, at: Long, mode: DisplayMode = DisplayMode.VIRTUAL) =
        RecentPlay(host, host, game, game, mode, at)

    private fun running(host: String, game: String, display: DisplayMode?) =
        NowPlaying(host, host, game, game, sinceEpochS = 0, exact = true, display = display)

    // --- which PC ---

    @Test fun hostIsThePcOfTheLastGamePlayedEvenWhenAsleep() {
        val recents = listOf(play(den.id, "a", 1), play(atom.id, "b", 5))
        assertEquals(atom, QuickResume.host(recents, listOf(atom, den), lastHostId = den.id))
    }

    @Test fun hostSkipsUnpairedPcs() {
        val recents = listOf(play(stranger.id, "a", 9), play(den.id, "b", 1))
        assertEquals(den, QuickResume.host(recents, listOf(atom, den, stranger), lastHostId = null))
    }

    @Test fun hostFallsBackToLastLibraryThenAnyPairedPc() {
        assertEquals(den, QuickResume.host(emptyList(), listOf(atom, den), lastHostId = den.id))
        assertEquals(atom, QuickResume.host(emptyList(), listOf(atom, den), lastHostId = "gone"))
        assertNull(QuickResume.host(emptyList(), listOf(stranger), lastHostId = stranger.id))
    }

    // --- which game ---

    @Test fun resumesTheRunningGameOnItsDisplay() {
        val t = QuickResume.target(atom.id, running(atom.id, "elden", DisplayMode.MIRROR), listOf(play(atom.id, "gta5", 3)))
        assertEquals(QuickResume.Target.Play("elden", DisplayMode.MIRROR, running = true), t)
    }

    @Test fun otherwiseStartsTheLastGamePlayedThereWithItsMode() {
        val recents = listOf(play(atom.id, "old", 1), play(atom.id, "new", 7, DisplayMode.MIRROR), play(den.id, "newest", 9))
        assertEquals(QuickResume.Target.Play("new", DisplayMode.MIRROR, running = false), QuickResume.target(atom.id, null, recents))
    }

    @Test fun aGameRunningOnAnotherPcIsIgnored() {
        val t = QuickResume.target(atom.id, running(den.id, "elden", null), listOf(play(atom.id, "gta5", 3)))
        assertEquals(QuickResume.Target.Play("gta5", DisplayMode.VIRTUAL, running = false), t)
    }

    @Test fun nothingToResumeOpensTheLibrary() {
        assertEquals(QuickResume.Target.Library, QuickResume.target(atom.id, null, listOf(play(den.id, "a", 1))))
    }

    // --- the resume link ---

    @Test fun resumeLinkRoundTrips() {
        assertEquals("nebula://resume", DeepLinks.buildResume())
        assertEquals(ResumeLink(null), DeepLinks.parseResume(DeepLinks.buildResume()))
        assertEquals(ResumeLink("uuid atom"), DeepLinks.parseResume(DeepLinks.buildResume("uuid atom")))
        assertEquals(ResumeLink(null), DeepLinks.parseResume("NEBULA://Resume/"))
    }

    @Test fun resumeLinkIsStrict() {
        assertNull(DeepLinks.parseResume("nebula://resume/a/b"))
        assertNull(DeepLinks.parseResume("nebula://resume?host=atom"))
        assertNull(DeepLinks.parseResume("nebula://resume#x"))
        assertNull(DeepLinks.parseResume("nebula://user@resume"))
        assertNull(DeepLinks.parseResume("nebula://resume:8080"))
        assertNull(DeepLinks.parseResume("https://resume"))
        assertNull(DeepLinks.parseResume("nebula://play/atom/88"))
        assertNull(DeepLinks.parseResume("nebula://resume/a%0Ab"))
        assertNull(DeepLinks.parseResume("nebula://resume/" + "a".repeat(600)))
        // And the play parser never takes a resume link.
        assertNull(DeepLinks.parse("nebula://resume/atom"))
    }

    @Test fun resumeHostRefStillOnlyMatchesPairedPcs() {
        assertEquals(DeepLinks.HostMatch.Rejected(DeepLinks.Rejection.HOST_NOT_PAIRED), DeepLinks.resolveHostRef("x", listOf(atom, stranger)))
        assertEquals(DeepLinks.HostMatch.Paired(atom), DeepLinks.resolveHostRef("ATOM", listOf(atom, stranger)))
    }
}
