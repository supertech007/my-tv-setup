package com.mytvsetup.launcher.ai

/** [webUrl] is used when the app is missing; null means hand over to the TV's Google Assistant. */
data class Assistant(val name: String, val pkg: String, val webUrl: String?)
val ASSISTANTS = listOf(
    Assistant("Claude", "com.anthropic.claude", "https://claude.ai/new"),
    Assistant("ChatGPT", "com.openai.chatgpt", "https://chatgpt.com/"),
    Assistant("Gemini", "com.google.android.apps.bard", null)
)
fun assistantStatus(installed: Set<String>): Map<Assistant, Boolean> =
    ASSISTANTS.associateWith { it.pkg in installed }

sealed interface JarvisRoute {
    data class App(val pkg: String) : JarvisRoute
    data class Web(val url: String) : JarvisRoute
    data object GoogleAssistant : JarvisRoute
}

fun jarvisRoute(assistant: Assistant, installed: Set<String>): JarvisRoute = when {
    assistant.pkg in installed -> JarvisRoute.App(assistant.pkg)
    assistant.webUrl != null -> JarvisRoute.Web(assistant.webUrl)
    else -> JarvisRoute.GoogleAssistant
}
