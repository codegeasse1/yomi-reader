package eu.kanade.tachiyomi.extension.installer

import eu.kanade.tachiyomi.extension.InstallStep
import kotlinx.coroutines.flow.Flow

interface ApkInstallBackendAdapter {
    val backend: ApkInstallBackend
    fun supports(kind: ApkExtensionKind): Boolean
    fun install(request: ApkInstallRequest): Flow<InstallStep>
    suspend fun uninstall(request: ApkUninstallRequest): ApkInstallResult
    fun cancel(packageName: String)
}
