package io.github.fenyx.nebula.engine

/**
 * A cursor image sent by the host for local drawing: [argb] is straight-alpha `0xAARRGGBB`, row by
 * row ([width] × [height]), the layout `Bitmap.createBitmap(int[], …)` takes. The host already
 * scaled it to the stream's resolution, so it only needs the video's own scaling on screen.
 */
class CursorShape(
    /** Host id; equal ids mean equal pixels, so a drawn bitmap can be reused. */
    val id: Int,
    val width: Int,
    val height: Int,
    /** The pixel that sits on the pointer position. */
    val hotspotX: Int,
    val hotspotY: Int,
    val argb: IntArray,
)

/** Where the local cursor stands with the host (control message 21, `LI_FF_CURSOR_SHAPE`). */
enum class LocalCursorStatus {
    /** Not requested: the host draws the cursor into the video. */
    OFF,

    /** Requested; waiting for the host's first cursor update. */
    WAITING,

    /** The host sends cursor shapes and leaves the cursor out of the video. */
    ACTIVE,

    /** The host doesn't offer it (no feature flag); its video keeps the cursor. */
    UNSUPPORTED,

    /** Requested but the host never answered, so Nebula went back to the in-video cursor. */
    FAILED,
}

/**
 * What the local cursor should show: nothing unless [status] is [LocalCursorStatus.ACTIVE] and
 * [visible] (the host hides it while a game hides its pointer). [shape] is null until the first
 * shape arrives.
 */
data class HostCursorState(
    val status: LocalCursorStatus = LocalCursorStatus.OFF,
    val visible: Boolean = false,
    val shape: CursorShape? = null,
)

/**
 * Turns the host's cursor updates (as moonlight-common-c reassembled them) into [HostCursorState]s.
 * Shapes are cached by id, like the V+ client, so the host can switch back to one it sent before
 * without resending the pixels. Thread safe.
 */
class HostCursorTracker(private val budgetBytes: Int = DEFAULT_BUDGET_BYTES) {
    private val cache = object : LinkedHashMap<Int, CursorShape>(16, 0.75f, true) {}
    private var cachedBytes = 0
    private var currentId: Int? = null
    private var visible = false

    /**
     * Applies one update. [flags] carries `LI_CURSOR_UPDATE_FLAG_SHAPE` (0x01) and `…_VISIBLE`
     * (0x02); [bgra] is the tightly packed BGRA pixels when the shape flag is set. Returns the
     * cursor to show, or null when the update was malformed and ignored.
     */
    @Synchronized
    fun onUpdate(flags: Int, shapeId: Int, width: Int, height: Int, hotspotX: Int, hotspotY: Int, bgra: ByteArray?): HostCursorState? {
        if (flags and FLAG_SHAPE != 0) {
            val shape = decode(shapeId, width, height, hotspotX, hotspotY, bgra) ?: return null
            put(shape)
        }
        currentId = shapeId
        visible = flags and FLAG_VISIBLE != 0
        return state()
    }

    /** The cursor as last updated (status ACTIVE; callers override the status as needed). */
    @Synchronized
    fun state(): HostCursorState = HostCursorState(LocalCursorStatus.ACTIVE, visible, currentId?.let { cache[it] })

    /** Forgets every shape (new connection). */
    @Synchronized
    fun reset() {
        cache.clear()
        cachedBytes = 0
        currentId = null
        visible = false
    }

    private fun put(shape: CursorShape) {
        cache.remove(shape.id)?.let { cachedBytes -= it.argb.size * 4 }
        cache[shape.id] = shape
        cachedBytes += shape.argb.size * 4
        val it = cache.entries.iterator()
        while (cachedBytes > budgetBytes && cache.size > 1 && it.hasNext()) {
            val oldest = it.next()
            if (oldest.key == shape.id) continue
            cachedBytes -= oldest.value.argb.size * 4
            it.remove()
        }
    }

    companion object {
        const val FLAG_SHAPE = 0x01
        const val FLAG_VISIBLE = 0x02
        const val MAX_DIMENSION = 256
        const val DEFAULT_BUDGET_BYTES = 8 * 1024 * 1024

        /** Validates a shape like the V+ client does and converts BGRA to ARGB; null if malformed. */
        fun decode(id: Int, width: Int, height: Int, hotspotX: Int, hotspotY: Int, bgra: ByteArray?): CursorShape? {
            if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION ||
                hotspotX !in 0 until width || hotspotY !in 0 until height ||
                bgra == null || bgra.size != width * height * 4
            ) {
                return null
            }
            return CursorShape(id, width, height, hotspotX, hotspotY, bgraToArgb(bgra))
        }

        /** Tightly packed BGRA bytes to `0xAARRGGBB` ints. */
        fun bgraToArgb(bgra: ByteArray): IntArray {
            require(bgra.size % 4 == 0) { "BGRA length must be a multiple of 4" }
            return IntArray(bgra.size / 4) { i ->
                val o = i * 4
                val b = bgra[o].toInt() and 0xFF
                val g = bgra[o + 1].toInt() and 0xFF
                val r = bgra[o + 2].toInt() and 0xFF
                val a = bgra[o + 3].toInt() and 0xFF
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
}
