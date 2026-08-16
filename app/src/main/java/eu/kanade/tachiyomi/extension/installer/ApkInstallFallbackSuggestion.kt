package eu.kanade.tachiyomi.extension.installer

/**
 * User-visible fallback suggestion after an APK install backend fails.
 *
 * This model intentionally represents a prompt/suggestion only. It must not trigger another
 * installer backend without explicit user action.
 */
data class ApkInstallFallbackSuggestion(
    val packageName: String,
    val displayName: String,
    val kind: ApkExtensionKind,
    val failedBackend: ApkInstallBackend,
    val reason: String,
    val suggestedBackends: List<ApkInstallBackend>,
)
