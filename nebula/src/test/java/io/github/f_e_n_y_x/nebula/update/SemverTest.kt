package io.github.f_e_n_y_x.nebula.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemverTest {
    @Test fun `parses tags and version names`() {
        assertEquals(Semver(0, 3, 0), Semver.parse("nebula-v0.3.0"))
        assertEquals(Semver(1, 2, 3), Semver.parse("v1.2.3"))
        assertEquals(Semver(0, 3, 0, listOf("dev5")), Semver.parse("0.3.0-dev5"))
        assertEquals(Semver(0, 3, 0, listOf("rc", "1")), Semver.parse("nebula-v0.3.0-rc.1"))
        assertEquals(Semver(0, 3, 0), Semver.parse("0.3.0+build.7"))
    }

    @Test fun `git hash, dirty and debug suffixes are build metadata`() {
        assertEquals(Semver(0, 3, 0), Semver.parse("0.3.0-abc1234"))
        assertEquals(Semver(0, 3, 0), Semver.parse("0.3.0-abc1234-dirty"))
        assertEquals(Semver(0, 3, 0), Semver.parse("0.3.0-dirty"))
        assertEquals(Semver(0, 3, 0, listOf("dev5")), Semver.parse("0.3.0-dev5-debug"))
        assertEquals(Semver(0, 3, 0, listOf("dev5")), Semver.parse("0.3.0-dev5-1d43d4a3"))
    }

    @Test fun `rejects non-semver`() {
        listOf(null, "", "latest", "v2026.730.002631.1", "0.3", "01.2.3", "0.3.0-", "0.3.0-01", "nebula-0.3.0-x..y").forEach {
            assertNull(it, Semver.parse(it))
        }
    }

    @Test fun `orders per semver 2 section 11`() {
        val ordered = listOf(
            "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2",
            "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0",
        ).map { Semver.parse(it)!! }
        for (i in 0 until ordered.size - 1) assertTrue("${ordered[i]} < ${ordered[i + 1]}", ordered[i] < ordered[i + 1])
        assertEquals(0, Semver.parse("0.3.0-abc1234")!!.compareTo(Semver.parse("0.3.0")!!))
    }

    @Test fun `update offered only for strictly newer releases`() {
        assertTrue(isNewer("nebula-v0.3.1", "0.3.0-abc1234-dirty"))
        assertFalse(isNewer("nebula-v0.3.0", "0.3.0-abc1234-dirty")) // local build of 0.3.0 plus commits
        assertTrue(isNewer("nebula-v0.3.0", "0.3.0-dev5")) // the release beats its dev builds
        assertFalse(isNewer("nebula-v0.3.0-dev4", "0.3.0-dev5"))
        // "dev10" and "dev5" are alphanumeric identifiers, so semver compares them lexically: tag
        // pre-releases as dev.10 / rc.2 (numeric identifiers) to get numeric order.
        assertFalse(isNewer("nebula-v0.3.0-dev10", "0.3.0-dev5-debug"))
        assertTrue(isNewer("nebula-v0.3.0-dev.10", "0.3.0-dev.5"))
        assertFalse(isNewer("nebula-v0.2.9", "0.3.0"))
        assertFalse(isNewer("garbage", "0.3.0"))
        assertFalse(isNewer("nebula-v9.9.9", "not-a-version"))
    }
}
