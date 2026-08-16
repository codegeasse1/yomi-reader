package eu.kanade.tachiyomi.extension.installer

/**
 * Outcome of installing a private extension file into the app's own directory.
 *
 * Carries the reason so callers can offer precise follow-up actions — most importantly
 * reinstall-with-uninstall when the signing key differs from the installed copy.
 */
sealed class PrivateExtensionInstallResult {
    /** The archive was copied and (re)registered. */
    data object Success : PrivateExtensionInstallResult()

    /** The archive is signed, but with a different key than the installed copy. */
    data object SignatureMismatch : PrivateExtensionInstallResult()

    /** The archive is older than the installed copy. */
    data object Downgrade : PrivateExtensionInstallResult()

    /** The archive is not a valid extension (no feature, bad package name, unsigned). */
    data object InvalidApk : PrivateExtensionInstallResult()

    /** IO/filesystem failure while copying the archive. */
    data object Error : PrivateExtensionInstallResult()
}
