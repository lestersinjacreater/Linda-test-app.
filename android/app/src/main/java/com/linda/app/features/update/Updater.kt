package com.linda.app.features.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.linda.app.BuildConfig
import com.linda.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Update Linda" from Settings: ask GitHub for the latest build, download linda.apk into the app's own cache, then hand
 * it to Android's installer. Android always shows its own "Update this app?" screen: no app can update itself silently.
 * Android also refuses an APK signed with a different key, so only the team's builds can ever replace Linda.
 * Nothing about the person is sent: it is one plain request for the public release page.
 */
object Updater {
    private const val FOLDER = "updates" // inside cacheDir; matches res/xml/update_paths.xml

    private const val RECHECK_MS = 30 * 60 * 1000L
    @Volatile private var lastAnswer: Pair<Long, AvailableUpdate?>? = null

    /**
     * The newer build, or null when Linda is up to date. Throws when GitHub cannot be reached, so the row can say so.
     * The answer is reused for 30 minutes: GitHub allows 60 unsigned requests an hour per network, and on hack day many
     * phones share the venue's Wi-Fi. [force] (the person tapped "try again") always asks.
     */
    suspend fun check(force: Boolean = false): AvailableUpdate? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = lastAnswer
        if (!force && cached != null && now - cached.first < RECHECK_MS) return@withContext cached.second
        val response = Http.get(UpdateCheck.RELEASE_API, headers = mapOf("Accept" to "application/vnd.github+json"))
            ?: throw java.io.IOException("unreachable")
        if (response.code != 200) throw java.io.IOException("HTTP ${response.code}")
        UpdateCheck.newer(response.body, BuildConfig.VERSION_CODE).also { lastAnswer = now to it }
    }

    /** Downloads the APK, reporting 0..100. Returns null when the download fails or is cut short. */
    suspend fun download(context: Context, update: AvailableUpdate, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, FOLDER).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only ever keep the one being installed
        val file = File(dir, "linda-${update.build}.apk")
        try {
            // GitHub answers with a redirect to its file storage; HttpURLConnection follows https -> https on its own.
            val c = URL(update.apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            if (c.responseCode != 200) return@withContext null
            val total = c.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            var done = 0L
            var shown = -1
            c.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                        if (pct != shown) { shown = pct; withContext(Dispatchers.Main) { onProgress(pct) } }
                    }
                }
            }
            c.disconnect()
            // A short file is a broken download: never hand half an APK to the installer.
            if (update.sizeBytes > 0 && file.length() != update.sizeBytes) null else file
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /** False when Android first needs the person to allow "Install unknown apps" for Linda (Android 8+). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens Android's "Install unknown apps" switch for Linda. */
    fun openInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Hands the downloaded APK to Android's installer, which asks the person to confirm. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
