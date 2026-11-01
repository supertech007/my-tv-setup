package com.mytvsetup.launcher.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisTest {
    private fun named(name: String) = ASSISTANTS.first { it.name == name }

    @Test fun assistantsAreClaudeChatGptGemini() {
        assertEquals(listOf("Claude", "ChatGPT", "Gemini"), ASSISTANTS.map { it.name })
    }

    @Test fun installedAppAlwaysWins() {
        assertEquals(JarvisRoute.App("com.anthropic.claude"), jarvisRoute(named("Claude"), setOf("com.anthropic.claude")))
        assertEquals(JarvisRoute.App("com.google.android.apps.bard"), jarvisRoute(named("Gemini"), setOf("com.google.android.apps.bard")))
    }

    @Test fun claudeAndChatGptFallBackToTheirWebsites() {
        // Neither has an Android TV app; you sign in once with your own subscription.
        assertEquals(JarvisRoute.Web("https://claude.ai/new"), jarvisRoute(named("Claude"), emptySet()))
        assertEquals(JarvisRoute.Web("https://chatgpt.com/"), jarvisRoute(named("ChatGPT"), emptySet()))
    }

    @Test fun geminiFallsBackToTheTvGoogleAssistant() {
        // Google blocks its sign-in inside embedded web views, so the TV's own assistant answers instead.
        assertEquals(JarvisRoute.GoogleAssistant, jarvisRoute(named("Gemini"), emptySet()))
    }

    @Test fun statusMarksOnlyInstalledApps() {
        assertEquals(listOf(true, false, false), assistantStatus(setOf("com.anthropic.claude")).values.toList())
    }
}
