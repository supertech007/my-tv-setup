package com.mytvsetup.launcher.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface Screen {
    data object Home : Screen
    data object Swipe : Screen
    data object Settings : Screen
    data object AllApps : Screen
    data object Browse : Screen
    data object ScreenOff : Screen
    data object History : Screen
    data class Assistant(val name: String) : Screen
    data object Accounts : Screen
    data class YouTube(val accountId: String?) : Screen
}

class Nav {
    private val history = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val stack: StateFlow<List<Screen>> = history.asStateFlow()
    fun push(s: Screen) { if (s == Screen.Home) home() else history.value += s }
    fun back(): Boolean {
        if (history.value.size == 1) return false
        history.value = history.value.dropLast(1)
        return true
    }
    fun home() { history.value = listOf(Screen.Home) }
}
