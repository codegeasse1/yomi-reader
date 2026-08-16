package eu.kanade.tachiyomi.extension.manga.installer

import android.app.Service
import com.rosan.dhizuku.api.Dhizuku
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.ApkInstallFailure
import eu.kanade.tachiyomi.extension.installer.DhizukuShellRunner
import eu.kanade.tachiyomi.extension.installer.DhizukuShellRunner.SESSION_ID_REGEX
import eu.kanade.tachiyomi.extension.installer.classifyInstallError
import eu.kanade.tachiyomi.util.system.getUriSize
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

class DhizukuInstallerManga(private val service: Service) : InstallerManga(service) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override var ready = false

    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        scope.launch {
            var sessionId: String? = null
            try {
                val size = service.getUriSize(entry.uri)
                    ?: throw IllegalStateException("Unable to determine APK size for ${entry.uri}")
                val inputStream =
                    service.contentResolver.openInputStream(entry.uri)
                        ?: throw IllegalStateException("Unable to open APK input stream for ${entry.uri}")
                inputStream.use {
                    val createResult = DhizukuShellRunner.exec(
                        arrayOf("pm", "install-create", "-r", "-i", service.packageName, "-S", size.toString()),
                    )
                    sessionId = SESSION_ID_REGEX.find(createResult.combinedOutput)?.value
                        ?: throw RuntimeException("Failed to create install session: ${createResult.combinedOutput}")

                    val writeResult = DhizukuShellRunner.exec(
                        arrayOf("pm", "install-write", "-S", size.toString(), sessionId, "base", "-"),
                        it,
                    )
                    if (writeResult.resultCode != 0) {
                        throw RuntimeException(
                            "Failed to write APK to session $sessionId: ${writeResult.combinedOutput}",
                        )
                    }

                    val commitResult = DhizukuShellRunner.exec(
                        arrayOf("pm", "install-commit", sessionId),
                    )
                    if (commitResult.resultCode != 0) {
                        throw RuntimeException(
                            "Failed to commit install session $sessionId: ${commitResult.combinedOutput}",
                        )
                    }

                    continueQueue(InstallStep.Installed)
                }
            } catch (e: Exception) {
                if (classifyInstallError(e.message) == ApkInstallFailure.SignatureMismatch) {
                    logcat(LogPriority.WARN, e) {
                        "Signature mismatch for extension ${entry.pkgName}; " +
                            "reinstall-with-uninstall required"
                    }
                    entry.pkgName?.let { extensionManager.reportSignatureMismatch(it) }
                } else {
                    logcat(LogPriority.ERROR, e) { "Failed to install extension ${entry.downloadId} ${entry.uri}" }
                }
                if (sessionId != null) {
                    DhizukuShellRunner.exec(arrayOf("pm", "install-abandon", sessionId!!))
                }
                continueQueue(InstallStep.Error)
            }
        }
    }

    // Don't cancel if entry is already started installing
    override fun cancelEntry(entry: Entry): Boolean = getActiveEntry() != entry

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    init {
        ready = if (Dhizuku.init(service)) {
            if (Dhizuku.isPermissionGranted()) {
                true
            } else {
                Dhizuku.requestPermission(DhizukuPermissionListener(this))
                logcat(LogPriority.INFO) { "Dhizuku permission needed, waiting for user grant" }
                false
            }
        } else {
            logcat(LogPriority.ERROR) { "Dhizuku is not ready to use" }
            service.toast(MR.strings.ext_installer_dhizuku_stopped)
            service.stopSelf()
            false
        }
    }

    private class DhizukuPermissionListener(
        installer: DhizukuInstallerManga,
    ) : com.rosan.dhizuku.api.DhizukuRequestPermissionListener() {
        private val installerRef = java.lang.ref.WeakReference(installer)

        override fun onRequestPermission(grantResult: Int) {
            val installer = installerRef.get() ?: return
            // Grant result codes: 0 = granted, non-0 = denied
            if (grantResult == 0) {
                installer.ready = true
                installer.checkQueue()
            } else {
                logcat(LogPriority.WARN) { "Dhizuku permission denied by user" }
                installer.service.stopSelf()
            }
        }
    }
}
