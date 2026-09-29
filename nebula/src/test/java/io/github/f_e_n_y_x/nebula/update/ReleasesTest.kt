package io.github.f_e_n_y_x.nebula.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    private val hexA = "a".repeat(64)
    private val hexB = "b".repeat(64)

    private fun asset(id: Int, name: String, digest: String? = null) =
        """{"id":$id,"name":"$name","size":1000,"url":"https://api.github.com/repos/F-e-n-y-x/nebula/releases/assets/$id"${digest?.let { ",\"digest\":\"$it\"" } ?: ""}}"""

    private fun release(tag: String, draft: Boolean = false, pre: Boolean = false, assets: String = "") =
        """{"tag_name":"$tag","name":"Nebula $tag","draft":$draft,"prerelease":$pre,"body":"Notes for $tag","html_url":"https://github.com/x","assets":[$assets]}"""

    private val sample = "[" + listOf(
        release("nebula-v0.4.0-rc.1", pre = true, assets = asset(1, "nebula-0.4.0-rc.1-release.apk")),
        release("nebula-v0.3.2", draft = true, assets = asset(2, "nebula-0.3.2.apk")),
        release("nebula-v0.3.1", assets = asset(3, "nebula-0.3.1-debug.apk") + "," + asset(4, "nebula-0.3.1-release.apk", "sha256:$hexA") + "," + asset(5, "nebula-0.3.1-release.apk.sha256")),
        release("nova-v0.9.0", assets = asset(6, "nova.deb")),
        release("v2026.730.1", assets = asset(7, "x.apk")),
        release("nebula-v0.3.0", assets = asset(8, "nebula.apk")),
        """{"tag_name":42}""",
    ).joinToString(",") + "]"

    @Test fun `parse keeps only published nebula tags`() {
        val tags = Releases.parse(sample).map { it.tag }
        assertEquals(listOf("nebula-v0.4.0-rc.1", "nebula-v0.3.1", "nebula-v0.3.0"), tags)
        assertTrue(Releases.parse(sample).first().prerelease)
    }

    @Test fun `parse tolerates garbage`() {
        assertEquals(emptyList<Release>(), Releases.parse("{\"message\":\"Not Found\"}"))
        assertEquals(emptyList<Release>(), Releases.parse("not json"))
        assertEquals(emptyList<Release>(), Releases.parse("[]"))
    }

    @Test fun `json nulls read as empty`() {
        val json = """[{"tag_name":"nebula-v0.3.1","name":null,"body":null,"draft":false,"prerelease":false,
            "assets":[{"id":1,"name":"a.apk","size":1,"digest":null,"url":"https://api.github.com/x/1"}]}]"""
        val r = Releases.parse(json).single()
        assertEquals("", r.notes)
        assertEquals("nebula-v0.3.1", r.name)
        assertNull(r.assets.single().digest)
    }

    @Test fun `assets must point at api github com`() {
        val json = "[" + release("nebula-v0.3.1", assets = """{"id":1,"name":"a.apk","size":1,"url":"https://evil.example/a.apk"}""") + "]"
        assertEquals(emptyList<ReleaseAsset>(), Releases.parse(json).single().assets)
    }

    @Test fun `newest skips prereleases unless asked`() {
        val rs = Releases.parse(sample)
        assertEquals("nebula-v0.3.1", Releases.newest(rs, "0.3.0-dev5", includePrereleases = false)?.tag)
        assertEquals("nebula-v0.4.0-rc.1", Releases.newest(rs, "0.3.0-dev5", includePrereleases = true)?.tag)
        assertNull(Releases.newest(rs, "0.3.1-abc1234", includePrereleases = false))
        assertNull(Releases.newest(rs, "0.3.1-abc1234-dirty-debug", includePrereleases = false))
        assertNull(Releases.newest(rs, "unknown", includePrereleases = true))
    }

    @Test fun `picks the release apk and its checksum file`() {
        val r = Releases.parse(sample).first { it.tag == "nebula-v0.3.1" }
        val apk = Releases.pickApk(r.assets)!!
        assertEquals("nebula-0.3.1-release.apk", apk.name)
        assertEquals(hexA, Releases.digestHex(apk))
        assertEquals("nebula-0.3.1-release.apk.sha256", Releases.pickChecksumAsset(r.assets, apk)?.name)
        assertNull(Releases.pickApk(Releases.parse("[" + release("nebula-v1.0.0", assets = asset(1, "only-debug.apk")) + "]").single().assets))
    }

    @Test fun `digest must be a sha256`() {
        val a = ReleaseAsset(1, "a.apk", 1, "sha1:abc", "https://api.github.com/x")
        assertNull(Releases.digestHex(a))
        assertNull(Releases.digestHex(a.copy(digest = "sha256:1234")))
        assertEquals(hexA, Releases.digestHex(a.copy(digest = "SHA256:" + hexA.uppercase())))
    }

    @Test fun `parses checksum files`() {
        assertEquals(hexA, Releases.parseChecksumFile("$hexA\n", "x.apk"))
        assertEquals(hexB, Releases.parseChecksumFile("$hexA  other.apk\n$hexB *x.apk\n", "x.apk"))
        assertEquals(hexB, Releases.parseChecksumFile("# comment\n${hexB.uppercase()}  x.apk", "x.apk"))
        assertNull(Releases.parseChecksumFile("$hexA  other.apk", "x.apk"))
        assertNull(Releases.parseChecksumFile("$hexA\n$hexB\n", "x.apk"))
        assertNull(Releases.parseChecksumFile("nothing here", "x.apk"))
    }

    @Test fun `both checksum sources must agree`() {
        assertEquals(Releases.Expected.Hash(hexA), Releases.expectedSha256(hexA, hexA.uppercase()))
        assertEquals(Releases.Expected.Conflict, Releases.expectedSha256(hexA, hexB))
        assertEquals(Releases.Expected.Hash(hexB), Releases.expectedSha256(null, hexB))
        assertEquals(Releases.Expected.Hash(hexA), Releases.expectedSha256(hexA, null))
        assertEquals(Releases.Expected.Missing, Releases.expectedSha256(null, null))
    }

    @Test fun `signer sets must match exactly`() {
        assertTrue(SignerMatch.same(setOf("ab"), setOf("AB")))
        assertFalse(SignerMatch.same(setOf("ab"), setOf("cd")))
        assertFalse(SignerMatch.same(setOf("ab", "cd"), setOf("ab")))
        assertFalse(SignerMatch.same(emptySet(), emptySet()))
    }

    @Test fun `notes excerpt is bounded`() {
        assertEquals("short", Releases.notesExcerpt("  short \r\n"))
        assertEquals(401, Releases.notesExcerpt("x".repeat(1000)).length)
    }
}
