package eu.kanade.tachiyomi.extension

import eu.kanade.domain.base.BasePreferences

/**
 * Which installer backend to use for one install.
 *
 * A privately installed extension lives in the app's own directory and has no system package. If its
 * update went through PackageInstaller or Shizuku instead, the user would end up with a real system
 * package installed *alongside* the private copy, and the loader would then see two builds of the
 * same extension. So a private extension stays private on update, whatever the current preference is.
 */
internal fun resolveExtensionInstaller(
    preferred: BasePreferences.ExtensionInstaller,
    isUpdateForPrivatelyInstalled: Boolean,
): BasePreferences.ExtensionInstaller {
    return if (isUpdateForPrivatelyInstalled) BasePreferences.ExtensionInstaller.PRIVATE else preferred
}
