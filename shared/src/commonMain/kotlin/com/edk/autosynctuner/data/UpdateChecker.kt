package com.edk.autosynctuner.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

/** This build's version, supplied by the platform launcher. */
data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    /** Where to look for newer builds, or null where the store handles updates (iOS/TestFlight). */
    val updateManifestUrl: String?,
)

data class UpdateInfo(val versionName: String, val versionCode: Long, val downloadUrl: String)

/**
 * Checks the project's GitHub "latest" release for a newer Android build.
 * Each CI build publishes version.json next to the APK:
 *   {"versionCode": 14, "versionName": "0.4.0", "apkUrl": "https://…/AutoSyncTuner.apk"}
 * Uses its own HTTP client so the AutoSync sign-in cookie is never sent to GitHub.
 */
class UpdateChecker(private val app: AppInfo) {
    private val http = createHttpClient { followRedirects = true; expectSuccess = false }

    val supported: Boolean get() = app.updateManifestUrl != null

    /** Returns the newer build, null if this one is current, or throws if the check failed. */
    suspend fun check(): UpdateInfo? {
        val url = app.updateManifestUrl ?: return null
        val json = try {
            val resp = http.get(url)
            if (resp.status.value !in 200..299) throw NetworkException("Update check failed (HTTP ${resp.status.value}).")
            resp.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            throw e
        } catch (e: Exception) {
            throw NetworkException("Couldn't check for updates.", e)
        }
        val info = parseManifest(json) ?: throw NetworkException("The update information couldn't be read.")
        return info.takeIf { it.versionCode > app.versionCode }
    }

    companion object {
        /** Tiny reader for the three fields above (avoids a JSON library for one file). */
        fun parseManifest(json: String): UpdateInfo? {
            fun str(key: String) = Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1)
            val code = Regex(""""versionCode"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull()
                ?: return null
            val apk = str("apkUrl")?.takeIf { it.startsWith("https://") } ?: return null
            return UpdateInfo(str("versionName").orEmpty(), code, apk)
        }
    }
}
