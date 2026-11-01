package com.mytvsetup.launcher.ui.home

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mytvsetup.launcher.BuildConfig
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

object HomeNetwork {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            if (BuildConfig.DEBUG && BuildConfig.OFFLINE) throw IOException("Debug offline mode")
            val allowed = setOf("apis.justwatch.com", "images.justwatch.com", "api.themoviedb.org",
                "www.googleapis.com", "oauth2.googleapis.com", "api.open-meteo.com", "geocoding-api.open-meteo.com",
                "i.ytimg.com", "c.saavncdn.com")
            if (chain.request().url.host !in allowed) throw IOException("Host is not allowed")
            chain.proceed(chain.request())
        }.followRedirects(false).build()

    fun imageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(http)
        .memoryCache { MemoryCache.Builder(context).maxSizeBytes(20 * 1024 * 1024).build() }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("posters")).maxSizeBytes(32L * 1024 * 1024).build() }
        .crossfade(false).build()
}
