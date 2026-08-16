package eu.kanade.tachiyomi.extension

/**
 * Whether a privately installed extension may be replaced in place by a new APK.
 *
 * A private extension lives in the app's own directory and is loaded without the system verifying
 * anything, so the signature is the only thing tying an update to whoever published the installed
 * copy. Replacing across signing keys would let any store hand out a build that inherits the
 * installed extension's identity - and its stored trust.
 *
 * Legitimate cross-store re-publication is not blocked by this: the manager routes those variants
 * through an uninstall + install (reinstall), where there is no installed copy left to replace.
 */
internal fun canReplacePrivateExtension(
    installedVersionCode: Long,
    newVersionCode: Long,
    installedSignatures: List<String>,
    newSignatures: List<String>,
): Boolean {
    if (newVersionCode < installedVersionCode) return false
    if (newSignatures.isEmpty()) return false
    return newSignatures.containsAll(installedSignatures)
}
