package com.edk.autosynctuner

import com.edk.autosynctuner.data.Changelog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fails the build if VERSION_NAME was bumped without adding release notes to Changelog.kt. */
class ChangelogVersionTest {
    @Test fun newestReleaseNotesMatchAppVersion() {
        val props = listOf(File("../gradle.properties"), File("gradle.properties")).first { it.exists() }
        val version = props.readLines().first { it.startsWith("VERSION_NAME=") }.substringAfter('=').trim()
        assertEquals(version, Changelog.releases.first().version, "Add a $version entry at the top of Changelog.kt")
    }
}
