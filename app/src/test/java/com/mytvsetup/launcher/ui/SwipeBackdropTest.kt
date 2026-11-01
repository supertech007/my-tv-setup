package com.mytvsetup.launcher.ui

import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Size
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.SuccessResult
import coil.request.ErrorResult
import com.mytvsetup.launcher.ui.swipe.SwipeBackdrop
import org.junit.Rule
import java.util.concurrent.ConcurrentHashMap
import com.mytvsetup.launcher.ui.swipe.PosterBlur
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "en-rIN-w960dp-h540dp-land-television-night-xhdpi-notouch-dpad")
class SwipeBackdropTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun backdropFillsScreenAndCrossfadesAfterTheNextPosterLoads() {
        val redPoster = "https://images.justwatch.com/red.webp"
        val bluePoster = "https://images.justwatch.com/blue.webp"
        val poster = mutableStateOf(redPoster)
        val loaded = ConcurrentHashMap.newKeySet<String>()
        val started = ConcurrentHashMap.newKeySet<String>()
        val errors = ConcurrentHashMap<String, Throwable>()
        val nextPosterReady = CompletableDeferred<Unit>()
        val images = ImageLoader.Builder(compose.activity).allowHardware(false)
            .components { add(Interceptor { chain ->
                val key = chain.request.data as String
                started += key
                if (key == bluePoster) nextPosterReady.await()
                val bitmap = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(if (key == redPoster) Color.RED else Color.BLUE)
                }
                chain.proceed(chain.request.newBuilder()
                    .data(BitmapDrawable(compose.activity.resources, bitmap)).build()).also {
                    if (it is SuccessResult) loaded += key
                    if (it is ErrorResult) errors[key] = it.throwable
                }
            }) }.build()
        try {
            compose.setContent { SwipeBackdrop(poster.value, images) }
            compose.waitForIdle()
            try {
                compose.waitUntil(10_000) {
                    compose.onRoot().assertExists()
                    redPoster in loaded || errors.isNotEmpty()
                }
            } catch (failure: Throwable) {
                throw AssertionError("Started=$started, loaded=$loaded, errors=$errors, lifecycle=${compose.activity.lifecycle.currentState}", failure)
            }
            assertTrue("Image loading failed: $errors", errors.isEmpty())
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            fun pixel(x: Int, y: Int) = compose.onRoot().captureToImage().toPixelMap()[x, y]
            val left = pixel(5, 5)
            val right = pixel(1850, 5)
            assertTrue("Backdrop must cover the screen", left.red > .3f && right.red > .15f)
            assertTrue("Right gradient protects title readability", left.red > right.red)
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { poster.value = bluePoster }
            compose.mainClock.advanceTimeByFrame()
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onRoot().assertExists()
                bluePoster in started
            }
            compose.mainClock.advanceTimeBy(600)
            val waiting = pixel(5, 5)
            assertTrue("Keep the current poster while its replacement loads: $waiting",
                waiting.red > .3f && waiting.blue < .05f)
            nextPosterReady.complete(Unit)
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onRoot().assertExists()
                bluePoster in loaded || errors.isNotEmpty()
            }
            assertTrue("Image loading failed: $errors", errors.isEmpty())
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(250)
            val middle = pixel(5, 5)
            assertTrue("Crossfade must retain the old poster during transition: $middle",
                middle.red > .05f && middle.blue > .05f)
            compose.mainClock.advanceTimeBy(350)
            val end = pixel(5, 5)
            assertTrue("Transition must finish on the current poster: $end", end.blue > .3f && end.red < .05f)
        } finally { images.shutdown() }
    }

    @Test fun blurUsesTinyBufferAndSoftensEdgesWithoutFlatteningPosterColors() = runBlocking {
        val input = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(480 * 720) { if (it % 480 < 240) Color.RED else Color.BLUE }
        input.setPixels(pixels, 0, 480, 0, 0, 480, 720)
        val result = PosterBlur.transform(input, Size(48, 72))
        assertEquals(48, result.width)
        assertEquals(72, result.height)
        assertEquals(Color.RED, result.getPixel(4, 36))
        assertEquals(Color.BLUE, result.getPixel(43, 36))
        val edge = result.getPixel(23, 36)
        assertTrue(Color.red(edge) in 1..254)
        assertTrue(Color.blue(edge) in 1..254)
        assertFalse("Coil owns the input bitmap", input.isRecycled)
    }
}
