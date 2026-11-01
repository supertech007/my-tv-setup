package com.mytvsetup.launcher.data

import androidx.room.*
import com.mytvsetup.launcher.content.Signal
import com.mytvsetup.launcher.content.Title
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "titles")
data class TitleEntity(@PrimaryKey val id: String, val payload: String) {
    fun toTitle(): Title = json.decodeFromString(payload)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun from(title: Title): TitleEntity = TitleEntity(title.id, json.encodeToString(title))
    }
}

@Entity(tableName = "taste")
data class TasteEntity(@PrimaryKey val feature: String, val weight: Double)

@Entity(tableName = "decisions", indices = [Index("titleId")])
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleId: String, val signal: Signal, val at: Long
)

@Entity(tableName = "my_list")
data class MyListEntity(@PrimaryKey val titleId: String, val at: Long)

interface ContentDao {
    fun observeTitles(): Flow<List<TitleEntity>>
    fun observeTaste(): Flow<List<TasteEntity>>
    fun observeDecisions(): Flow<List<DecisionEntity>>
    suspend fun upsertTitles(items: List<TitleEntity>)
    suspend fun recordDecision(titleId: String, signal: Signal, at: Long, features: Set<String>)
    suspend fun clearLearning()
}

@Dao
abstract class RoomContentDao : ContentDao {
    @Query("SELECT * FROM titles ORDER BY id")
    abstract override fun observeTitles(): Flow<List<TitleEntity>>

    @Query("SELECT * FROM taste ORDER BY feature")
    abstract override fun observeTaste(): Flow<List<TasteEntity>>

    @Query("SELECT * FROM decisions ORDER BY id")
    abstract override fun observeDecisions(): Flow<List<DecisionEntity>>

    @Upsert
    abstract override suspend fun upsertTitles(items: List<TitleEntity>)

    @Insert
    protected abstract suspend fun insertDecision(decision: DecisionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertFeature(feature: TasteEntity)

    @Query("UPDATE taste SET weight = weight + :delta WHERE feature = :feature")
    protected abstract suspend fun incrementFeature(feature: String, delta: Double)

    @Upsert
    protected abstract suspend fun saveToMyList(item: MyListEntity)

    @Query("DELETE FROM my_list WHERE titleId = :titleId")
    protected abstract suspend fun removeFromMyList(titleId: String)

    @Query("SELECT * FROM my_list ORDER BY at DESC")
    abstract fun observeMyList(): Flow<List<MyListEntity>>

    @Query("DELETE FROM taste") protected abstract suspend fun deleteTaste()
    @Query("DELETE FROM decisions") protected abstract suspend fun deleteDecisions()
    @Transaction override suspend fun clearLearning() { deleteTaste(); deleteDecisions() }

    @Transaction
    override suspend fun recordDecision(titleId: String, signal: Signal, at: Long, features: Set<String>) {
        insertDecision(DecisionEntity(titleId = titleId, signal = signal, at = at))
        features.forEach { feature ->
            insertFeature(TasteEntity(feature, 0.0))
            incrementFeature(feature, signal.weight)
        }
        when (signal) {
            Signal.ACCEPT -> saveToMyList(MyListEntity(titleId, at))
            Signal.REJECT -> removeFromMyList(titleId)
            Signal.PLAY -> Unit
        }
    }
}

class SignalConverter {
    @TypeConverter fun encode(signal: Signal): String = signal.name
    @TypeConverter fun decode(value: String): Signal = Signal.valueOf(value)
}

@Database(
    entities = [TitleEntity::class, TasteEntity::class, DecisionEntity::class, MyListEntity::class],
    version = 1, exportSchema = true
)
@TypeConverters(SignalConverter::class)
abstract class Db : RoomDatabase() {
    abstract fun contentDao(): RoomContentDao
}
