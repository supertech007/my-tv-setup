package com.mytvsetup.launcher.content

/**
 * Turns a title-page link into one that starts playback, where the app supports it (checked on the TV):
 * JioHotstar plays from `/watch`, Prime from `/watch?gti=`; SonyLIV links already play, Apple TV opens on Play.
 */
fun playUrl(service: String, url: String): String = when (service) {
    "jhs" -> url.substringBefore('?').trimEnd('/').let { if (it.endsWith("/watch")) it else "$it/watch" }
    "prv" -> url.replaceFirst("app.primevideo.com/detail?", "app.primevideo.com/watch?")
    else -> url
}

/**
 * Only Prime drops a link when it is already running, so only Prime is restarted. A restart makes
 * JioHotstar ask "Who's watching?", which it otherwise skips (checked on the TV 1 Nov).
 */
fun needsFreshStart(service: String): Boolean = service == "prv"
