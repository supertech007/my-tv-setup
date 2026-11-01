package com.mytvsetup.launcher.content

import java.util.Locale
import kotlinx.serialization.json.Json

data class GenreGroup(val name: String, val tags: Set<String>)

/** Tags worth a second step in the genre picker: everything except the group's own name. */
fun GenreGroup.finerTags(): List<String> = tags.filter { it != GenreCatalog.canonical(name) }

/** A gathered catalogue; synonym-map iteration order determines the first matching group. */
class GenreCatalog(synonyms: Map<String, List<String>>) {
    private val groupsByTag: Map<String, Set<String>> = buildMap {
        synonyms.forEach { (group, labels) ->
            (listOf(group) + labels).forEach { label ->
                val tag = normalise(label)
                if (tag.isNotEmpty()) {
                    put(tag, getOrDefault(tag, emptySet()) + group)
                }
            }
        }
        // JustWatch combines these genres in one code; keep both filter memberships.
        val combined = getOrDefault("mystery thriller", emptySet()) +
            getOrDefault("mystery", emptySet()) + getOrDefault("thriller", emptySet())
        if (combined.isNotEmpty()) {
            put("mystery thriller", synonyms.keys.filterTo(linkedSetOf()) { it in combined })
        }
    }

    /** Canonical labels use spaces for separators. The ignored `eur` code becomes empty. */
    fun normalise(label: String): String {
        val lower = label.trim().lowercase(Locale.ROOT)
        return canonical(justWatchLabels[lower] ?: lower)
    }

    /** Compatibility helper. Use [groupsOf] for filtering so multi-group labels survive. */
    fun groupOf(label: String): String = groupsFor(normalise(label)).firstOrNull().orEmpty()

    /** Each repeated input label counts once toward every group that it matches. */
    private val knownGroups: Set<String> = groupsByTag.values.flatten().toSet()

    /**
     * Unknown labels (mostly TMDB keywords) become a group only once [minUnknownCount] titles share them;
     * groups of [curated] labels (hand-picked playlist tags) always survive.
     */
    fun build(allLabels: Collection<String>, minUnknownCount: Int = 1, curated: Collection<String> = emptyList()): List<GenreGroup> {
        val keep = knownGroups + groupsOf(curated)
        val tagsByGroup = linkedMapOf<String, MutableSet<String>>()
        val counts = mutableMapOf<String, Int>()
        allLabels.forEach { label ->
            val tag = normalise(label)
            groupsFor(tag).forEach { group ->
                tagsByGroup.getOrPut(group) { linkedSetOf() }.add(tag)
                counts[group] = counts.getOrDefault(group, 0) + 1
            }
        }
        // Stable sort keeps first-seen order when counts tie.
        return tagsByGroup.filterKeys { it in keep || counts.getValue(it) >= minUnknownCount }
            .map { (name, tags) -> GenreGroup(name, tags.toSet()) }
            .sortedByDescending { counts.getValue(it.name) }
    }

    fun groupsOf(labels: Collection<String>): Set<String> =
        labels.flatMapTo(linkedSetOf()) { groupsFor(normalise(it)) }

    private fun groupsFor(tag: String): Set<String> = when {
        tag.isEmpty() -> emptySet()
        else -> groupsByTag[tag] ?: setOf(tag.split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.ROOT) }
        })
    }

    companion object {
        /** Parse the asset contents without depending on an Android Context. */
        fun fromJson(json: String): GenreCatalog =
            GenreCatalog(Json.decodeFromString<Map<String, List<String>>>(json))

        internal fun canonical(label: String): String =
            label.lowercase(Locale.ROOT).replace(separators, " ").replace(whitespace, " ").trim()

        private val separators = Regex("&|\\band\\b|-")
        private val whitespace = Regex("\\s+")
        private val justWatchLabels = mapOf(
            "act" to "Action & Adventure", "ani" to "Animation", "cmy" to "Comedy",
            "crm" to "Crime", "doc" to "Documentary", "drm" to "Drama", "eur" to "",
            "fml" to "Kids & Family", "fnt" to "Fantasy", "hrr" to "Horror",
            "hst" to "History", "msc" to "Music & Musical", "rly" to "Reality TV",
            "rma" to "Romance", "scf" to "Science-Fiction", "spt" to "Sport",
            "trl" to "Mystery & Thriller", "war" to "War & Military", "wsn" to "Western"
        ).mapValues { (_, label) -> label.lowercase(Locale.ROOT) }
    }
}
