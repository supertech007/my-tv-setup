package com.mytvsetup.launcher.ui.swipe

import com.mytvsetup.launcher.ui.home.FakeHomeContentSource
import com.mytvsetup.launcher.ui.home.HomeContentSource

fun defaultTasteSummary(source: HomeContentSource): TasteSummary =
    (source as? FakeHomeContentSource)?.tasteSummary ?: TasteSummary { emptyList() }
