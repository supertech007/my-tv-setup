package com.mytvsetup.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.asImageBitmap

/** Offline artwork exercises the banner path; these are test drawings, not vendor assets. */
internal fun fixtureBanner(label: String, index: Int) = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888).also { bitmap ->
    val colors = listOf(0xFF164B72, 0xFF493CA1, 0xFF12655C, 0xFF63243D, 0xFF595D21, 0xFF2C3A53)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 480f, 270f, colors[index % colors.size].toInt(), 0xFF111D30.toInt(), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 480f, 270f, paint)
    paint.shader = null
    paint.color = 0x1AFFFFFF
    canvas.drawCircle(420f, 60f, 155f, paint)
    canvas.drawCircle(30f, 260f, 110f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 46f
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(label, 240f, 145f, paint)
}.asImageBitmap()
