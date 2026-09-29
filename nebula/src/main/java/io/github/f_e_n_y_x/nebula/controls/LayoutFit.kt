package io.github.f_e_n_y_x.nebula.controls

import kotlin.math.min

/**
 * Fits a layout to this screen. Positions are already shares of the controls area, so only the
 * dp sizes change: presets are drawn for a phone's landscape area of about [REF_W_DP] ×
 * [REF_H_DP] dp; a tablet gets bigger buttons, a small phone slightly smaller ones, within
 * [MIN_SCALE]..[MAX_SCALE]. Afterwards every element is pulled back inside the area, so nothing
 * hangs off an edge on a narrower screen.
 */
object LayoutFit {
    const val REF_W_DP = 780f
    const val REF_H_DP = 360f
    const val MIN_SCALE = 0.8f
    const val MAX_SCALE = 1.35f

    fun scaleFor(areaWdp: Float, areaHdp: Float): Float {
        if (areaWdp <= 0f || areaHdp <= 0f) return 1f
        val long = maxOf(areaWdp, areaHdp)
        val short = min(areaWdp, areaHdp)
        return min(long / REF_W_DP, short / REF_H_DP).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    fun fit(elements: List<ControlElement>, areaWdp: Float, areaHdp: Float, scale: Float = scaleFor(areaWdp, areaHdp)): List<ControlElement> =
        elements.map { e ->
            val sized = if (e.areaSized) e else e.copy(width = e.width * scale, height = e.height * scale).clampedSize()
            inside(sized, areaWdp, areaHdp)
        }

    /** Moves [e] so it lies fully inside an area of [areaWdp] × [areaHdp] dp (if it can). */
    fun inside(e: ControlElement, areaWdp: Float, areaHdp: Float): ControlElement {
        if (areaWdp <= 0f || areaHdp <= 0f) return e
        val hw = (if (e.areaSized) e.width else e.width / areaWdp) / 2f
        val hh = (if (e.areaSized) e.height else e.height / areaHdp) / 2f
        val x = if (hw >= 0.5f) 0.5f else e.x.coerceIn(hw, 1f - hw)
        val y = if (hh >= 0.5f) 0.5f else e.y.coerceIn(hh, 1f - hh)
        return if (x == e.x && y == e.y) e else e.copy(x = x, y = y)
    }

    /** [p] with both layouts fitted. */
    fun fit(p: ControlsProfile, landWdp: Float, landHdp: Float): ControlsProfile = p.copy(
        landscape = fit(p.landscape, landWdp, landHdp),
        portrait = p.portrait?.let { fit(it, min(landWdp, landHdp), maxOf(landWdp, landHdp)) },
    )
}
