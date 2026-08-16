package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import eu.kanade.tachiyomi.util.lang.Hash
import java.io.File

/**
 * Signature helpers for the install pipeline: lets the caller compare a downloaded APK against the
 * currently installed copy, so a signing-key change can be detected even when the backend reported
 * only a generic failure — and a reinstall-with-uninstall can be offered instead of a bare error.
 */
object ExtensionSignatureComparison {

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_META_DATA or
        PackageManager.GET_SIGNATURES or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    /** SHA-256 fingerprints of the signing certs of [apkFile], or null when it is not parseable. */
    fun apkSignatures(context: Context, apkFile: File): List<String>? {
        val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PACKAGE_FLAGS)
            ?: return null
        return getSignatures(info)
    }

    /** SHA-256 fingerprints of the signing certs of the installed [packageName], or null when absent. */
    fun installedSignatures(context: Context, packageName: String): List<String>? {
        val info = runCatching {
            context.packageManager.getPackageInfo(packageName, PACKAGE_FLAGS)
        }.getOrNull() ?: return null
        return getSignatures(info)
    }

    /**
     * Whether the downloaded [apkFile] is signed with a different key than the installed
     * [packageName]. Returns null when either side cannot be inspected (fresh install, unparseable
     * APK), so callers can treat it as "cannot tell" rather than a mismatch.
     */
    fun signaturesDiffer(context: Context, apkFile: File, packageName: String): Boolean? {
        val apkSignatures = apkSignatures(context, apkFile) ?: return null
        val installedSignatures = installedSignatures(context, packageName) ?: return null
        if (apkSignatures.isEmpty() || installedSignatures.isEmpty()) return null
        // Same containment semantics as canReplacePrivateExtension: the new build must carry
        // every key the installed copy was signed with.
        return !apkSignatures.containsAll(installedSignatures)
    }

    private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
            ?.map { Hash.sha256(it.toByteArray()) }
            ?.toList()
    }
}
