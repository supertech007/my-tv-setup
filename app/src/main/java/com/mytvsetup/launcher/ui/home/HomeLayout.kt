package com.mytvsetup.launcher.ui.home

data class CardBox(val x: Int, val y: Int, val w: Int, val h: Int)

// The approved 1920×1080 design: the hideable cards share the area right of the clock.
private const val LEFT = 830
private const val RIGHT = 1840
private const val TOP = 72
private const val BOTTOM = 712
private val TOP_ROW = listOf("foryou" to 760, "swipe" to 220)
private val MIDDLE_ROW = listOf("music" to 320, "jarvis" to 360, "youtube" to 290)

/**
 * Where each visible card goes. Cards in a row split its width in their design proportions, so hiding one lets
 * its neighbours grow; a row with nothing left gives its height to the other row. No gaps are left behind.
 */
fun homeLayout(visible: Set<String>): Map<String, CardBox> {
    val top = TOP_ROW.filter { it.first in visible }
    val middle = MIDDLE_ROW.filter { it.first in visible }
    val rows = listOf(top to 30, middle to 20).filter { it.first.isNotEmpty() }
    val heights = if (rows.size == 2) listOf(400, 210) else listOf(BOTTOM - TOP)
    var y = TOP
    return buildMap {
        rows.forEachIndexed { i, (cards, gap) ->
            val space = RIGHT - LEFT - gap * (cards.size - 1)
            val total = cards.sumOf { it.second }
            var x = LEFT
            cards.forEachIndexed { j, (id, base) ->
                // The last card takes the rounding remainder so the row always ends at the right edge.
                val w = if (j == cards.lastIndex) RIGHT - x else base * space / total
                put(id, CardBox(x, y, w, heights[i]))
                x += w + gap
            }
            y += heights[i] + 30
        }
    }
}
