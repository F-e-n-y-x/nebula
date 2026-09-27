package io.github.fenyx.nebula.engine.internal

import java.io.File
import java.security.MessageDigest

/**
 * Disk cache for host artwork, keyed by host + app + kind. Bounded by [maxBytes]; the least
 * recently used files are evicted first.
 */
class ArtCache(private val dir: File, private val maxBytes: Long = 64L * 1024 * 1024) {

    @Synchronized
    fun get(key: String): ByteArray? {
        val file = fileFor(key)
        if (!file.isFile) return null
        file.setLastModified(System.currentTimeMillis())
        return runCatching { file.readBytes() }.getOrNull()
    }

    @Synchronized
    fun put(key: String, bytes: ByteArray) {
        if (!dir.isDirectory && !dir.mkdirs()) return
        val tmp = File(dir, "${fileFor(key).name}.tmp")
        runCatching {
            tmp.writeBytes(bytes)
            tmp.renameTo(fileFor(key))
        }
        trim()
    }

    /** Drops every entry for [hostId], e.g. after unpairing. */
    @Synchronized
    fun clearHost(hostId: String) {
        val prefix = hash(hostId).take(HOST_PREFIX)
        dir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            total -= file.length()
            file.delete()
            if (total <= maxBytes) break
        }
    }

    private fun fileFor(key: String): File {
        val hostId = key.substringBefore('/')
        return File(dir, hash(hostId).take(HOST_PREFIX) + hash(key))
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val HOST_PREFIX = 8

        fun key(hostId: String, vararg parts: String) = (listOf(hostId) + parts).joinToString("/")
    }
}
