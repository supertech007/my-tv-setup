package com.mytvsetup.launcher

import android.app.Application
import androidx.work.Configuration
import com.mytvsetup.launcher.content.RefreshWorker

class TvApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedule(this)
    }
}
