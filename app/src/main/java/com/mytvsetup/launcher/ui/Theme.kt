package com.mytvsetup.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.mytvsetup.launcher.R

@OptIn(ExperimentalTextApi::class)
val Outfit = FontFamily(listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium,
    FontWeight.SemiBold, FontWeight.Bold).map { weight ->
    // Outfit's variable font defaults to wght=100; declaring weight alone doesn't set its axis.
    Font(R.font.outfit_variable, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
})

data class Wallpaper(val id: String, val image: Int, val blurred: Int)
val WALLPAPERS = listOf(
    Wallpaper("rain", R.drawable.wall_rain, R.drawable.wall_rain_blur),
    Wallpaper("forest", R.drawable.wall_forest, R.drawable.wall_forest_blur),
    Wallpaper("mountains", R.drawable.wall_mountains, R.drawable.wall_mountains_blur),
    Wallpaper("ocean", R.drawable.wall_ocean, R.drawable.wall_ocean_blur)
)
// Saturated enough to tell apart on a TV: the accent colours focus rings, selected chips and switches.
val ACCENTS = mapOf("ice" to Color(0xFF8FD3FF), "mint" to Color(0xFF6EE7B7), "rose" to Color(0xFFFF8FB1), "gold" to Color(0xFFFFC94D))

@Composable
fun TvTheme(accent: String = "ice", content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = darkColorScheme(primary = ACCENTS[accent] ?: ACCENTS.getValue("ice"), background = Color(0xFF0D1522)),
        typography = Typography(
            bodyLarge = base.bodyLarge.copy(fontFamily = Outfit),
            bodyMedium = base.bodyMedium.copy(fontFamily = Outfit),
            labelLarge = base.labelLarge.copy(fontFamily = Outfit)
        ), content = content
    )
}
