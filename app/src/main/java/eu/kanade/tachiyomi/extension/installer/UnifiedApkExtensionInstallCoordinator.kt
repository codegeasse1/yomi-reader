package eu.kanade.tachiyomi.extension.installer

import eu.kanade.tachiyomi.extension.InstallStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class UnifiedApkExtensionInstallCoordinator(
    private val stateStore: ApkInstallStateStore,
    private val backendAdapters: Set<ApkInstallBackendAdapter>,
) : UnifiedApkExtensionInstaller {
    override fun install(request: ApkInstallRequest): Flow<InstallStep> {
        val adapter = backendAdapters.firstOrNull { it.backend == request.backend && it.supports(request.kind) }
            ?: return kotlinx.coroutines.flow.flowOf(InstallStep.Error).onEach {
                stateStore.update(
                    ApkInstallStateStore.ApkInstallState(
                        packageName = request.packageName,
                        kind = request.kind,
                        step = InstallStep.Error,
                        installBackend = request.backend,
                        filePath = request.file?.absolutePath,
                        failure = ApkInstallFailure.Unknown(
                            "Unsupported backend ${request.backend} for ${request.kind}",
                        ),
                    ),
                )
            }
        stateStore.update(
            ApkInstallStateStore.ApkInstallState(
                packageName = request.packageName,
                kind = request.kind,
                step = InstallStep.Pending,
                installBackend = request.backend,
                filePath = request.file?.absolutePath,
            ),
        )
        return adapter.install(request).onEach { step ->
            stateStore.update(
                ApkInstallStateStore.ApkInstallState(
                    packageName = request.packageName,
                    kind = request.kind,
                    step = step,
                    installBackend = request.backend,
                    filePath = request.file?.absolutePath,
                ),
            )
        }
    }

    fun observe(packageName: String): Flow<InstallStep> = stateStore.observe(packageName)

    override fun cancel(packageName: String) {
        backendAdapters.forEach { it.cancel(packageName) }
        stateStore.clear(packageName)
    }

    override suspend fun uninstall(request: ApkUninstallRequest): ApkInstallResult {
        val adapter = request.backend
            ?.let { backend ->
                backendAdapters.firstOrNull { it.supports(request.kind) && it.backend == backend }
            }
            ?: backendAdapters.firstOrNull { it.supports(request.kind) }
            ?: return ApkInstallResult.Error("Unsupported uninstall kind ${request.kind}")
        return adapter.uninstall(request)
    }
}
