package com.mytvsetup.launcher.ui.settings

import com.mytvsetup.launcher.content.*
import com.mytvsetup.launcher.ui.home.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResetTasteTest {
    @Test fun resetClearsSignalsAndRestoresRejectedAndDecidedTitles() = runBlocking {
        val source = FakeHomeContentSource(GenreCatalog(mapOf("Drama" to listOf("Drama"))))
        val original = source.swipeQueue.first()
        val title = original.first()
        source.decide(title, Signal.REJECT)
        assertFalse(source.forYou.first().any { it.id == title.id })
        assertFalse(source.swipeQueue.first().any { it.id == title.id })
        assertFalse(source.tasteSummary.top(5).isEmpty())
        source.resetTaste()
        assertEquals(original.map { it.id }, source.swipeQueue.first().map { it.id })
        assertTrue(source.tasteSummary.top(5).isEmpty())
    }
}
