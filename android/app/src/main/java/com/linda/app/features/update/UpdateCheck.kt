package com.linda.app.features.update

import org.json.JSONObject

/** A newer Linda on the team's "latest-build" release: its build number and where to download it. */
data class AvailableUpdate(val build: Int, val apkUrl: String, val sizeBytes: Long)

/**
 * Reads the "latest-build" release that GitHub Actions publishes on every merge (.github/workflows/build-apk.yml).
 * Kept free of Android so it can be unit tested. Hackathon only: in production updates come from the Play Store or
 * My OneApp, and this feature is removed.
 */
object UpdateCheck {
    /** The public release page's data. No token: the repository is public, and the app must never carry a secret. */
    const val RELEASE_API = "https://api.github.com/repos/lestersinjacreater/linda-test-app./releases/tags/latest-build"

    /** The small shrunk build. It installs over linda-debug.apk too: both are signed with the same key. */
    const val APK_NAME = "linda.apk"

    /** "Latest build #182 (3588ccc)" -> 182. The same number is the app's versionCode (app/build.gradle.kts). */
    fun buildNumber(releaseName: String): Int? = Regex("""#(\d+)""").find(releaseName)?.groupValues?.get(1)?.toIntOrNull()

    /** The update, or null when the release is not newer than [installedBuild] or has no linda.apk. */
    fun newer(releaseJson: String, installedBuild: Int): AvailableUpdate? {
        return try {
            val release = JSONObject(releaseJson)
            val build = buildNumber(release.optString("name")) ?: return null
            if (build <= installedBuild) return null
            val assets = release.getJSONArray("assets")
            (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == APK_NAME }
                ?.let { AvailableUpdate(build, it.getString("browser_download_url"), it.optLong("size")) }
        } catch (e: Exception) {
            null // a page we cannot read is "no update", never a crash
        }
    }

    /** "2.3" for the row's "About 2.3 MB". */
    fun megabytes(bytes: Long): String = String.format(java.util.Locale.US, "%.1f", bytes / 1_048_576.0)
}
