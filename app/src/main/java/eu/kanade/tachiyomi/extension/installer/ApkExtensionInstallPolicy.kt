package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.util.system.hasMiuiPackageInstaller

fun BasePreferences.ExtensionInstaller.toApkInstallBackend(): ApkInstallBackend {
    return when (this) {
        BasePreferences.ExtensionInstaller.PACKAGEINSTALLER -> ApkInstallBackend.PACKAGE_INSTALLER
        BasePreferences.ExtensionInstaller.LEGACY -> ApkInstallBackend.LEGACY
        BasePreferences.ExtensionInstaller.SHIZUKU -> ApkInstallBackend.SHIZUKU
        BasePreferences.ExtensionInstaller.DHIZUKU -> ApkInstallBackend.DHIZUKU
        BasePreferences.ExtensionInstaller.PRIVATE -> ApkInstallBackend.PRIVATE
    }
}

object ApkExtensionInstallPolicy {
    fun selectDownloadBackend(
        context: Context,
        kind: ApkExtensionKind,
        preferredBackend: ApkDownloadBackend,
    ): ApkDownloadBackend {
        return when (preferredBackend) {
            ApkDownloadBackend.DOWNLOAD_MANAGER,
            ApkDownloadBackend.OKHTTP,
            -> preferredBackend
            ApkDownloadBackend.AUTO -> {
                when {
                    context.hasMiuiPackageInstaller -> ApkDownloadBackend.OKHTTP
                    kind == ApkExtensionKind.NOVEL_KOTLIN -> ApkDownloadBackend.OKHTTP
                    else -> ApkDownloadBackend.DOWNLOAD_MANAGER
                }
            }
        }
    }
}
