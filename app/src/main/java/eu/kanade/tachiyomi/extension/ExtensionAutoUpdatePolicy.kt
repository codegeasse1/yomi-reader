package eu.kanade.tachiyomi.extension

import eu.kanade.domain.base.BasePreferences

/**
 * A background update may only run when it can complete without any user interaction and without
 * changing how the extension is installed.
 *
 * - Every installer other than [BasePreferences.ExtensionInstaller.PRIVATE] either shows a system
 *   dialog or delegates to a service the user has to confirm, so it cannot update silently.
 * - A shared (system installed) extension cannot be replaced privately: doing so would leave a
 *   second copy of the same package behind. Those updates stay manual even when the toggle is on.
 */
fun canAutoUpdateExtension(
    autoUpdateEnabled: Boolean,
    installer: BasePreferences.ExtensionInstaller,
    isSharedInstall: Boolean,
): Boolean {
    return autoUpdateEnabled &&
        installer == BasePreferences.ExtensionInstaller.PRIVATE &&
        !isSharedInstall
}
