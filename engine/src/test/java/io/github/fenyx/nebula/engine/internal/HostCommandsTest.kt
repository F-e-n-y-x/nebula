package io.github.fenyx.nebula.engine.internal

import com.limelight.nvstream.http.NvApp
import io.github.fenyx.nebula.engine.HostCommand
import io.github.fenyx.nebula.engine.HostCommandList
import io.github.fenyx.nebula.engine.HostRefusedException
import io.github.fenyx.nebula.engine.NovaCapabilities
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HostCommandParsingTest {
    @Test fun `capabilities carry the permission list when present`() {
        val caps = NovaApi.parseCapabilities("""{"nova":true,"version":"0.3","features":["pcsleep"],"permissions":["power"]}""")!!
        assertTrue(caps.has("pcsleep"))
        assertTrue(caps.allows("power"))
        assertFalse(caps.allows("host_commands"))
    }

    @Test fun `no permission list means actions are offered`() {
        val caps = NovaApi.parseCapabilities("""{"nova":true,"version":"0.3","features":[]}""")!!
        assertNull(caps.permissions)
        assertTrue(caps.allows("power"))
        assertEquals(NovaCapabilities("0.3", emptySet()), caps)
    }

    @Test fun `nova command list per the phase 1 contract`() {
        val list = NovaApi.parseCommands(
            """{"allowed":true,"commands":[
              {"id":"restart-steam","name":"Restart Steam","icon":"refresh","confirm":true,"scope":"app","app":"5f0c2a9e1b3d4c77",
               "runnable":false,"running":false,"last_run":{"at":1790000000,"exit_code":0,"ok":true,"timed_out":false}},
              {"id":"g-lock","name":"Lock screen","icon":"lock","confirm":false,"scope":"global","app":null,"runnable":true,"running":true,"last_run":null}]}""",
        )
        assertEquals(true, list.allowed)
        assertEquals(
            listOf(
                HostCommand("restart-steam", "Restart Steam", confirm = true, appNovaId = "5f0c2a9e1b3d4c77", icon = "refresh", runnable = false),
                HostCommand("g-lock", "Lock screen", confirm = false, appNovaId = null, icon = "lock", runnable = true, running = true),
            ),
            list.commands,
        )
    }

    @Test fun `not allowed means an empty list and allowed false`() {
        val list = NovaApi.parseCommands("""{"allowed":false,"commands":[{"id":"a","name":"A"}]}""")
        assertEquals(false, list.allowed)
        assertTrue(list.commands.isEmpty())
    }

    @Test fun `a bare array is accepted and confirm defaults to true`() {
        val list = NovaApi.parseCommands("""[{"id":7,"name":"Kill game","app":"gta5"},{"id":"x","name":"Global"}]""")
        assertNull(list.allowed)
        assertEquals("7", list.commands[0].id)
        assertEquals("gta5", list.commands[0].appNovaId)
        assertTrue(list.commands.all { it.confirm && it.runnable && !it.running })
    }

    @Test fun `entries without id or name, duplicates and junk are dropped`() {
        val list = NovaApi.parseCommands("""{"commands":[{"id":"a","name":"A"},{"id":"a","name":"Again"},{"name":"No id"},{"id":"b","name":"  "},3,null]}""")
        assertEquals(listOf("a"), list.commands.map { it.id })
        assertEquals(HostCommandList.Empty, NovaApi.parseCommands("   "))
        assertEquals(HostCommandList(null, emptyList()), NovaApi.parseCommands("""{"other":1}"""))
    }

    @Test fun `foundation SuperCmds string`() {
        val list = NovaApi.parseSuperCmds("""[{"id":"1","name":"Toggle HDR"},{"id":2,"name":"Mute Discord"}]""", appNovaId = "wukong")
        assertEquals(listOf("1" to "Toggle HDR", "2" to "Mute Discord"), list.map { it.id to it.name })
        assertTrue(list.all { it.appNovaId == "wukong" && it.confirm })
    }

    @Test fun `SuperCmds null, blank or malformed means none`() {
        listOf(null, "", "null", "  ", "{not json", "[{\"id\":").forEach {
            assertEquals("for '$it'", emptyList<HostCommand>(), NovaApi.parseSuperCmds(it))
        }
    }

    @Test fun `merged apps carry their SuperCmds`() {
        val app = NvApp("GTA V", 5, false).apply { setCmdList("""[{"id":"9","name":"Reset graphics"}]""") }
        val merged = HostRepository.mergeApps(listOf(app), emptyList(), 0)
        assertEquals(listOf(HostCommand("9", "Reset graphics")), merged.single().commands)
    }
}

class HostPowerTest {
    private suspend fun TestScope.added(backend: FakeBackend): Pair<HostRepository, String> {
        val r = HostRepository(backend, FakeStore(), null, backgroundScope, StandardTestDispatcher(testScheduler))
        val h = r.addManually("10.0.0.2")
        return r to h.id
    }

    @Test fun `sleep reaches the host`() = runTest {
        val backend = FakeBackend()
        val (r, id) = added(backend)
        r.sleep(id)
        assertEquals(1, backend.slept)
    }

    @Test fun `a refusal surfaces as HostRefusedException`() = runTest {
        val backend = FakeBackend().apply { sleepRefusal = "Not allowed" }
        val (r, id) = added(backend)
        try {
            r.sleep(id)
            fail("expected a refusal")
        } catch (e: HostRefusedException) {
            assertEquals(403, e.code)
        }
    }

    @Test fun `commands list and run`() = runTest {
        val backend = FakeBackend().apply { novaJson = mapOf(NovaApi.COMMANDS to """{"allowed":true,"commands":[{"id":"r","name":"Restart Steam","scope":"global"}]}""") }
        val (r, id) = added(backend)
        assertEquals(listOf("r"), r.commands(id).commands.map { it.id })
        r.runCommand(id, "r")
        assertEquals(listOf("r"), backend.commandsRun)
    }

    @Test fun `no command endpoint means an empty list`() = runTest {
        val (r, id) = added(FakeBackend())
        assertEquals(HostCommandList.Empty, r.commands(id))
        assertEquals(HostCommandList.Empty, r.commands("unknown"))
    }
}
