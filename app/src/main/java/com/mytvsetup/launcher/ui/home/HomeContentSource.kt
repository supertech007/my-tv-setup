package com.mytvsetup.launcher.ui.home

import com.mytvsetup.launcher.content.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface HomeContentSource {
    val forYou: Flow<List<Title>>
    val swipeQueue: Flow<List<Title>>
    val genreGroups: Flow<List<GenreGroup>>
    val personalise: StateFlow<Boolean>
    val filters: StateFlow<Filters>
    fun setFilters(f: Filters)
    fun resetTaste()
    fun setPersonalise(on: Boolean)
    suspend fun decide(t: Title, s: Signal)
    /** Short genre for the poster badge; null when unknown. */
    fun genreOf(t: Title): String? = null
}

/** Safe release shell until the controller wires the real ContentRepository. */
class EmptyHomeContentSource : HomeContentSource {
    override val forYou = MutableStateFlow<List<Title>>(emptyList())
    override val swipeQueue = MutableStateFlow<List<Title>>(emptyList())
    override val genreGroups = MutableStateFlow<List<GenreGroup>>(emptyList())
    override val personalise = MutableStateFlow(true)
    override val filters = MutableStateFlow(Filters())
    override fun resetTaste() = Unit
    override fun setFilters(f: Filters) { filters.value = f }
    override fun setPersonalise(on: Boolean) { personalise.value = on }
    override suspend fun decide(t: Title, s: Signal) = Unit
}
