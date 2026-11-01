package com.mytvsetup.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class DockTest {
    private val installed = listOf(
        AppEntry("z", "Zulu", null, null),
        AppEntry("b", "Bravo", null, null),
        AppEntry("a", "alpha", null, null),
        AppEntry("c", "Charlie", null, null)
    )

    @Test fun preferredPackagesLeadInSavedOrderThenOthersSortByLabel() {
        assertEquals(
            listOf("c", "b", "a", "z"),
            dockApps(installed, listOf("c", "b"), emptySet()).map { it.pkg }
        )
    }

    @Test fun hiddenPackagesAreExcludedFromBothPreferredAndRemainingApps() {
        assertEquals(
            listOf("b", "a"),
            dockApps(installed, listOf("c", "b"), setOf("c", "z")).map { it.pkg }
        )
    }

    @Test fun uninstalledPreferredPackagesAreSkipped() {
        assertEquals(
            listOf("b", "a", "c", "z"),
            dockApps(installed, listOf("missing", "b"), emptySet()).map { it.pkg }
        )
    }

    @Test fun emptyPreferenceOrderSortsAllAppsByLabel() {
        assertEquals(listOf("a", "b", "c", "z"), dockApps(installed, emptyList(), emptySet()).map { it.pkg })
    }

    @Test fun repeatedPreferredPackagesAppearOnlyOnce() {
        assertEquals(
            listOf("b", "a", "c", "z"),
            dockApps(installed, listOf("b", "b"), emptySet()).map { it.pkg }
        )
    }

    @Test fun noInstalledAppsGivesEmptyDock() {
        assertEquals(emptyList<AppEntry>(), dockApps(emptyList(), listOf("b"), emptySet()))
    }
}
