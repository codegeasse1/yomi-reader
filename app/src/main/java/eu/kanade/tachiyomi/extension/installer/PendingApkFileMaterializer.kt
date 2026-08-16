package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import android.net.Uri
import tachiyomi.core.common.util.lang.withIOContext
import java.io.File
import java.io.IOException

/**
 * Converts potentially short-lived install URIs into stable app-private APK files so pending
 * installs can be resumed after the user grants "Install unknown apps" permission.
 */
class PendingApkFileMaterializer {
    suspend fun materialize(
        context: Context,
        uri: Uri,
        packageName: String,
    ): File {
        return withIOContext {
            val dir = File(context.cacheDir, PENDING_INSTALL_DIR).also { directory ->
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("Failed to create pending APK cache directory: ${directory.absolutePath}")
                }
            }
            val safeName = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val partFile = File(dir, "$safeName.apk.part")
            val apkFile = File(dir, "$safeName.apk")

            partFile.delete()

            when (uri.scheme) {
                "file" -> {
                    val sourceFile = File(uri.path ?: throw IOException("Missing file path for $uri"))
                    if (!sourceFile.isFile) {
                        throw IOException(
                            "APK source file does not exist: ${sourceFile.absolutePath}",
                        )
                    }
                    sourceFile.copyTo(partFile, overwrite = true)
                }
                null -> {
                    val sourceFile = File(uri.toString())
                    if (!sourceFile.isFile) {
                        throw IOException(
                            "APK source file does not exist: ${sourceFile.absolutePath}",
                        )
                    }
                    sourceFile.copyTo(partFile, overwrite = true)
                }
                else -> {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        partFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IOException("Unable to open APK input stream for $uri")
                }
            }

            if (partFile.length() <= 0L) {
                partFile.delete()
                throw IOException("Materialized APK is empty for $uri")
            }
            apkFile.delete()
            if (!partFile.renameTo(apkFile)) {
                partFile.copyTo(apkFile, overwrite = true)
                partFile.delete()
            }
            apkFile
        }
    }

    private companion object {
        const val PENDING_INSTALL_DIR = "pending_apk_installs"
    }
}
