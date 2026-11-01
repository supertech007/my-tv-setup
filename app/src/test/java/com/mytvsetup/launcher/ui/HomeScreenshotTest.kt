package com.mytvsetup.launcher.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import com.mytvsetup.launcher.ui.swipe.*
import com.mytvsetup.launcher.apps.LaunchResult
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.TextLayoutResult
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.github.takahirom.roborazzi.captureRoboImage
import com.mytvsetup.launcher.apps.AppEntry
import com.mytvsetup.launcher.content.GenreCatalog
import com.mytvsetup.launcher.settings.Prefs
import com.mytvsetup.launcher.ui.home.*
import com.mytvsetup.launcher.ui.settings.*
import com.mytvsetup.launcher.ui.allapps.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Native JVM render of the production shell at the Mi TV Stick's physical resolution. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "en-rIN-w960dp-h540dp-land-television-night-xhdpi-notouch-dpad")
class HomeScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var images: ImageLoader
    private val nav = Nav()
    private lateinit var loaded: MutableSet<String>
    private lateinit var loadedBackdrops: MutableSet<String>
    private lateinit var firstPoster: String
    private var currentPrefs by mutableStateOf(Prefs())
    private var catalog by mutableStateOf<List<AppEntry>>(emptyList())
    private var resets = 0
    private val opened = mutableListOf<String>()

    @Before
    fun showHome() {
        val context = compose.activity
        val metrics = context.resources.displayMetrics
        assertEquals(320, metrics.densityDpi)
        assertEquals(Configuration.UI_MODE_TYPE_TELEVISION,
            context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK)
        catalog = dockFixture
        val prefs = currentPrefs
        val source = FakeHomeContentSource(GenreCatalog.fromJson(
            context.assets.open("genre_synonyms.json").bufferedReader().use { it.readText() }))
        val confirmed = debugTitles.filter { t -> t.offers.any { o -> o.audio.any { it == "hi" || it == "en" } } }
        val posters = confirmed.associate { title ->
            val resource = requireNotNull(javaClass.getResource("/posters/${title.id}.webp")) {
                "Missing cached poster for ${title.title}"
            }
            val bitmap = resource.openStream().use { BitmapFactory.decodeStream(it) }
            assertNotNull("Poster decode failed for ${title.title}", bitmap)
            title.posterUrl to BitmapDrawable(context.resources, bitmap!!)
        }
        loaded = ConcurrentHashMap.newKeySet<String>()
        loadedBackdrops = ConcurrentHashMap.newKeySet<String>()
        firstPoster = confirmed.sortedByDescending { it.imdb ?: -1.0 }.first().posterUrl!!
        // Every URL is resolved locally; accidental network fallback fails the test.
        images = ImageLoader.Builder(context)
            .crossfade(false)
            .allowHardware(false)
            .components { add(Interceptor { chain ->
                val url = chain.request.data as? String
                    ?: error("Expected a debug poster URL: ${chain.request.data}")
                val poster = posters.getValue(url)
                chain.proceed(chain.request.newBuilder().data(poster).build()).also {
                    if (it is SuccessResult) {
                        loaded += url
                        if (PosterBlur in chain.request.transformations) loadedBackdrops += url
                    }
                }
            }) }.build()
        @Suppress("DEPRECATION")
        compose.runOnUiThread {
            context.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
            val livePrefs = currentPrefs
            TvTheme(livePrefs.accent) {
                WallpaperStage(livePrefs.wallpaper) {
                    val stack by nav.stack.collectAsState()
                    BackHandler { nav.back() }
                    if (stack.last() == Screen.Settings) {
                        SettingsScreen(livePrefs, catalog, { update -> currentPrefs = update(currentPrefs) },
                            { resets++ }, { nav.push(Screen.Accounts) }, { nav.back() },
                            citySearch = { listOf(City("Mumbai", "Mumbai", "India", 19.08, 72.88)) })
                    } else if (stack.last() == Screen.AllApps) {
                        AllAppsScreen(remember(catalog) { catalog.mapIndexed { i, app ->
                            if (i == catalog.lastIndex) app else app.copy(banner = fixtureBanner(app.label, i))
                        } }, livePrefs, { currentPrefs = it(currentPrefs) },
                            { opened += it.pkg }, { nav.back() })
                    } else if (stack.last() == Screen.Accounts) {
                        AccountsScreen({ nav.back() })
                    } else if (stack.last() == Screen.Swipe) {
                        val scope = rememberCoroutineScope()
                        val vm = remember { SwipeViewModel(source, SwipeLauncher { _, _, _ -> LaunchResult.Ok },
                            source.tasteSummary, scope) }
                        SwipeScreen(vm, images) { nav.back() }
                    } else HomeScreen(livePrefs, source, dockFixture, images, nav, {}, { _, _ -> },
                        onPrefs = { currentPrefs = it(currentPrefs) })
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("Game of Thrones, 2011, IMDb 9.2"))
                .fetchSemanticsNodes().size == 1
        }
        val visiblePosters = confirmed.sortedByDescending { it.imdb ?: -1.0 }.take(4).map { it.posterUrl }
        try {
            compose.waitUntil(10_000) { loaded.containsAll(visiblePosters) }
        } catch (e: AssertionError) {
            error("Visible posters did not load: expected=$visiblePosters; loaded=$loaded; cause=$e")
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val bounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(1920f, bounds.width, 0f)
        assertEquals(1080f, bounds.height, 0f)
    }

    @After fun closeImages() { if (::images.isInitialized) images.shutdown() }

    @Test fun firstForYouPoster() {
        // Entering the panel from outside always lands on Open.
        compose.onNodeWithContentDescription("Game of Thrones, 2011, IMDb 9.2")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Open ⤢").assertIsFocused()
        // Inside the panel, posters take focus normally.
        compose.onNodeWithContentDescription("Game of Thrones, 2011, IMDb 9.2")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Game of Thrones, 2011, IMDb 9.2")
            .assertIsFocused()
        record("home-render.png")
        val poster = compose.onNodeWithContentDescription("Game of Thrones, 2011, IMDb 9.2")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Focused poster is clipped: height=${poster.height}", poster.height >= 225 * TILE_LIFT - 2f) // 2 px for rounding
    }

    @Test fun secondDockTile() {
        compose.onNodeWithText("JioHotstar").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("JioHotstar").assertIsFocused()
        record("home-render-dock.png")
    }

    @Test fun releasingTheHoldDoesNotRemoveTheDockApp() {
        remoteHold("JioHotstar")
        // The edit buttons appear under the still-held OK; letting go must not press ✕.
        compose.onNodeWithContentDescription("Remove JioHotstar from the dock").assertIsFocused()
        assertFalse("in.startv.hotstar" in currentPrefs.hiddenApps)
        compose.onNodeWithContentDescription("Remove JioHotstar from the dock")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertTrue("in.startv.hotstar" in currentPrefs.hiddenApps)
    }

    @Test fun weatherSetupPromptFitsInsideClock() {
        assertTextFits("Set your city in Settings")
        compose.onNodeWithText("—°", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun musicActionFitsInsideCard() {
        assertTextFits("Open music  ↗")
    }

    @Test fun swipeOpensFromHomeAndBackReturnsHome() {
        openSwipe()
        compose.onNodeWithText("Game of Thrones").assertIsDisplayed()
        compose.onNodeWithText("1 of 10").assertIsDisplayed()
        compose.onNodeWithTag("swipe-input").assertIsFocused()
        assertTextFits("Not for me")
        assertTextFits("Play now")
        compose.onNodeWithText("2011 · Audio: Hindi · English").assertIsDisplayed()
        assertTextFits("Add to my list")
        record("swipe-render.png")
        compose.onNodeWithTag("swipe-input").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("Rate &\ndiscover").assertIsDisplayed()
    }

    @Test fun swipeRemoteRatesAfterAnimationAndUpFocusesFilters() {
        openSwipe()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("swipe-input").performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("YES").assertIsDisplayed()
        compose.onNodeWithText("Game of Thrones").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("2 of 10").assertIsDisplayed()
        compose.onNodeWithText("Your taste: Action & Adventure ▲  Drama ▲  Fantasy ▲  Sci-Fi ▲").assertIsDisplayed()
        compose.onNodeWithTag("swipe-input").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithText("3 of 10").assertIsDisplayed()
        compose.onNodeWithTag("swipe-input").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Movies").assertIsFocused()
        compose.onNodeWithText("Movies").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("TV shows").assertIsFocused()
        compose.onNodeWithText("TV shows").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("swipe-input").assertIsFocused()
    }

    @Test fun filtersCanBeReenteredAfterScrollingToLastGenre() {
        openSwipe()
        compose.onNodeWithTag("swipe-input").performKeyInput { pressKey(Key.DirectionUp) }
        repeat(18) { compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) } }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("swipe-input").assertIsFocused()
        compose.onNodeWithTag("swipe-input").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Movies").assertIsFocused()
    }

    @Test fun settingsRendersAndRemoteOpensAccounts() {
        compose.runOnIdle { nav.push(Screen.Settings) }
        compose.onNodeWithText("Profile").assertIsFocused()
        assertTextFits("Make yourself at home.")
        compose.mainClock.advanceTimeBy(500)
        record("settings-render.png")
        selectSection("Accounts")
        remoteClick("YouTube accounts")
        compose.onNodeWithText("YouTube accounts").assertIsDisplayed()
        remoteClick("Back")
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test fun settingsChangesPersistAndResetRequiresConfirmation() {
        compose.runOnIdle { nav.push(Screen.Settings) }
        selectSection("Look")
        remoteClick("Forest")
        remoteClick("Gold")
        compose.runOnIdle { assertEquals("forest", currentPrefs.wallpaper); assertEquals("gold", currentPrefs.accent) }
        selectSection("Home cards")
        remoteClick("Music · Shown")
        compose.runOnIdle { assertTrue("music" in currentPrefs.hiddenCards) }
        selectSection("For you")
        remoteClick("Reset learned taste")
        compose.runOnIdle { assertEquals(0, resets) }
        remoteClick("Cancel")
        compose.runOnIdle { assertEquals(0, resets) }
        remoteClick("Reset learned taste")
        remoteClick("Reset taste")
        compose.runOnIdle { assertEquals(1, resets) }
    }

    @Test fun settingsCitySelectionStoresCoordinatesAndName() {
        compose.runOnIdle { nav.push(Screen.Settings) }
        compose.onNodeWithTag("profile-name").performTextReplacement("Test")
        remoteClick("Save name")
        compose.onNodeWithTag("city-query").performTextReplacement("Mumbai")
        remoteClick("Search cities")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Mumbai, Mumbai, India").fetchSemanticsNodes().isNotEmpty() }
        remoteClick("Mumbai, Mumbai, India")
        compose.runOnIdle {
            assertEquals("Test", currentPrefs.name)
            assertEquals("Mumbai", currentPrefs.city)
            assertEquals(19.08, currentPrefs.lat!!, .001)
            assertEquals(72.88, currentPrefs.lon!!, .001)
        }
    }

    @Test fun allAppsIncludesHiddenAppsAndHoldAddsToDockWithoutLaunching() {
        compose.runOnIdle {
            currentPrefs = Prefs(hiddenApps = setOf("org.smarttube.stable"), dockOrder = listOf("com.sonyliv"))
            nav.push(Screen.AllApps)
        }
        compose.onNodeWithText("YouTube").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(500)
        record("allapps-render.png")
        remoteClick("prime video")
        compose.runOnIdle { assertEquals(listOf("com.amazon.amazonvideo.livingroom"), opened) }
        remoteHold("YouTube")
        remoteClick("Add to dock")
        compose.runOnIdle {
            assertFalse("org.smarttube.stable" in currentPrefs.hiddenApps)
            assertEquals(listOf("com.sonyliv", "org.smarttube.stable"), currentPrefs.dockOrder)
            assertEquals(1, opened.size)
        }
    }

    @Test fun dockCanBeHeldMovedAndHiddenWithRemote() {
        compose.runOnIdle { nav.push(Screen.Settings) }
        selectSection("Dock")
        remoteHold("JioHotstar")
        compose.onNodeWithText("JioHotstar").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals("in.startv.hotstar", currentPrefs.dockOrder.first()) }
        compose.onNodeWithText("JioHotstar").performKeyInput { pressKey(Key.DirectionCenter) }
        remoteClick("Hide JioHotstar")
        compose.runOnIdle { assertTrue("in.startv.hotstar" in currentPrefs.hiddenApps) }
        remoteClick("Show JioHotstar")
        compose.runOnIdle { assertFalse("in.startv.hotstar" in currentPrefs.hiddenApps) }
    }

    @Test fun settingsSidebarAndWallpaperPickerUseDirectionalFocus() {
        compose.runOnIdle { nav.push(Screen.Settings) }
        compose.onNodeWithText("Profile").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Look").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Look").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Rain").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Forest").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("forest", currentPrefs.wallpaper) }
    }

    @Test fun languageAndInstalledMusicChoicesUpdatePreferences() {
        compose.runOnIdle {
            catalog = listOf(AppEntry("com.jio.media.jiobeats", "JioSaavn", null, null),
                AppEntry("com.spotify.tv.android", "Spotify", null, null),
                AppEntry("unrelated", "Unrelated app", null, null))
            nav.push(Screen.Settings)
        }
        selectSection("Languages")
        remoteClick("Video: Hindi · On")
        remoteClick("Music: Haryanvi · On")
        compose.runOnIdle {
            assertEquals(setOf("en"), currentPrefs.videoLangs)
            assertEquals(setOf("pa", "hi", "en"), currentPrefs.musicLangs)
        }
        selectSection("Music app")
        compose.onNodeWithText("Unrelated app").assertDoesNotExist()
        compose.onNodeWithText("Apple Music").assertDoesNotExist()
        remoteClick("Spotify")
        compose.runOnIdle { assertEquals("com.spotify.tv.android", currentPrefs.musicApp) }
    }

    @Test fun switchBackIsInstructionalAndReturnsToSettings() {
        compose.runOnIdle { nav.push(Screen.Settings) }
        selectSection("About")
        remoteClick("How to switch back to Projectivy")
        assertTextFits("TV Settings → Apps → Default apps → Home app → Projectivy Launcher")
        compose.onNodeWithText("exact menu names may vary slightly").assertIsDisplayed()
        compose.onNodeWithText("Back to Settings").assertIsFocused()
        remoteClick("Back to Settings")
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    private fun selectSection(label: String) { remoteClick(label) }

    private fun remoteClick(label: String) {
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(label).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    private fun remoteHold(label: String) {
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText(label).performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(800)
        compose.onAllNodes(isRoot())[0].performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    private fun openSwipe() {
        compose.onNodeWithText("Rate &\ndiscover")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Rate &\ndiscover").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(10_000) {
            compose.onNodeWithTag("swipe-input").assertExists()
            firstPoster in loadedBackdrops
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
    }

    @Test fun outfitBoldHasMoreVisibleInkThanLight() {
        val resolver = createFontFamilyResolver(compose.activity)
        fun ink(weight: FontWeight): Int {
            val face = resolver.resolve(Outfit, weight).value as Typeface
            val bitmap = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face; textSize = 72f; color = android.graphics.Color.WHITE
            }
            Canvas(bitmap).drawText("My TV", 0f, 75f, paint)
            val pixels = IntArray(400 * 100)
            bitmap.getPixels(pixels, 0, 400, 0, 0, 400, 100)
            bitmap.recycle()
            return pixels.count { android.graphics.Color.alpha(it) > 128 }
        }
        val light = ink(FontWeight.Light)
        val bold = ink(FontWeight.Bold)
        assertTrue("Font weights look identical: light=$light, bold=$bold", bold > light * 1.2)
    }

    private fun record(name: String) {
        val file = File("../shots/$name")
        file.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }

    private fun assertTextFits(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse("Text is vertically clipped: $text", layout.didOverflowHeight)
        // A paragraph may retain the parent's width while Text shrinks to its longest line.
        // Check the actual line extents rather than treating that empty width as clipped ink.
        repeat(layout.lineCount) { line ->
            assertFalse("Text is ellipsized: $text", layout.isLineEllipsized(line))
            assertTrue("Text is horizontally clipped: $text", layout.getLineLeft(line) >= 0f &&
                layout.getLineRight(line) <= layout.size.width + .5f)
        }
        assertTrue("Text is clipped by its parent: $text",
            node.fetchSemanticsNode().boundsInRoot.height >= layout.size.height - .5f)
    }

    private val dockFixture = listOf(
        "com.amazon.amazonvideo.livingroom" to "prime video",
        "in.startv.hotstar" to "JioHotstar",
        "com.sonyliv" to "SonyLIV",
        "com.graymatrix.did" to "ZEE5",
        "tv.airtel.xstream.tvapp" to "Xstream",
        "org.smarttube.stable" to "YouTube",
        "com.apple.atve.androidtv.appletv" to "Apple TV"
    ).map { (pkg, label) -> AppEntry(pkg, label, null, null) }
}
