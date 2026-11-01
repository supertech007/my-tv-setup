package com.mytvsetup.launcher.ui

fun greeting(hour: Int, name: String): String = when {
    hour < 12 -> "Good morning"
    hour < 17 -> "Good afternoon"
    else -> "Good evening"
} + if (name.isBlank()) "" else ", ${name.trim()}"
