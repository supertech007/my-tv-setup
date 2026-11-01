package com.mytvsetup.launcher.music

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.mytvsetup.launcher.apps.LaunchResult
import com.mytvsetup.launcher.content.Kind
import com.mytvsetup.launcher.settings.Prefs
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = android.app.Application::class)
class MusicCatalogTest {
    private val playlists = Json.decodeFromString<List<Playlist>>(
        javaClass.getResource("/music_playlists.json")!!.readText()
    )
    private val catalog = MusicCatalog(playlists)
    private val punjabi = playlists.first()

    @Test fun requestingPunjabiReturnsOnlyPunjabi() {
        assertEquals(listOf("pa"), catalog.forLangs(setOf("pa")).map { it.id })
    }

    @Test fun tamilEntryInJsonNeverAppearsForDefaultOrAnUnsupportedSelection() {
        assertTrue(playlists.any { it.lang == "ta" })
        assertEquals(listOf("pa", "bgc", "hi", "en"), catalog.forLangs(Prefs().musicLangs).map { it.lang })
        assertEquals(emptyList<Playlist>(), catalog.forLangs(setOf("ta")))
        assertEquals(emptyList<Playlist>(), catalog.forLangs(emptySet()))
        assertEquals(listOf("bgc"), catalog.forLangs(setOf("bgc", "ta")).map { it.lang })
    }

    @Test fun defaultsUseTheProvidedMusicPreferences() {
        assertEquals(listOf("bgc"), MusicCatalog(playlists, Prefs(musicLangs = setOf("bgc")))
            .forLangs().map { it.lang })
    }

    @Test fun bundledAssetProvidesFourUniqueUsablePlaylistsPerSupportedLanguage() {
        val bundled = MusicCatalog(RuntimeEnvironment.getApplication().assets, Prefs())
        val all = bundled.forLangs(Prefs().musicLangs)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (lang in listOf("pa", "bgc", "hi", "en")) {
            assertTrue("At least four playlists for $lang", bundled.forLangs(setOf(lang)).size >= 4)
        }
        assertTrue(all.all { p -> p.tags.isNotEmpty() && p.search.isNotBlank() &&
            p.links.getValue("com.jio.media.jiobeats").startsWith("https://www.jiosaavn.com/featured/") })
    }

    @Test fun featuredPlaylistIsStableForTheDateAndRotatesWithinAllowedLanguages() {
        val epoch = LocalDate.of(1970, 1, 1)
        val langs = Prefs().musicLangs
        assertEquals("pa", catalog.featuredPlaylist(epoch, langs).id)
        assertEquals("pa", catalog.featuredPlaylist(epoch, langs).id)
        assertEquals("bgc", catalog.featuredPlaylist(epoch.plusDays(1), langs).id)
        assertEquals("en", catalog.featuredPlaylist(epoch.minusDays(1), langs).id)
        assertEquals("pa", catalog.featuredPlaylist(epoch.plusDays(4), langs).id)
        assertEquals("hi", catalog.featuredPlaylist(epoch, setOf("hi")).lang)
    }

    @Test(expected = IllegalArgumentException::class)
    fun featuredPlaylistRequiresAtLeastOneAllowedLanguage() {
        catalog.featuredPlaylist(LocalDate.of(2026, 11, 1), emptySet())
    }

    @Test fun titleCarriesPlaylistTagsAndLanguageWithoutFabricatingVideoOffers() {
        val title = punjabi.toTitle()
        assertEquals("music:pa", title.id)
        assertEquals(Kind.MUSIC, title.kind)
        assertEquals("Punjabi Hits", title.title)
        assertEquals(listOf("Pop", "Dance"), title.labels)
        assertEquals("pa", title.originalLanguage)
        assertTrue(title.offers.isEmpty())
    }

