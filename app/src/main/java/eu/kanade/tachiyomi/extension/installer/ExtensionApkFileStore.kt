package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.toShareIntent
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import java.io.File
import tachiyomi.i18n.R as I18nR

/**
 * Stores the last app-private APK downloaded for an extension so users can manually share/install
 * it when OEM package installers fail.
 */
class ExtensionApkFileStore(
    private val basePreferences: BasePreferences,
) {
    data class ApkFile(
        val packageName: String,
        val displayName: String,
        val filePath: String,
        val kind: ApkExtensionKind,
    )

    fun save(apkFile: ApkFile) {
        basePreferences.lastExtensionApkPackage().set(apkFile.packageName)
        basePreferences.lastExtensionApkDisplayName().set(apkFile.displayName)
        basePreferences.lastExtensionApkPath().set(apkFile.filePath)
        basePreferences.lastExtensionApkKind().set(apkFile.kind.name)
    }

    fun get(packageName: String? = null): ApkFile? {
        val storedPackage = basePreferences.lastExtensionApkPackage().get().takeIf { it.isNotBlank() } ?: return null
        if (packageName != null && packageName != storedPackage) return null
        val displayName = basePreferences.lastExtensionApkDisplayName().get()
        val filePath = basePreferences.lastExtensionApkPath().get().takeIf { it.isNotBlank() } ?: return null
        val kind = basePreferences.lastExtensionApkKind().get().toEnumOrNull<ApkExtensionKind>() ?: return null
        return ApkFile(
            packageName = storedPackage,
            displayName = displayName,
            filePath = filePath,
            kind = kind,
        )
    }

    fun clear() {
        basePreferences.lastExtensionApkPackage().set("")
        basePreferences.lastExtensionApkDisplayName().set("")
        basePreferences.lastExtensionApkPath().set("")
        basePreferences.lastExtensionApkKind().set("")
    }

    suspend fun share(context: Context, packageName: String? = null): Boolean {
        val apk = get(packageName) ?: return false
        val file = File(apk.filePath)
        val exists = withIOContext { file.isFile }
        if (!exists) {
            logcat(LogPriority.WARN) {
                "Stored extension APK is missing package=${apk.packageName} path=${apk.filePath}"
            }
            clear()
            return false
        }

        return runCatching {
            val message = context.getString(I18nR.string.ext_manual_apk_share_message)
            withUIContext {
                context.startActivity(
                    file.getUriCompat(context).toShareIntent(
                        context = context,
                        type = APK_MIME,
                        message = message,
                    ),
                )
            }
            true
        }.getOrElse { error ->
            logcat(LogPriority.ERROR, error) {
                "Failed to share extension APK package=${apk.packageName} path=${apk.filePath}"
            }
            false
        }
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? {
        return runCatching { enumValueOf<T>(this) }.getOrNull()
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
