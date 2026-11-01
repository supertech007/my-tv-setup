package com.mytvsetup.launcher.ui.swipe

import com.mytvsetup.launcher.ui.home.HomeContentSource

// The controller supplies the real Taste.top provider when ContentRepository is merged.
fun defaultTasteSummary(source: HomeContentSource): TasteSummary = TasteSummary { emptyList() }
