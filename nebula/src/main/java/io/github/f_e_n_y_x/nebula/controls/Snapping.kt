package io.github.f_e_n_y_x.nebula.controls

import kotlin.math.abs
import kotlin.math.roundToInt

/** An axis-aligned box in canvas pixels. */
data class Box(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
    val centerX get() = left + width / 2
    val centerY get() = top + height / 2
}

/** Where a moved element lands, and the alignment guides to draw (canvas x / y lines). */
data class SnapResult(val left: Float, val top: Float, val guidesX: List<Float> = emptyList(), val guidesY: List<Float> = emptyList())

/**
 * Editor snapping. Alignment with other elements (edges and centres) and with the screen's
 * centre lines wins when within [threshold]; otherwise the element's centre snaps to the grid.
 * The result always stays inside the canvas.
 */
object Snapping {
    fun toGrid(v: Float, grid: Float): Float = if (grid <= 0f) v else (v / grid).roundToInt() * grid

    fun move(
        moving: Box,
        others: List<Box>,
        canvasW: Float,
        canvasH: Float,
        grid: Float,
        threshold: Float,
        gridOn: Boolean,
        alignOn: Boolean = true,
    ): SnapResult {
        var left = moving.left
        var top = moving.top
        val guidesX = mutableListOf<Float>()
        val guidesY = mutableListOf<Float>()

        // Candidate lines: others' edges and centres, plus the canvas centre lines.
        val linesX = if (alignOn) others.flatMap { listOf(it.left, it.centerX, it.right) } + canvasW / 2 else emptyList()
        val linesY = if (alignOn) others.flatMap { listOf(it.top, it.centerY, it.bottom) } + canvasH / 2 else emptyList()

        val alignedX = bestAlign(listOf(0f, moving.width / 2, moving.width), left, linesX, threshold)
        if (alignedX != null) {
            left = alignedX.first
            guidesX += alignedX.second
        } else if (gridOn) {
            left = toGrid(left + moving.width / 2, grid) - moving.width / 2
        }
        val alignedY = bestAlign(listOf(0f, moving.height / 2, moving.height), top, linesY, threshold)
        if (alignedY != null) {
            top = alignedY.first
            guidesY += alignedY.second
        } else if (gridOn) {
            top = toGrid(top + moving.height / 2, grid) - moving.height / 2
        }
        left = left.coerceIn(0f, (canvasW - moving.width).coerceAtLeast(0f))
        top = top.coerceIn(0f, (canvasH - moving.height).coerceAtLeast(0f))
        return SnapResult(left, top, guidesX, guidesY)
    }

    /** Nearest (new start, line) that puts one of the element's [anchors] on a line, if within [threshold]. */
    private fun bestAlign(anchors: List<Float>, start: Float, lines: List<Float>, threshold: Float): Pair<Float, Float>? {
        var best: Pair<Float, Float>? = null
        var bestDist = threshold
        for (line in lines) for (a in anchors) {
            val d = abs(start + a - line)
            if (d <= bestDist) {
                bestDist = d
                best = (line - a) to line
            }
        }
        return best
    }

    /** A resized dimension (dp), snapped to [grid] when on and kept within the element limits. */
    fun size(dp: Float, grid: Float, gridOn: Boolean): Float {
        val v = if (gridOn) toGrid(dp, grid).coerceAtLeast(grid) else dp
        return v.coerceIn(ControlElement.MIN_SIZE_DP, ControlElement.MAX_SIZE_DP)
    }
}
