package com.edk.autosynctuner.data

/** One release's notes for the "What's new" popup. */
data class Release(val version: String, val changes: List<String>)

/**
 * The app's release notes, newest first. When VERSION_NAME in gradle.properties is
 * bumped, add an entry here (a test fails the build if the newest entry doesn't match).
 */
object Changelog {
    val releases = listOf(
        Release(
            "0.6.0",
            listOf(
                "\"What's new\" now appears after each update (you're reading it).",
                "Send feedback: a button on the vehicle list and sign-in screen, plus \"Report a problem with this setting\" on every setting.",
                "Behind the scenes: the app's website connection is now checked every day, so problems get caught early.",
            ),
        ),
        Release(
            "0.5.0",
            listOf(
                "Try demo: explore the app with sample trucks and settings, no account needed.",
            ),
        ),
        Release(
            "0.4.0",
            listOf(
                "Module tabs show license status: a green check for \"Licensed - Ready\", a lock with a Get license link otherwise.",
                "Android: the app checks for updates and offers to download them.",
            ),
        ),
        Release(
            "0.3.0",
            listOf(
                "Switch between vehicles, and see the AutoSync devices on your account.",
                "The app reopens the last vehicle you used.",
            ),
        ),
    )

    /** Releases newer than [seenVersion], up to and including [currentVersion]. */
    fun since(seenVersion: String, currentVersion: String): List<Release> =
        releases.filter { compare(it.version, seenVersion) > 0 && compare(it.version, currentVersion) <= 0 }

    /** Compares "0.10.2" style versions numerically. */
    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
