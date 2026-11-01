package com.mytvsetup.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * How far a list scrolls to show a newly focused item: not at all while it is fully on screen, otherwise just
 * enough plus room for the focus lift. Android TV's default pivots on every focus change, which shook grids.
 */
fun stillScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
    val lift = size * (TILE_LIFT - 1f)
    return when {
        size > containerSize -> offset
        offset >= 0f && offset + size <= containerSize -> 0f
        offset < 0f -> offset - lift
        else -> offset + size - containerSize + lift
    }
}

@OptIn(ExperimentalFoundationApi::class)
val StillBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        stillScrollDistance(offset, size, containerSize)
}
