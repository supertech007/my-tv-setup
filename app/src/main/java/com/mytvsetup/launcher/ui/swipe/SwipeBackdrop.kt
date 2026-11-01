package com.mytvsetup.launcher.ui.swipe

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import coil.transform.Transformation

@Composable
internal fun SwipeBackdrop(poster: String?, images: ImageLoader) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf<ImageBitmap?>(null) }
    val saturation = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.3f) }) }
    LaunchedEffect(poster, images) {
        if (poster == null) loaded = null
        else {
            val request = ImageRequest.Builder(context).data(poster).size(48, 72)
                .allowHardware(false).transformations(PosterBlur).build()
            // Keep the previous poster visible until the next transformed image is ready.
            // Coil caches each poster + transformation in the shared bounded memory cache.
            val result = images.execute(request)
            if (result is SuccessResult) loaded = (result.drawable as BitmapDrawable).bitmap.asImageBitmap()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF070B12))) {
        Crossfade(loaded, Modifier.fillMaxSize(), tween(500, easing = LinearEasing), label = "poster backdrop") { bitmap ->
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                colorFilter = saturation)
        }
        Box(Modifier.fillMaxSize().background(Color(0xFF070B12).copy(alpha = .45f)))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            Color(0xFF070B12).copy(alpha = .2f), Color(0xFF070B12).copy(alpha = .6f)))))
    }
}

/** Three radius-1 box passes on 48×72 approximate a 50px blur when cropped across 1920px. */
internal object PosterBlur : Transformation {
    override val cacheKey = "swipe-poster-blur-48x72-r1-v2"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = 48; val height = 72
        val small = Bitmap.createScaledBitmap(input, width, height, true)
        var pixels = IntArray(width * height)
        var output = IntArray(pixels.size)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== input) small.recycle()
        // Three box passes approximate a Gaussian; clamp edges to avoid dark fringes.
        repeat(3) {
            for (horizontal in listOf(true, false)) {
                val length = if (horizontal) width else height
                val lines = if (horizontal) height else width
                for (line in 0 until lines) {
                    fun index(pos: Int) = if (horizontal) line * width + pos else pos * width + line
                    var red = 0; var green = 0; var blue = 0
                    fun add(pos: Int, sign: Int) {
                        val p = pixels[index(pos.coerceIn(0, length - 1))]
                        red += ((p shr 16) and 255) * sign
                        green += ((p shr 8) and 255) * sign
                        blue += (p and 255) * sign
                    }
                    for (p in -1..1) add(p, 1)
                    for (p in 0 until length) {
                        output[index(p)] = (255 shl 24) or ((red / 3) shl 16) or ((green / 3) shl 8) or (blue / 3)
                        add(p - 1, -1); add(p + 2, 1)
                    }
                }
                val swap = pixels; pixels = output; output = swap
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
