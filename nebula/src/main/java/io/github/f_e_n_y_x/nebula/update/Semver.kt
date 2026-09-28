package io.github.f_e_n_y_x.nebula.update

/**
 * A release version, `X.Y.Z` with optional semver pre-release identifiers.
 *
 * Nebula's own version names carry build suffixes that are not pre-releases: a git hash
 * (`0.3.0-abc1234`, `0.3.0-abc1234-dirty`), `dirty`, and the debug build type's `-debug`. Those are
 * build metadata meaning "X.Y.Z plus local changes", so they are dropped and the version compares
 * equal to X.Y.Z: an update is offered only when a release is strictly newer than X.Y.Z. Every other
 * suffix (`-dev5`, `-rc.1`) is a pre-release and orders per semver 2.0 §11.
 */
data class Semver(val major: Int, val minor: Int, val patch: Int, val pre: List<String> = emptyList()) : Comparable<Semver> {

    val isPrerelease: Boolean get() = pre.isNotEmpty()

    override fun compareTo(other: Semver): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        // A release is greater than any of its pre-releases.
        if (pre.isEmpty() && other.pre.isEmpty()) return 0
        if (pre.isEmpty()) return 1
        if (other.pre.isEmpty()) return -1
        for (i in 0 until minOf(pre.size, other.pre.size)) {
            val c = compareIdentifier(pre[i], other.pre[i])
            if (c != 0) return c
        }
        return compareValues(pre.size, other.pre.size)
    }

    override fun toString(): String = "$major.$minor.$patch" + if (pre.isEmpty()) "" else pre.joinToString(".", prefix = "-")

    companion object {
        private val CORE = Regex("""^(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")
        private val HASH = Regex("^[0-9a-f]{7,40}$")
        private val IDENT = Regex("^[0-9A-Za-z-]+$")
        private val BUILD_WORDS = setOf("dirty", "debug")

        /** Parse a tag or version name (`nebula-v0.3.0`, `v0.3.0`, `0.3.0-rc.1`); null when it isn't semver. */
        fun parse(text: String?): Semver? {
            if (text == null) return null
            val bare = text.trim().removePrefix("nebula-").removePrefix("v")
            val m = CORE.matchEntire(bare) ?: return null
            val suffix = m.groupValues[4]
            val pre = if (suffix.isEmpty()) emptyList() else prereleaseOf(suffix) ?: return null
            return Semver(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), pre)
        }

        /**
         * Strip build-metadata words (git hash, dirty, debug) from the dash-separated suffix, then
         * split what remains into dot-separated pre-release identifiers.
         */
        private fun prereleaseOf(suffix: String): List<String>? {
            val kept = suffix.split('-').filter { part -> !(HASH.matches(part) || part in BUILD_WORDS) }
            if (kept.isEmpty()) return emptyList()
            val ids = kept.joinToString("-").split('.')
            if (ids.any { it.isEmpty() || !IDENT.matches(it) }) return null
            if (ids.any { it.length > 1 && it.all(Char::isDigit) && it.startsWith('0') }) return null
            return ids
        }

        private fun compareIdentifier(a: String, b: String): Int {
            val an = a.all(Char::isDigit)
            val bn = b.all(Char::isDigit)
            return when {
                an && bn -> compareValues(a.toBigInteger(), b.toBigInteger())
                an -> -1 // numeric identifiers have lower precedence than alphanumeric ones
                bn -> 1
                else -> a.compareTo(b)
            }
        }
    }
}

/** True only when both parse and [remote] is strictly newer; anything unparseable never offers an update. */
fun isNewer(remote: String?, current: String?): Boolean {
    val r = Semver.parse(remote) ?: return false
    val c = Semver.parse(current) ?: return false
    return r > c
}
