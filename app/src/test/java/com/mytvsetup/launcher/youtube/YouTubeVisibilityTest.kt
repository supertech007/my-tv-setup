package com.mytvsetup.launcher.youtube

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class YouTubeVisibilityTest {
    private val all = listOf(
        YtVideo("1", "A video", "A channel", "", Instant.EPOCH, "A"),
        YtVideo("2", "B video", "B channel", "", Instant.EPOCH, "B"),
        YtVideo("3", "Another A video", "A channel", "", Instant.EPOCH, "A")
    )

    @Test fun hidingBRemovesAllItsVideosAndKeepsAInOrder() {
        assertEquals(listOf("1", "3"), visibleVideos(all, setOf("B")).map { it.id })
    }

    @Test fun unhidingRestoresBWithoutMutatingCache() {
        visibleVideos(all, setOf("B"))
        assertEquals(listOf("1", "2", "3"), visibleVideos(all, emptySet()).map { it.id })
    }
}
