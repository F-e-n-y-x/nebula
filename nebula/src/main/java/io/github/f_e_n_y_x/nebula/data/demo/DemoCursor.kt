package io.github.f_e_n_y_x.nebula.data.demo

import io.github.f_e_n_y_x.nebula.domain.model.RemoteCursorImage

/**
 * A desktop-style arrow like the one a PC would send (white with a black outline, hotspot at the
 * tip), so the demo host exercises the local cursor overlay without a PC.
 */
internal fun demoArrowCursor(): RemoteCursorImage {
    // The classic 12 × 19 arrow; '#' outline, '.' fill, ' ' transparent.
    val rows = listOf(
        "#           ",
        "##          ",
        "#.#         ",
        "#..#        ",
        "#...#       ",
        "#....#      ",
        "#.....#     ",
        "#......#    ",
        "#.......#   ",
        "#........#  ",
        "#.........# ",
        "#......#####",
        "#...#..#    ",
        "#..# #..#   ",
        "#.#  #..#   ",
        "##    #..#  ",
        "#     #..#  ",
        "       #..# ",
        "       ###  ",
    )
    val w = rows[0].length
    val h = rows.size
    val argb = IntArray(w * h) { i ->
        when (rows[i / w][i % w]) {
            '#' -> 0xFF000000.toInt()
            '.' -> 0xFFFFFFFF.toInt()
            else -> 0
        }
    }
    return RemoteCursorImage(id = 1, width = w, height = h, hotspotX = 0, hotspotY = 0, argb = argb)
}
