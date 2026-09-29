package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinksTest {
    private val atom = Host("uuid-atom", "atom", "192.168.10.10", HostStatus.ONLINE, paired = true, isNova = true)
    private val den = Host("uuid-den", "den", "192.168.10.24", HostStatus.OFFLINE, paired = false, isNova = false)
    private val hosts = listOf(atom, den)
    private val games = listOf(
        Game("1234", atom.id, "Grand Theft Auto V", GameKind.GAME, GameArt()),
        Game("88", atom.id, "Far Cry 5", GameKind.GAME, GameArt()),
    )

    // --- parsing ---

    @Test fun parsesIdsAndMode() {
        assertEquals(PlayLink("uuid-atom", "1234", DisplayMode.VIRTUAL), DeepLinks.parse("nebula://play/uuid-atom/1234?display=virtual"))
        assertEquals(PlayLink("atom", "88", DisplayMode.MIRROR), DeepLinks.parse("nebula://play/atom/88?display=mirror"))
    }

    @Test fun modeIsOptionalAndCaseInsensitive() {
        assertNull(DeepLinks.parse("nebula://play/atom/88")!!.mode)
        assertEquals(DisplayMode.MIRROR, DeepLinks.parse("NEBULA://PLAY/atom/88?display=MIRROR")!!.mode)
        assertEquals(DisplayMode.VIRTUAL, DeepLinks.parse("nebula://play/atom/88?mode=virtual")!!.mode)
    }

    @Test fun decodesNamesWithSpacesAndPlus() {
        assertEquals("Grand Theft Auto V", DeepLinks.parse("nebula://play/atom/Grand%20Theft%20Auto%20V")!!.gameRef)
        assertEquals("C++ Game", DeepLinks.parse("nebula://play/atom/C++%20Game")!!.gameRef)
    }

    @Test fun rejectsOtherSchemesAndActions() {
        assertNull(DeepLinks.parse("https://play/atom/88"))
        assertNull(DeepLinks.parse("nebula://pair/atom/88"))
        assertNull(DeepLinks.parse("nebula://play.evil.com/atom/88"))
        assertNull(DeepLinks.parse("nebulax://play/atom/88"))
    }

    @Test fun rejectsWrongShapes() {
        assertNull(DeepLinks.parse(null))
        assertNull(DeepLinks.parse(""))
        assertNull(DeepLinks.parse("nebula://play/atom"))
        assertNull(DeepLinks.parse("nebula://play/atom/88/extra"))
        assertNull(DeepLinks.parse("nebula://play//88"))
        assertNull(DeepLinks.parse("nebula://play/atom/88?display=fullscreen"))
    }

    @Test fun rejectsAddressesUserInfoAndPorts() {
        assertNull(DeepLinks.parse("nebula://user@play/atom/88"))
        assertNull(DeepLinks.parse("nebula://play:47989/atom/88"))
    }

    @Test fun rejectsControlCharsSlashesAndOversize() {
        assertNull(DeepLinks.parse("nebula://play/atom/a%0Ab"))
        assertNull(DeepLinks.parse("nebula://play/atom/a%2Fb"))
        assertNull(DeepLinks.parse("nebula://play/atom/" + "x".repeat(200)))
        assertNull(DeepLinks.parse("nebula://play/atom/88?x=" + "y".repeat(600)))
    }

    @Test fun buildRoundTrips() {
        // Spaces and '+' survive; a '/' inside an id is rejected on purpose (see rejectsControlCharsSlashesAndOversize).
        val link = DeepLinks.build("uuid atom+1", "Grand Theft Auto V", DisplayMode.MIRROR)
        assertFalse(link.contains(' '))
        assertEquals(PlayLink("uuid atom+1", "Grand Theft Auto V", DisplayMode.MIRROR), DeepLinks.parse(link))
        val simple = DeepLinks.build("uuid-atom", "1234", DisplayMode.VIRTUAL)
        assertEquals("nebula://play/uuid-atom/1234?display=virtual", simple)
        assertEquals(PlayLink("uuid-atom", "1234", DisplayMode.VIRTUAL), DeepLinks.parse(simple))
    }

    // --- validation against paired hosts ---

    private fun host(ref: String) = DeepLinks.resolveHost(PlayLink(ref, "88", null), hosts)

    @Test fun pairedHostByIdOrName() {
        assertEquals(DeepLinks.HostMatch.Paired(atom), host("uuid-atom"))
        assertEquals(DeepLinks.HostMatch.Paired(atom), host("ATOM"))
    }

    @Test fun unpairedHostIsRejected() {
        assertEquals(DeepLinks.HostMatch.Rejected(DeepLinks.Rejection.HOST_NOT_PAIRED), host("uuid-den"))
        assertEquals(DeepLinks.HostMatch.Rejected(DeepLinks.Rejection.HOST_NOT_PAIRED), host("den"))
    }

    @Test fun unknownHostIsRejected() {
        assertEquals(DeepLinks.HostMatch.Rejected(DeepLinks.Rejection.UNKNOWN_HOST), host("192.168.10.10"))
        assertEquals(DeepLinks.HostMatch.Rejected(DeepLinks.Rejection.UNKNOWN_HOST), host("attacker"))
    }

    @Test fun ambiguousNamesAreRejected() {
        val twin = atom.copy(id = "uuid-atom-2")
        val r = DeepLinks.resolveHost(PlayLink("atom", "88", null), listOf(atom, twin))
        assertEquals(DeepLinks.HostMatch.Rejected(DeepLinks.Rejection.AMBIGUOUS_HOST), r)
        // The id still works.
        assertEquals(DeepLinks.HostMatch.Paired(twin), DeepLinks.resolveHost(PlayLink("uuid-atom-2", "88", null), listOf(atom, twin)))
    }

    @Test fun anUnpairedHostNamedLikeAPairedOneDoesNotShadowIt() {
        val impostor = den.copy(id = "uuid-x", name = "atom")
        assertEquals(DeepLinks.HostMatch.Paired(atom), DeepLinks.resolveHost(PlayLink("atom", "88", null), listOf(impostor, atom)))
    }

    @Test fun gameByIdThenUniqueName() {
        assertEquals("1234", DeepLinks.resolveGame(PlayLink("atom", "1234", null), games)?.id)
        assertEquals("88", DeepLinks.resolveGame(PlayLink("atom", "far cry 5", null), games)?.id)
        assertNull(DeepLinks.resolveGame(PlayLink("atom", "Half-Life 3", null), games))
    }

    @Test fun fullResolveNeverLoadsTheLibraryOfARejectedHost() = runTest {
        var asked = false
        val r = DeepLinks.resolve("nebula://play/den/88", hosts) { asked = true; games }
        assertEquals(DeepLinks.Resolved.Rejected(DeepLinks.Rejection.HOST_NOT_PAIRED), r)
        assertFalse(asked)
    }

    @Test fun fullResolveOk() = runTest {
        val r = DeepLinks.resolve("nebula://play/atom/1234?display=mirror", hosts) { games }
        assertTrue(r is DeepLinks.Resolved.Ok)
        r as DeepLinks.Resolved.Ok
        assertEquals(atom, r.host)
        assertEquals("1234", r.game.id)
        assertEquals(DisplayMode.MIRROR, r.mode)
        assertEquals(DeepLinks.Resolved.Rejected(DeepLinks.Rejection.MALFORMED), DeepLinks.resolve("nebula://play/atom", hosts) { games })
        assertEquals(DeepLinks.Resolved.Rejected(DeepLinks.Rejection.UNKNOWN_GAME), DeepLinks.resolve("nebula://play/atom/999", hosts) { games })
    }
}
