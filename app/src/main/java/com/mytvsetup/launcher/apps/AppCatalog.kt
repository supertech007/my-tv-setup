package com.mytvsetup.launcher.apps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Locale
import kotlin.math.roundToInt

data class AppEntry(val pkg: String, val label: String, val banner: ImageBitmap?, val icon: ImageBitmap?)

class AppCatalog(ctx: Context) {
    private val context = ctx.applicationContext

    // Query and rasterize off the UI thread; retain the returned list in the caller.
    @Suppress("DEPRECATION")
    fun installed(): List<AppEntry> {
        val pm = context.packageManager
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { category ->
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != "com.mytvsetup.launcher" }
            .map { resolved ->
                val app = resolved.activityInfo.applicationInfo
                AppEntry(
                    pkg = app.packageName,
                    label = app.loadLabel(pm).toString(),
                    banner = imageOrNull { app.loadBanner(pm)?.toImageBitmap(480, 270) },
                    icon = imageOrNull { app.loadIcon(pm).toImageBitmap(96, 96) }
                )
            }
            .sortedWith(appOrder)
    }
}

private val appOrder = compareBy<AppEntry> { it.label.lowercase(Locale.ROOT) }.thenBy { it.pkg }

fun dockApps(installed: List<AppEntry>, order: List<String>, hidden: Set<String>): List<AppEntry> {
    val visible = installed.filterNot { it.pkg in hidden }.distinctBy { it.pkg }
    val byPackage = visible.associateBy { it.pkg }
    val preferred = order.distinct().mapNotNull { byPackage[it] }
    val preferredPackages = order.toSet()
    return preferred + visible.filterNot { it.pkg in preferredPackages }.sortedWith(appOrder)
}

private inline fun imageOrNull(load: () -> ImageBitmap?): ImageBitmap? = try {
    load()
} catch (_: Exception) {
    null
}

private fun Drawable.toImageBitmap(maxWidth: Int, maxHeight: Int): ImageBitmap {
    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: maxWidth
    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: maxHeight
    val scale = minOf(1f, maxWidth.toFloat() / sourceWidth, maxHeight.toFloat() / sourceHeight)
    val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val originalBounds = Rect(bounds)
    try {
        setBounds(0, 0, width, height)
        draw(Canvas(bitmap))
    } finally {
        bounds = originalBounds
    }
    return bitmap.asImageBitmap()
}

const val ALL_APPS = "@all-apps"
const val SCREEN_OFF = "@screen-off"
const val SETTINGS = "@settings"
const val MUTE = "@mute"
const val HISTORY = "@history"
/** Launcher shortcuts that sit in the dock and move like apps (they cannot be removed). */
val DOCK_SHORTCUTS = listOf(ALL_APPS, HISTORY, SCREEN_OFF, MUTE, SETTINGS)

/** Dock ids in display order: [appIds] (already ordered by [dockApps]) and the shortcuts, each where [order] puts it. */
fun dockIds(appIds: List<String>, order: List<String>): List<String> {
    val known = (appIds + DOCK_SHORTCUTS).toSet()
    val placed = order.distinct().filter { it in known }
    return placed + appIds.filterNot { it in placed } + DOCK_SHORTCUTS.filterNot { it in placed }
}
