package eu.kanade.tachiyomi.extension.installer

sealed class ApkInstallFailure {
    data object PermissionMissing : ApkInstallFailure()
    data object PackageInstallerTimeout : ApkInstallFailure()
    data object PackageInstallerAborted : ApkInstallFailure()
    data object DownloadFailed : ApkInstallFailure()
    data object MissingApkFile : ApkInstallFailure()

    /** The APK is signed with a different key than the installed copy (update-incompatible). */
    data object SignatureMismatch : ApkInstallFailure()

    data class Unknown(val message: String?) : ApkInstallFailure()
}

/**
 * Maps raw installer backend error text to [ApkInstallFailure] so the UI can offer precise
 * follow-up actions (e.g. reinstall-with-uninstall on a signing-key change).
 */
fun classifyInstallError(message: String?): ApkInstallFailure {
    val text = message.orEmpty()
    return when {
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in text -> ApkInstallFailure.SignatureMismatch
        "STATUS_FAILURE_ABORTED" in text ||
            "INSTALL_FAILED_USER_CANCELLED" in text ||
            "INSTALL_CANCELED" in text -> ApkInstallFailure.PackageInstallerAborted
        else -> ApkInstallFailure.Unknown(message)
    }
}
