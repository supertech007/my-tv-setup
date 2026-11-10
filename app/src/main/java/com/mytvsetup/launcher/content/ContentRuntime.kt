package com.mytvsetup.launcher.content

import android.content.Context
import androidx.room.Room
import com.mytvsetup.launcher.BuildConfig
import com.mytvsetup.launcher.data.Db
import com.mytvsetup.launcher.music.JioSaavnClient
import com.mytvsetup.launcher.music.MusicCatalog
import com.mytvsetup.launcher.settings.PrefsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** One process-wide database, settings store and repository shared by Home and background refresh. */
object ContentRuntime {
    @Volatile private var instance: ContentRepository? = null

    fun repository(context: Context): ContentRepository = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    @Volatile private var musicInstance: MusicCatalog? = null
    private val http by lazy { OkHttpClient() }

    /** One music catalog, so Home and the background refresh see the same JioSaavn playlists. */
    fun music(context: Context): MusicCatalog = musicInstance ?: synchronized(this) {
        musicInstance ?: MusicCatalog(context.applicationContext.assets,
            store = context.applicationContext.filesDir.resolve("jiosaavn_playlists.json")).also { musicInstance = it }
    }

    @Volatile private var historyInstance: com.mytvsetup.launcher.history.WatchHistory? = null

    /** One watch history for the whole app: Home, YouTube and Swipe all add to it. */
    fun history(context: Context): com.mytvsetup.launcher.history.WatchHistory = historyInstance ?: synchronized(this) {
        historyInstance ?: com.mytvsetup.launcher.history.WatchHistory(context.applicationContext.filesDir.resolve("watch_history.json"))
            .also { historyInstance = it }
    }

    suspend fun refreshMusic(context: Context): Boolean = music(context).refresh(JioSaavnClient(http)::featured)

    private fun create(context: Context): ContentRepository {
        val db = Room.databaseBuilder(context, Db::class.java, "content.db").build()
        val genres = context.assets.open("genre_synonyms.json").bufferedReader().use {
            GenreCatalog.fromJson(it.readText())
        }
        return ContentRepository(
            db.contentDao(), genres, PrefsRepository(context),
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
            JustWatchClient(http), TmdbClient(http, BuildConfig.TMDB_KEY), music(context)
        )
    }
}
