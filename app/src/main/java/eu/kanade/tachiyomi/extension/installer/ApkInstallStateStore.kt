package eu.kanade.tachiyomi.extension.installer

import eu.kanade.tachiyomi.extension.InstallStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class ApkInstallStateStore {
    data class ApkInstallState(
        val packageName: String,
        val kind: ApkExtensionKind,
        val step: InstallStep,
        val downloadBackend: ApkDownloadBackend = ApkDownloadBackend.AUTO,
        val installBackend: ApkInstallBackend,
        val filePath: String? = null,
        val failure: ApkInstallFailure? = null,
    )

    private val states = MutableStateFlow<Map<String, ApkInstallState>>(emptyMap())

    fun update(state: ApkInstallState) {
        states.update { it + (state.packageName to state) }
    }

    fun observe(packageName: String): Flow<InstallStep> {
        return states.map { it[packageName]?.step ?: InstallStep.Idle }
    }

    fun get(packageName: String): ApkInstallState? = states.value[packageName]

    fun clear(packageName: String) {
        states.update { it - packageName }
    }
}
