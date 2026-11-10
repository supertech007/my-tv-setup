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

class DockShortcutOrderTest {
    private val apps = listOf("prime", "hotstar", "sony")

    @org.junit.Test fun shortcutsStartAfterTheApps() {
        org.junit.Assert.assertEquals(apps + listOf(ALL_APPS, HISTORY, SCREEN_OFF, MUTE, SETTINGS), dockIds(apps, emptyList()))
    }

    @org.junit.Test fun movedShortcutsKeepTheirPlace() {
        val moved = com.mytvsetup.launcher.settings.moveInDock(dockIds(apps, emptyList()), SETTINGS, -6)
        org.junit.Assert.assertEquals(listOf("prime", SETTINGS, "hotstar", "sony", ALL_APPS, HISTORY, SCREEN_OFF, MUTE), moved)
        org.junit.Assert.assertEquals(moved, dockIds(apps, moved))
    }

    @org.junit.Test fun newAppsJoinBeforeShortcutsThatWereNeverMoved() {
        val order = listOf(SCREEN_OFF, "prime", "hotstar")
        org.junit.Assert.assertEquals(listOf(SCREEN_OFF, "prime", "hotstar", "sony", ALL_APPS, HISTORY, MUTE, SETTINGS),
            dockIds(apps, order))
    }

    @org.junit.Test fun hiddenOrUninstalledAppsInTheSavedOrderAreSkipped() {
        org.junit.Assert.assertEquals(listOf("prime", ALL_APPS, "sony", HISTORY, SCREEN_OFF, MUTE, SETTINGS),
            dockIds(listOf("prime", "sony"), listOf("prime", "gone", ALL_APPS, "sony")))
    }
}
