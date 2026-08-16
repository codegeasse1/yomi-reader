package eu.kanade.tachiyomi.extension.util

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.installer.ApkExtensionKind
import eu.kanade.tachiyomi.extension.installer.ExtensionApkFileStore
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.IOException

/**
 * App-private APK downloader used as a HyperOS/MIUI-safe alternative to Android DownloadManager.
 */
class OkHttpExtensionApkDownloader(
    private val context: Context,
    private val client: OkHttpClient,
    basePreferences: BasePreferences,
) {
    private val apkFileStore = ExtensionApkFileStore(basePreferences)

    suspend fun download(
        url: String,
        packageName: String,
        displayName: String,
        kind: ApkExtensionKind,
    ): File {
        return withIOContext {
            val dir = File(context.cacheDir, DOWNLOAD_DIR).also { directory ->
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("Failed to create extension APK cache directory: ${directory.absolutePath}")
                }
            }
            val safeName = packageName.ifBlank { displayName }.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val partFile = File(dir, "$safeName.apk.part")
            val apkFile = File(dir, "$safeName.apk")

            if (partFile.exists() && !partFile.delete()) {
                throw IOException("Failed to delete stale partial APK: ${partFile.absolutePath}")
            }

            try {
                client.newCall(GET(url)).awaitSuccess().use { response ->
                    val contentLength = response.body.contentLength()
                    response.body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (partFile.length() <= 0L) {
                        throw IOException("Downloaded APK is empty")
                    }
                    if (contentLength > 0L && partFile.length() != contentLength) {
                        throw IOException(
                            "Downloaded APK size mismatch: expected=$contentLength actual=${partFile.length()}",
                        )
                    }
                }

                if (apkFile.exists() && !apkFile.delete()) {
                    throw IOException("Failed to replace previous APK: ${apkFile.absolutePath}")
                }
                if (!partFile.renameTo(apkFile)) {
                    partFile.copyTo(apkFile, overwrite = true)
                    if (!partFile.delete()) {
                        logcat(LogPriority.WARN) { "Failed to delete partial APK after copy: ${partFile.absolutePath}" }
                    }
                }
                apkFileStore.save(
                    ExtensionApkFileStore.ApkFile(
                        packageName = packageName,
                        displayName = displayName,
                        filePath = apkFile.absolutePath,
                        kind = kind,
                    ),
                )
                logcat(LogPriority.INFO) {
                    "Downloaded extension APK via OkHttp name=$displayName package=$packageName bytes=${apkFile.length()}"
                }
                apkFile
            } catch (e: Exception) {
                partFile.delete()
                apkFile.delete()
                throw e
            }
        }
    }

    private companion object {
        const val DOWNLOAD_DIR = "extension_apks"
    }
}
