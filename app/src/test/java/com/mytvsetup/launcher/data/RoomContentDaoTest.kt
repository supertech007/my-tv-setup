package com.mytvsetup.launcher.data

import android.app.Application
import androidx.room.Room
import com.mytvsetup.launcher.content.Offer
import com.mytvsetup.launcher.content.Signal
import com.mytvsetup.launcher.content.testTitle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class RoomContentDaoTest {
    private lateinit var db: Db
    private lateinit var dao: RoomContentDao

    @Before fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), Db::class.java)
            .allowMainThreadQueries().build()
        dao = db.contentDao()
    }

    @After fun closeDatabase() { db.close() }

    @Test fun decisionsIncrementExistingTasteAndPersistEverySignalThroughConverter() = runBlocking {
        withTimeout(5_000) {
            dao.recordDecision("horror", Signal.ACCEPT, 10, setOf("g:Horror", "svc:prv"))
            dao.recordDecision("horror", Signal.PLAY, 20, setOf("g:Horror"))
            dao.recordDecision("comedy", Signal.REJECT, 30, setOf("g:Comedy"))
            assertEquals(mapOf("g:Horror" to 3.0, "svc:prv" to 1.0, "g:Comedy" to -1.0),
                dao.observeTaste().first().associate { it.feature to it.weight })
            val decisions = dao.observeDecisions().first()
            assertEquals(listOf("horror", "horror", "comedy"), decisions.map { it.titleId })
            assertEquals(listOf(Signal.ACCEPT, Signal.PLAY, Signal.REJECT), decisions.map { it.signal })
            assertEquals(listOf(10L, 20L, 30L), decisions.map { it.at })
            assertEquals(3, decisions.map { it.id }.distinct().size)
            db.openHelper.readableDatabase.query("SELECT signal FROM decisions ORDER BY id").use { cursor ->
                val stored = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertEquals(listOf("ACCEPT", "PLAY", "REJECT"), stored)
            }
        }
    }

    @Test fun acceptAddsMyListPlayPreservesItAndRejectRemovesIt() = runBlocking {
        withTimeout(5_000) {
            dao.recordDecision("title", Signal.ACCEPT, 10, emptySet())
            assertEquals(listOf(MyListEntity("title", 10)), dao.observeMyList().first())
            dao.recordDecision("title", Signal.PLAY, 20, emptySet())
            assertEquals(listOf(MyListEntity("title", 10)), dao.observeMyList().first())
            dao.recordDecision("title", Signal.ACCEPT, 30, emptySet())
            assertEquals(listOf(MyListEntity("title", 30)), dao.observeMyList().first())
            dao.recordDecision("title", Signal.REJECT, 40, emptySet())
            assertTrue(dao.observeMyList().first().isEmpty())
        }
    }

    @Test fun myListWriteFailureRollsBackDecisionAndTasteIncrementTogether() = runBlocking {
        withTimeout(5_000) {
            dao.recordDecision("existing", Signal.ACCEPT, 10, setOf("g:Horror"))
            // Fail the last write, after decision insertion and the existing taste increment.
            db.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER fail_my_list BEFORE INSERT ON my_list
                WHEN NEW.titleId = 'blocked'
                BEGIN SELECT RAISE(ABORT, 'forced my_list failure'); END
            """.trimIndent())
            val result = runCatching {
                dao.recordDecision("blocked", Signal.ACCEPT, 20, setOf("g:Horror", "g:Comedy"))
            }
            assertTrue(result.isFailure)
            assertEquals(listOf("existing"), dao.observeDecisions().first().map { it.titleId })
            assertEquals(listOf(TasteEntity("g:Horror", 1.0)), dao.observeTaste().first())
            assertEquals(listOf(MyListEntity("existing", 10)), dao.observeMyList().first())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_my_list")
            dao.recordDecision("blocked", Signal.ACCEPT, 30, setOf("g:Horror"))
            assertEquals(listOf(TasteEntity("g:Horror", 2.0)), dao.observeTaste().first())
        }
    }

    @Test fun titlePayloadRoundTripsAndUpsertKeepsUnrelatedTitles() = runBlocking {
        withTimeout(5_000) {
            val title = testTitle("title", listOf("paranormal")).copy(
                originalLanguage = "hi", offers = listOf(Offer("snl", listOf("hi", "en"), "sony")))
            val cached = testTitle("cached")
            dao.upsertTitles(listOf(TitleEntity.from(title), TitleEntity.from(cached)))
            val updated = title.copy(imdb = 8.5)
            dao.upsertTitles(listOf(TitleEntity.from(updated)))
            assertEquals(listOf(cached, updated), dao.observeTitles().first().map { it.toTitle() })
        }
    }
}
