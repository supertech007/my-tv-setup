package com.mytvsetup.launcher.music

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import com.mytvsetup.launcher.apps.LaunchResult
import com.mytvsetup.launcher.apps.Launcher
import com.mytvsetup.launcher.content.Kind
import com.mytvsetup.launcher.content.MUSIC_LANGUAGES
import com.mytvsetup.launcher.content.Title
import com.mytvsetup.launcher.settings.Prefs
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Playlist(
    val id: String, val name: String, val lang: String, val tags: List<String>,
    val links: Map<String, String>, val search: String, val image: String? = null
)

interface MusicLauncher {
    fun openUrlIn(ctx: Context, pkg: String, url: String, label: String): LaunchResult
    fun openApp(ctx: Context, pkg: String, label: String): LaunchResult
}

private object AndroidMusicLauncher : MusicLauncher {
    override fun openUrlIn(ctx: Context, pkg: String, url: String, label: String): LaunchResult =
        Launcher.openUrlIn(ctx, pkg, url, label)
    override fun openApp(ctx: Context, pkg: String, label: String): LaunchResult =
        Launcher.openApp(ctx, pkg, label)
}

class MusicCatalog(
    private val bundled: List<Playlist>,
    private val prefs: Prefs = Prefs(),
    private val launcher: MusicLauncher = AndroidMusicLauncher,
    private val store: File? = null
) {
    constructor(assets: AssetManager, prefs: Prefs = Prefs(), store: File? = null) : this(
        runCatching {
            assets.open("playlists.json").bufferedReader().use { Json.decodeFromString<List<Playlist>>(it.readText()) }
        }.getOrDefault(emptyList()), prefs, AndroidMusicLauncher, store
    )

    private val state = MutableStateFlow(merge(runCatching {
        store?.takeIf { it.isFile }?.let { Json.decodeFromString<List<Playlist>>(it.readText()) }
    }.getOrNull().orEmpty()))
    /** Bundled playlists plus those fetched from JioSaavn; updates when a refresh lands. */
    val playlists: StateFlow<List<Playlist>> = state.asStateFlow()

    /** Keeps the current list when a fetch comes back empty; saves what it gets for the next start. */
    fun replaceFetched(fetched: List<Playlist>) {
        if (fetched.isEmpty()) return
        state.value = merge(fetched)
        runCatching { store?.writeText(Json.encodeToString(fetched)) }
    }

    /** Fetches every music language; a language that fails keeps nothing back from the others. */
    suspend fun refresh(fetch: suspend (String) -> List<Playlist>): Boolean {
        val fetched = MUSIC_LANGUAGES.flatMap { lang ->
            try { fetch(lang) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        }
        replaceFetched(fetched)
        return fetched.isNotEmpty()
    }

    // A fetched playlist with the same link replaces the bundled one: it has cover art and a current name.
    private fun merge(fetched: List<Playlist>): List<Playlist> {
        val fetchedLinks = fetched.flatMap { it.links.values }.toSet()
        return (fetched + bundled.filterNot { b -> b.links.values.any { it in fetchedLinks } }).distinctBy { it.id }
    }

    fun forLangs(langs: Set<String> = prefs.musicLangs): List<Playlist> =
        state.value.filter { it.lang in langs && it.lang in MUSIC_LANGUAGES }

    /** Stable asset order gives every selected playlist one day per rotation. */
    fun featuredPlaylist(today: LocalDate, langs: Set<String> = prefs.musicLangs): Playlist {
        val candidates = forLangs(langs)
        require(candidates.isNotEmpty()) { "No playlists for selected languages" }
        return candidates[Math.floorMod(today.toEpochDay(), candidates.size.toLong()).toInt()]
    }

    fun open(ctx: Context, p: Playlist, musicApp: String): LaunchResult {
        p.links[musicApp]?.let { return launcher.openUrlIn(ctx, musicApp, it, p.name) }
        // Some apps (Amazon Music on Android TV) cannot be sent to a playlist; JioSaavn plays the one picked.
        p.links[JIOSAAVN]?.let { url ->
            val result = launcher.openUrlIn(ctx, JIOSAAVN, url, p.name)
            if (result !is LaunchResult.NotInstalled) return result
        }
        val search = Intent(Intent.ACTION_SEARCH).setPackage(musicApp)
            .putExtra(SearchManager.QUERY, p.search)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(search)
            LaunchResult.Ok
        } catch (_: ActivityNotFoundException) {
            launcher.openApp(ctx, musicApp, p.name)
        } catch (_: SecurityException) {
            launcher.openApp(ctx, musicApp, p.name)
        }
    }
}

private const val JIOSAAVN = "com.jio.media.jiobeats"

/** Music stores Playlist.lang in originalLanguage and never fabricates streaming offers. */
fun Playlist.toTitle(): Title = Title(
    "music:$id", Kind.MUSIC, name, null, null, tags, "", emptyList(),
    image, null, emptyList(), null, originalLanguage = lang
)
