package com.mytvsetup.launcher.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Keep-days value meaning "never drop anything". */
const val FOREVER = 0
const val DEFAULT_KEEP_DAYS = 90
/** The choices offered in Settings → History. */
val KEEP_DAY_CHOICES = listOf(7, 30, 90, 365, FOREVER)

@Serializable
enum class HistoryKind { MOVIE, SHOW, MUSIC, YOUTUBE, APP }

/**
 * One thing opened from My TV. [ref] identifies it within its [kind] (title id, playlist id, video id or package);
 * [pkg] and [url] reopen it. Android does not let a launcher see what plays inside other apps, so this is what
 * was opened from here, not every minute watched.
 */
@Serializable
data class HistoryEntry(
    val kind: HistoryKind,
    val ref: String,
    val name: String,
    val detail: String,
    val poster: String?,
    val pkg: String,
    val url: String?,
    val at: Long = 0
)

/** Watch history kept in a small JSON file: newest first, one entry per item, older than the keep-days dropped. */
class WatchHistory(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = state
    /** Follows Settings → History; places that only record (YouTube, Swipe) use it. */
    @Volatile var keepDays: Int = DEFAULT_KEEP_DAYS

    @Synchronized fun record(entry: HistoryEntry, keepDays: Int = this.keepDays) {
        val stamped = entry.copy(at = clock())
        save(fresh(listOf(stamped) + state.value.filterNot { it.kind == entry.kind && it.ref == entry.ref }, keepDays))
    }

    @Synchronized fun prune(keepDays: Int) {
        val kept = fresh(state.value, keepDays)
        if (kept.size != state.value.size) save(kept)
    }

    @Synchronized fun clear() = save(emptyList())

    private fun fresh(list: List<HistoryEntry>, keepDays: Int): List<HistoryEntry> {
        if (keepDays == FOREVER) return list.take(MAX_ENTRIES)
        val cutoff = clock() - keepDays * DAY_MS
        return list.filter { it.at >= cutoff }.take(MAX_ENTRIES)
    }

    private fun load(): List<HistoryEntry> =
        runCatching { if (file.exists()) json.decodeFromString<List<HistoryEntry>>(file.readText()) else emptyList() }
            .getOrDefault(emptyList())

    private fun save(list: List<HistoryEntry>) {
        state.value = list
        runCatching {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(list))
            temp.renameTo(file) || run { file.writeText(temp.readText()); temp.delete() }
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_ENTRIES = 2_000
    }
}