    @Test fun openChoosesTheSelectedAppsLink() {
        val launcher = RecordingLauncher()
        val context = RecordingContext()
        assertEquals(LaunchResult.Ok, MusicCatalog(playlists, launcher = launcher)
            .open(context, punjabi, "other.music"))
        assertEquals(listOf("other.music|https://example.com/punjabi|Punjabi Hits"), launcher.urls)
        assertTrue(context.intents.isEmpty())
        assertTrue(launcher.apps.isEmpty())
    }

    @Test fun appsThatCannotOpenThePlaylistGetItInJioSaavn() {
        // Amazon Music on Android TV ignores every playlist and search link (checked on the TV 1 Nov).
        val launcher = RecordingLauncher()
        val context = RecordingContext()
        assertEquals(LaunchResult.Ok, MusicCatalog(playlists, launcher = launcher)
            .open(context, punjabi, "com.amazon.music.tv"))
        assertEquals(listOf("com.jio.media.jiobeats|https://www.jiosaavn.com/featured/punjabi-hit-songs/T,w3Z-u7t6A_|Punjabi Hits"),
            launcher.urls)
        assertTrue(context.intents.isEmpty())
    }

    @Test fun withoutJioSaavnTheChosenAppIsSearched() {
        val launcher = RecordingLauncher(LaunchResult.NotInstalled("JioSaavn"))
        val context = RecordingContext()
        assertEquals(LaunchResult.Ok, MusicCatalog(playlists, launcher = launcher)
            .open(context, punjabi, "com.amazon.music.tv"))
        assertEquals("com.amazon.music.tv", context.intents.single().`package`)
    }

    @Test fun absentLinkSearchesOnlyTheSelectedAppWithTheExactQuery() {
        val launcher = RecordingLauncher()
        val context = RecordingContext()
        val noLinks = punjabi.copy(links = emptyMap())
        assertEquals(LaunchResult.Ok, MusicCatalog(playlists, launcher = launcher)
            .open(context, noLinks, "com.amazon.music.tv"))
        val intent = context.intents.single()
        assertEquals(Intent.ACTION_SEARCH, intent.action)
        assertEquals("com.amazon.music.tv", intent.`package`)
        assertEquals("Punjabi Hits", intent.getStringExtra(SearchManager.QUERY))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(launcher.urls.isEmpty())
        assertTrue(launcher.apps.isEmpty())
    }

    @Test fun missingSearchActivityFallsBackToOpenAppAndPreservesItsResult() {
        val launcher = RecordingLauncher(LaunchResult.NotInstalled("Punjabi Hits"))
        val context = RecordingContext(ActivityNotFoundException())
        assertEquals(LaunchResult.NotInstalled("Punjabi Hits"), MusicCatalog(playlists, launcher = launcher)
            .open(context, punjabi.copy(links = emptyMap()), "missing.app"))
        assertEquals(listOf("missing.app|Punjabi Hits"), launcher.apps)
        assertEquals(Intent.ACTION_SEARCH, context.intents.single().action)
    }

    @Test fun inaccessibleSearchActivityAlsoFallsBackToOpenApp() {
        val launcher = RecordingLauncher()
        assertEquals(LaunchResult.Ok, MusicCatalog(playlists, launcher = launcher)
            .open(RecordingContext(SecurityException()), punjabi.copy(links = emptyMap()), "locked.app"))
        assertEquals(listOf("locked.app|Punjabi Hits"), launcher.apps)
    }

    private class RecordingContext(private val failure: RuntimeException? = null) :
        ContextWrapper(RuntimeEnvironment.getApplication()) {
        val intents = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            intents += intent
            failure?.let { throw it }
        }
    }

    private class RecordingLauncher(private val result: LaunchResult = LaunchResult.Ok) : MusicLauncher {
        val urls = mutableListOf<String>()
        val apps = mutableListOf<String>()
        override fun openUrlIn(ctx: Context, pkg: String, url: String, label: String): LaunchResult {
            urls += "$pkg|$url|$label"
            return result
        }
        override fun openApp(ctx: Context, pkg: String, label: String): LaunchResult {
            apps += "$pkg|$label"
            return result
        }
    }
}
