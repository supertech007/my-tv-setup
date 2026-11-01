package com.mytvsetup.launcher.content

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenresTest {
    private fun catalog(): GenreCatalog {
        val asset = listOf(
            File("src/main/assets/genre_synonyms.json"),
            File("app/src/main/assets/genre_synonyms.json")
        ).first { it.isFile }
        return GenreCatalog.fromJson(asset.readText())
    }

    @Test fun paranormalAndSupernaturalBelongToHorror() {
        assertEquals("Horror", catalog().groupOf("Paranormal"))
        assertEquals("Horror", catalog().groupOf("SUPERNATURAL "))
    }

    @Test fun thrillerCodeBelongsToBothGroups() {
        assertEquals(setOf("Mystery", "Thriller"), catalog().groupsOf(listOf("trl")))
        assertEquals("Mystery", catalog().groupOf("trl"))
    }

    @Test fun singleGroupFollowsSynonymFileOrder() {
        val catalog = GenreCatalog.fromJson("""{"Thriller":["thriller"],"Mystery":["mystery"]}""")
        assertEquals(setOf("Mystery", "Thriller"), catalog.groupsOf(listOf("trl")))
        assertEquals("Thriller", catalog.groupOf("trl"))
    }

    @Test fun unknownLabelSurvivesAsItsOwnTitleCasedGroup() {
        assertEquals("Slow Burn", catalog().groupOf("Slow Burn"))
        assertEquals(setOf("Slow Burn"), catalog().groupsOf(listOf(" SLOW burn ")))
    }

    @Test fun buildGathersObservedTagsAndOrdersByFrequency() {
        assertEquals(
            listOf(
                GenreGroup("Horror", setOf("horror", "paranormal")),
                GenreGroup("Comedy", setOf("comedy"))
            ),
            catalog().build(listOf("horror", "paranormal", "comedy"))
        )
    }

    @Test fun repeatsCountTowardFrequencyWithoutDuplicatingTags() {
        assertEquals(
            listOf(
                GenreGroup("Comedy", setOf("comedy")),
                GenreGroup("Horror", setOf("horror", "paranormal"))
            ),
            catalog().build(listOf("horror", "paranormal", "comedy", "comedy", "comedy"))
        )
    }

    @Test fun paranormalOnlyTitleMatchesHorrorFilter() {
        val titleLabels = listOf("Paranormal")
        assertTrue("Horror" in catalog().groupsOf(titleLabels))
    }

    @Test fun separatorsAndCaseNormaliseConsistently() {
        val catalog = catalog()
        assertEquals("action adventure", catalog.normalise(" ACTION & Adventure "))
        assertEquals("action adventure", catalog.normalise("action and adventure"))
        assertEquals("action adventure", catalog.normalise("action-adventure"))
        assertEquals("science fiction", catalog.normalise(" Science-Fiction "))
        assertEquals("sci fi", catalog.normalise("sci-fi"))
    }

    @Test fun normalizationDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("science fiction", catalog().normalise("SCIENCE-FICTION"))
            assertEquals("Sci-Fi", catalog().groupOf("SCIENCE-FICTION"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun allJustWatchCodesExpandToSpecifiedLabels() {
        val catalog = catalog()
        val expansions = mapOf(
            "act" to "action adventure", "ani" to "animation", "cmy" to "comedy",
            "crm" to "crime", "doc" to "documentary", "drm" to "drama",
            "fml" to "kids family", "fnt" to "fantasy", "hrr" to "horror",
            "hst" to "history", "msc" to "music musical", "rly" to "reality tv",
            "rma" to "romance", "scf" to "science fiction", "spt" to "sport",
            "trl" to "mystery thriller", "war" to "war military", "wsn" to "western"
        )
        expansions.forEach { (code, expanded) ->
            assertEquals(code, expanded, catalog.normalise(" ${code.uppercase(Locale.ROOT)} "))
        }
    }

    @Test fun allJustWatchCodesResolveToDisplayGroups() {
        val expected = mapOf(
            "act" to "Action & Adventure", "ani" to "Animation", "cmy" to "Comedy",
            "crm" to "Crime", "doc" to "Documentary", "drm" to "Drama",
            "fml" to "Kids & Family", "fnt" to "Fantasy", "hrr" to "Horror",
            "hst" to "History", "msc" to "Music & Musical", "rly" to "Reality TV",
            "rma" to "Romance", "scf" to "Sci-Fi", "spt" to "Sport",
            "war" to "War & Military", "wsn" to "Western"
        )
        val catalog = catalog()
        expected.forEach { (code, group) ->
            assertEquals(code, setOf(group), catalog.groupsOf(listOf(code)))
        }
    }

    @Test fun assetContainsEveryRequiredSynonym() {
        val seeds = mapOf(
            "Horror" to listOf("horror", "paranormal", "supernatural", "ghost", "haunted house", "possession", "slasher", "zombie"),
            "Mystery" to listOf("mystery", "whodunit", "detective", "investigation"),
            "Thriller" to listOf("thriller", "psychological thriller", "suspense"),
            "Sci-Fi" to listOf("science-fiction", "science fiction", "sci-fi", "dystopia", "time travel"),
            "Romance" to listOf("romance", "love", "romantic comedy"),
            "Crime" to listOf("crime", "gangster", "heist", "police")
        )
        val catalog = catalog()
        seeds.forEach { (group, labels) ->
            labels.forEach { label -> assertTrue(label, group in catalog.groupsOf(listOf(label))) }
        }
    }

    @Test fun constructorNormalisesCustomSynonymsAndGroupNames() {
        val catalog = GenreCatalog(linkedMapOf("Custom Group" to listOf(" SPECIAL-TAG ")))
        assertEquals("Custom Group", catalog.groupOf("special tag"))
        assertEquals("Custom Group", catalog.groupOf("CUSTOM GROUP"))
    }

    @Test fun sharedSynonymsMatchAllGroupsInMapOrder() {
        val catalog = GenreCatalog(linkedMapOf("First" to listOf("shared"), "Second" to listOf("SHARED")))
        assertEquals(setOf("First", "Second"), catalog.groupsOf(listOf("Shared")))
        assertEquals("First", catalog.groupOf("shared"))
    }

    @Test fun buildKeepsUnknownLabelsAndEveryGroupOfMultiGroupCodes() {
        assertEquals(
            listOf(
                GenreGroup("Mystery", setOf("mystery thriller")),
                GenreGroup("Thriller", setOf("mystery thriller")),
                GenreGroup("Slow Burn", setOf("slow burn"))
            ),
            catalog().build(listOf("trl", " SLOW BURN "))
        )
    }

    @Test fun rareKeywordsDoNotBecomeGenresButKnownGenresAlwaysDo() {
        // Real catalogue: ~2,400 TMDB keywords; each one becoming a chip froze the stick.
        val labels = listOf("horror") + List(3) { "slow burn" } + List(2000) { "keyword $it" }
        assertEquals(
            listOf(GenreGroup("Slow Burn", setOf("slow burn")), GenreGroup("Horror", setOf("horror"))),
            catalog().build(labels, minUnknownCount = 3)
        )
    }

    @Test fun finerTagsLeaveOutTheGroupsOwnName() {
        val groups = catalog().build(listOf("drm", "act", "trl", "paranormal", "hrr"))
        assertEquals(emptyList<String>(), groups.first { it.name == "Drama" }.finerTags())
        assertEquals(emptyList<String>(), groups.first { it.name == "Action & Adventure" }.finerTags())
        assertEquals(listOf("mystery thriller"), groups.first { it.name == "Mystery" }.finerTags())
        assertEquals(listOf("paranormal"), groups.first { it.name == "Horror" }.finerTags())
    }

    @Test fun europeanCodeAndBlankLabelsAreIgnored() {
        val catalog = catalog()
        assertEquals("", catalog.normalise(" EUR "))
        assertEquals("", catalog.groupOf("eur"))
        assertEquals(emptySet<String>(), catalog.groupsOf(listOf("eur", "", "  ")))
        assertEquals(emptyList<GenreGroup>(), catalog.build(listOf("eur", "", "  ")))
    }

    @Test fun emptyInputProducesNoGroups() {
        assertEquals(emptySet<String>(), catalog().groupsOf(emptyList()))
        assertEquals(emptyList<GenreGroup>(), catalog().build(emptyList()))
    }
}
