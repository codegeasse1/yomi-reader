package eu.kanade.tachiyomi.extension.installer

import android.content.Context
import android.os.Build
import com.yomi.reader.BuildConfig
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.hasMiuiPackageInstaller
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copy-safe diagnostic snapshot for extension install failures.
 */
data class ExtensionInstallDiagnostic(
    val timestamp: String,
    val kind: String,
    val packageName: String,
    val displayName: String,
    val selectedInstaller: String,
    val downloadBackend: ApkDownloadBackend,
    val installBackend: ApkInstallBackend,
    val installUnknownAppsGranted: Boolean,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val androidSdk: Int,
    val buildIncremental: String,
    val isMiui: Boolean,
    val hasMiuiPackageInstaller: Boolean,
    val appVersionName: String,
    val appVersionCode: Int,
    val repoUrl: String?,
    val assetUrl: String?,
    val failureClass: String?,
    val failureMessage: String?,
) {
    fun format(): String {
        return buildString {
            appendLine("Extension install diagnostic")
            appendLine("timestamp=$timestamp")
            appendLine("kind=$kind")
            appendLine("packageName=$packageName")
            appendLine("displayName=$displayName")
            appendLine("selectedInstaller=$selectedInstaller")
            appendLine("downloadBackend=$downloadBackend")
            appendLine("installBackend=$installBackend")
            appendLine("installUnknownAppsGranted=$installUnknownAppsGranted")
            appendLine("device=$manufacturer $model")
            appendLine("android=$androidVersion sdk=$androidSdk incremental=$buildIncremental")
            appendLine("isMiui=$isMiui")
            appendLine("hasMiuiPackageInstaller=$hasMiuiPackageInstaller")
            appendLine("appVersion=$appVersionName ($appVersionCode)")
            repoUrl?.let { appendLine("repoUrl=$it") }
            assetUrl?.let { appendLine("assetUrl=$it") }
            failureClass?.let { appendLine("failureClass=$it") }
            failureMessage?.let { appendLine("failureMessage=$it") }
        }
    }

    companion object {
        fun forNovelPlugin(
            context: Context,
            basePreferences: BasePreferences,
            packageName: String,
            displayName: String,
            repoUrl: String?,
            assetUrl: String?,
            isKotlinExtension: Boolean,
            failure: Throwable?,
        ): ExtensionInstallDiagnostic {
            val selectedInstaller = basePreferences.extensionInstaller().get()
            val installBackend = selectedInstaller.toApkInstallBackend()
            return ExtensionInstallDiagnostic(
                timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()),
                kind = if (isKotlinExtension) "NOVEL_KOTLIN" else "NOVEL_JS",
                packageName = packageName,
                displayName = displayName,
                selectedInstaller = selectedInstaller.name,
                downloadBackend = ApkExtensionInstallPolicy.selectDownloadBackend(
                    context = context,
                    kind = ApkExtensionKind.NOVEL_KOTLIN,
                    preferredBackend = ApkDownloadBackend.AUTO,
                ),
                installBackend = installBackend,
                installUnknownAppsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.packageManager.canRequestPackageInstalls()
                } else {
                    true
                },
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                androidVersion = Build.VERSION.RELEASE.orEmpty(),
                androidSdk = Build.VERSION.SDK_INT,
                buildIncremental = Build.VERSION.INCREMENTAL.orEmpty(),
                isMiui = DeviceUtil.isMiui,
                hasMiuiPackageInstaller = context.hasMiuiPackageInstaller,
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                repoUrl = repoUrl?.sanitizeUrl(),
                assetUrl = assetUrl?.sanitizeUrl(),
                failureClass = failure?.let { it::class.qualifiedName ?: it::class.simpleName },
                failureMessage = failure?.message?.take(MAX_FAILURE_MESSAGE_LENGTH),
            )
        }

        fun getInstallerDiagnosticString(context: Context, basePreferences: BasePreferences): String {
            val installer = basePreferences.extensionInstaller().get()
            val installUnknownAppsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else {
                true
            }
            return buildString {
                appendLine("Installer compatibility diagnostics")
                appendLine("manufacturer=${Build.MANUFACTURER}")
                appendLine("model=${Build.MODEL}")
                appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                appendLine("incremental=${Build.VERSION.INCREMENTAL}")
                appendLine("isMiui=${DeviceUtil.isMiui}")
                appendLine("hasMiuiPackageInstaller=${context.hasMiuiPackageInstaller}")
                appendLine("selectedInstaller=$installer")
                appendLine("installUnknownAppsGranted=$installUnknownAppsGranted")
                val isMiuiOptDisabled = if (DeviceUtil.isMiui) DeviceUtil.isMiuiOptimizationDisabled() else false
                appendLine("isMiuiOptimizationDisabled=$isMiuiOptDisabled")
                appendLine(
                    "autoDownloadManga=" + ApkExtensionInstallPolicy.selectDownloadBackend(
                        context = context,
                        kind = ApkExtensionKind.MANGA,
                        preferredBackend = ApkDownloadBackend.AUTO,
                    ),
                )
                appendLine(
                    "autoDownloadAnime=" + ApkExtensionInstallPolicy.selectDownloadBackend(
                        context = context,
                        kind = ApkExtensionKind.ANIME,
                        preferredBackend = ApkDownloadBackend.AUTO,
                    ),
                )
                appendLine(
                    "autoDownloadNovel=" + ApkExtensionInstallPolicy.selectDownloadBackend(
                        context = context,
                        kind = ApkExtensionKind.NOVEL_KOTLIN,
                        preferredBackend = ApkDownloadBackend.AUTO,
                    ),
                )
                appendLine(
                    "recommendation=" + if (DeviceUtil.isMiui || context.hasMiuiPackageInstaller) {
                        if (isMiuiOptDisabled) {
                            "PRIVATE (Shizuku if rooted/ADB, Legacy if manual)"
                        } else {
                            "PRIVATE (MIUI Optimization is enabled. PackageInstaller may block/timeout. Prefer Shizuku or Legacy/Share)"
                        }
                    } else {
                        "PACKAGEINSTALLER (standard AOSP)"
                    },
                )
            }
        }

        private fun String.sanitizeUrl(): String {
            return substringBefore('#')
                .substringBefore('?')
                .take(MAX_URL_LENGTH)
        }

        private const val MAX_URL_LENGTH = 512
        private const val MAX_FAILURE_MESSAGE_LENGTH = 1000
    }
}
