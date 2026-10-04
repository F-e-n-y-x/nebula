package io.github.f_e_n_y_x.nebula.controls

/**
 * Puts a layout switch on a layout that has none, so every layout of a set can leave itself:
 * top centre, clear of the other controls. Look areas (zones) don't count as controls here;
 * a switch over a look area takes the touch first, like any other control.
 *
 * Positions are worked out on a reference screen (360 dp short side, the layout's aspect or a
 * phone's 2.17), the same way the library's previews are drawn.
 */
object SwitchPlacement {
    const val W = 88f
    const val H = 34f
    private const val GAP = 6f
    private const val EDGE = 8f
    private const val SHORT_SIDE = 360f
    private const val DEFAULT_ASPECT = 2.17f

    /** The reference area (width, height) in dp for [o]. */
    fun area(o: LayoutOrientation, aspect: Float? = null): Pair<Float, Float> {
        val a = (aspect ?: DEFAULT_ASPECT).coerceIn(1.3f, 3f)
        return if (o == LayoutOrientation.LANDSCAPE) SHORT_SIDE * a to SHORT_SIDE else SHORT_SIDE to SHORT_SIDE * a
    }

    private class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun hits(o: Box) = l < o.r && o.l < r && t < o.b && o.t < b
    }

    private fun boxOf(e: ControlElement, aw: Float, ah: Float): Box {
        val w = if (e.areaSized) e.width * aw else e.width
        val h = if (e.areaSized) e.height * ah else e.height
        val cx = e.x * aw
        val cy = e.y * ah
        return Box(cx - w / 2 - GAP, cy - h / 2 - GAP, cx + w / 2 + GAP, cy + h / 2 + GAP)
    }

    /**
     * Where a switch goes in [elements] (centre, as shares of the area): the top centre, else
     * the nearest free spot to it along the top, row by row down to under half the height;
     * the top centre again when nothing is free.
     */
    fun place(elements: List<ControlElement>, aw: Float, ah: Float): Pair<Float, Float> {
        val blocking = elements.filter { it.kind != ElementKind.ZONE }.map { boxOf(it, aw, ah) }
        val firstY = EDGE + H / 2
        var y = firstY
        while (y + H / 2 <= ah * 0.5f) {
            for (x in columns(aw)) {
                val me = Box(x - W / 2, y - H / 2, x + W / 2, y + H / 2)
                if (blocking.none { it.hits(me) }) return x / aw to y / ah
            }
            y += H + GAP
        }
        return 0.5f to firstY / ah
    }

    /** Centre first, then alternately left and right of it, up to 30% of the width away. */
    private fun columns(aw: Float): List<Float> {
        val c = aw / 2
        val step = (W + GAP) / 2
        val out = mutableListOf(c)
        var k = 1
        while (k * step <= aw * 0.3f) {
            for (x in listOf(c - k * step, c + k * step)) if (x - W / 2 >= EDGE && x + W / 2 <= aw - EDGE) out += x
            k++
        }
        return out
    }

    fun hasSwitch(elements: List<ControlElement>) = elements.any { it.kind == ElementKind.SWITCH }

    /** [elements] with a switch added when it has none. */
    fun withSwitch(elements: List<ControlElement>, o: LayoutOrientation, aspect: Float? = null, to: SwitchTarget = SwitchTarget.Next): List<ControlElement> {
        if (hasSwitch(elements)) return elements
        val (aw, ah) = area(o, aspect)
        val (x, y) = place(elements, aw, ah)
        val ids = elements.map { it.id }.toSet()
        var id = "switch"
        var n = 2
        while (id in ids) id = "switch-${n++}"
        return elements + newElement(ElementKind.SWITCH, id, x, y).copy(switchTo = to)
    }

    /** [p] with a switch on its landscape layout and, when it has its own, its portrait one. */
    fun ensure(p: ControlsProfile, to: SwitchTarget = SwitchTarget.Next): ControlsProfile {
        val aspect = p.meta?.aspect
        val land = withSwitch(p.landscape, LayoutOrientation.LANDSCAPE, aspect, to)
        val port = p.portrait?.takeIf { it.isNotEmpty() }?.let { withSwitch(it, LayoutOrientation.PORTRAIT, aspect, to) } ?: p.portrait
        return if (land === p.landscape && port === p.portrait) p else p.copy(landscape = land, portrait = port)
    }
}
