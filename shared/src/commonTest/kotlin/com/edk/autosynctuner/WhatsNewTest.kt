package com.edk.autosynctuner

import com.edk.autosynctuner.data.Changelog
import com.edk.autosynctuner.data.KeyValueStore
import com.edk.autosynctuner.ui.whatsNewAtLaunch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsNewTest {
    private class Store(vararg pairs: Pair<String, String>) : KeyValueStore {
        val map = mutableMapOf(*pairs)
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    @Test fun versionsCompareNumerically() {
        assertTrue(Changelog.compare("0.10.0", "0.9.0") > 0)
        assertEquals(0, Changelog.compare("0.6", "0.6.0"))
        assertTrue(Changelog.compare("0.5.0", "0.6.0") < 0)
    }

    @Test fun freshInstallShowsNothing() {
        val s = Store()
        assertTrue(whatsNewAtLaunch(s, "0.6.0").isEmpty())
        assertEquals("0.6.0", s.map["seenVersion"])
    }

    @Test fun existingUserUpdatingToFirstVersionWithPopupSeesIt() {
        val s = Store("email" to "x")
        assertEquals(listOf("0.6.0"), whatsNewAtLaunch(s, "0.6.0").map { it.version })
    }

    @Test fun showsEverythingSinceLastSeenOnceOnly() {
        val s = Store("email" to "x", "seenVersion" to "0.3.0")
        assertEquals(listOf("0.6.0", "0.5.0", "0.4.0"), whatsNewAtLaunch(s, "0.6.0").map { it.version })
        assertTrue(whatsNewAtLaunch(s, "0.6.0").isEmpty()) // second launch: nothing
    }

    @Test fun releasesAreNewestFirstAndNotEmpty() {
        val versions = Changelog.releases.map { it.version }
        assertEquals(versions.sortedWith { a, b -> Changelog.compare(b, a) }, versions)
        assertTrue(Changelog.releases.all { it.changes.isNotEmpty() })
    }
}
