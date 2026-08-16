package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.PrivateExtensionInstallResult
import eu.kanade.tachiyomi.extension.novel.kotlin.KotlinNovelExtensionLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tachiyomi.core.common.util.lang.withIOContext

class PrivateApkInstallBackendAdapter(
    private val context: Context,
) : ApkInstallBackendAdapter {
    override val backend = ApkInstallBackend.PRIVATE

    override fun supports(kind: ApkExtensionKind): Boolean = kind == ApkExtensionKind.NOVEL_KOTLIN

    override fun install(request: ApkInstallRequest): Flow<InstallStep> = flow {
        emit(InstallStep.Installing)
        val file = request.file ?: return@flow emit(InstallStep.Error)
        val installed = withIOContext {
            KotlinNovelExtensionLoader.installPrivateExtensionFile(context, file, request.packageName)
        }
        emit(
            if (installed == PrivateExtensionInstallResult.Success) {
                InstallStep.Installed
            } else {
                InstallStep.Error
            },
        )
    }

    override suspend fun uninstall(request: ApkUninstallRequest): ApkInstallResult {
        return withIOContext {
            KotlinNovelExtensionLoader.uninstallPrivateExtension(context, request.packageName)
            ApkInstallResult.Installed
        }
    }

    override fun cancel(packageName: String) = Unit
}
