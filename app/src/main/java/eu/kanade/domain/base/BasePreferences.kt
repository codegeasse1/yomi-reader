package eu.kanade.domain.base

import android.content.Context
import android.content.pm.PackageManager
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.util.system.GLUtil
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.MR

class BasePreferences(
    val context: Context,
    private val preferenceStore: PreferenceStore,
) {

    fun downloadedOnly() = preferenceStore.getBoolean(
        Preference.appStateKey("pref_downloaded_only"),
        false,
    )

    fun incognitoMode() = preferenceStore.getBoolean(Preference.appStateKey("incognito_mode"), false)

    fun extensionInstaller() = ExtensionInstallerPreference(context, preferenceStore)

    /**
     * Silent background updates are only possible with the private installer, so the toggle is
     * gated on it in the settings UI.
     */
    fun autoUpdateExtensions() = preferenceStore.getBoolean("pref_auto_update_extensions", false)

    /**
     * Queue of pending APK installs waiting for the "Install unknown apps" permission; replaces
     * the single-slot legacy prefs below, which are kept for migration reads only.
     */
    fun pendingApkInstallQueue() = preferenceStore.getStringSet(
        Preference.appStateKey("pending_apk_install_queue"),
        emptySet(),
    )

    fun pendingApkInstallPackage() = preferenceStore.getString(
        Preference.appStateKey("pending_apk_install_package"),
        "",
    )

    fun pendingApkInstallDisplayName() = preferenceStore.getString(
        Preference.appStateKey("pending_apk_install_display_name"),
        "",
    )

    fun pendingApkInstallPath() = preferenceStore.getString(Preference.appStateKey("pending_apk_install_path"), "")

    fun pendingApkInstallKind() = preferenceStore.getString(Preference.appStateKey("pending_apk_install_kind"), "")

    fun pendingApkInstallBackend() = preferenceStore.getString(
        Preference.appStateKey("pending_apk_install_backend"),
        "",
    )

    fun lastExtensionApkPackage() = preferenceStore.getString(Preference.appStateKey("last_extension_apk_package"), "")

    /**
     * Persisted "downloadId|packageName" pairs for DownloadManager downloads that have not
     * reached a terminal state, so a process restart can resume installing finished ones.
     */
    fun extensionActiveDownloads() = preferenceStore.getStringSet(
        Preference.appStateKey("extension_active_downloads"),
        emptySet(),
    )

    fun lastExtensionApkDisplayName() = preferenceStore.getString(
        Preference.appStateKey("last_extension_apk_display_name"),
        "",
    )

    fun lastExtensionApkPath() = preferenceStore.getString(Preference.appStateKey("last_extension_apk_path"), "")

    fun lastExtensionApkKind() = preferenceStore.getString(Preference.appStateKey("last_extension_apk_kind"), "")

    fun deviceHasPip() = context.packageManager.hasSystemFeature(
        PackageManager.FEATURE_PICTURE_IN_PICTURE,
    )

    fun shownOnboardingFlow() = preferenceStore.getBoolean(Preference.appStateKey("onboarding_complete"), false)

    enum class ExtensionInstaller(val titleRes: StringResource, val requiresSystemPermission: Boolean) {
        LEGACY(MR.strings.ext_installer_legacy, true),
        PACKAGEINSTALLER(MR.strings.ext_installer_packageinstaller, true),
        SHIZUKU(MR.strings.ext_installer_shizuku, false),
        DHIZUKU(MR.strings.ext_installer_dhizuku, false),
        PRIVATE(MR.strings.ext_installer_private, false),
    }

    fun displayProfile() = preferenceStore.getString("pref_display_profile_key", "")

    fun hardwareBitmapThreshold() = preferenceStore.getInt("pref_hardware_bitmap_threshold", GLUtil.SAFE_TEXTURE_LIMIT)

    fun alwaysDecodeLongStripWithSSIV() = preferenceStore.getBoolean("pref_always_decode_long_strip_with_ssiv", false)
}
