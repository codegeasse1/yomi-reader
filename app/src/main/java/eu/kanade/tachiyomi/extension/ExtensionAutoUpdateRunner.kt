package eu.kanade.tachiyomi.extension

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Installs pending extension updates without any user interaction.
 *
 * The run is deliberately not tied to app startup: it is started lazily once the app is already
 * usable, after the regular update checks have filled the pending update lists.
 */
class ExtensionAutoUpdateRunner(
    private val basePreferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val mangaExtensionManager: MangaExtensionManager = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
    private val novelExtensionManager: NovelExtensionManagerProvider = NovelExtensionManagerProvider(),
) {

    suspend fun run(context: Context) {
        if (!basePreferences.autoUpdateExtensions().get()) return
        val installer = basePreferences.extensionInstaller().get()
        if (installer != BasePreferences.ExtensionInstaller.PRIVATE) return

        runCatching { updateMangaExtensions(context, installer) }
            .onFailure { logcat(LogPriority.WARN, it) { "Manga extension auto-update failed" } }
        runCatching { updateAnimeExtensions(context, installer) }
            .onFailure { logcat(LogPriority.WARN, it) { "Anime extension auto-update failed" } }
        runCatching { updateNovelExtensions(installer) }
            .onFailure { logcat(LogPriority.WARN, it) { "Novel extension auto-update failed" } }
    }

    private suspend fun updateMangaExtensions(context: Context, installer: BasePreferences.ExtensionInstaller) {
        val installed = mangaExtensionManager.installedExtensionsFlow.first()
        val sharedSkipped = installed.filter { it.hasUpdate && it.isShared }
        val candidates = installed.filter { it.hasUpdate }
            .filter { canAutoUpdateExtension(true, installer, isSharedInstall = it.isShared) }
        if (candidates.isEmpty()) {
            if (sharedSkipped.isNotEmpty()) {
                ExtensionUpdateNotifier(context).notifySharedAutoUpdateSkipped(sharedSkipped.map { it.name })
            }
            return
        }

        val updated = candidates.mapNotNull { extension ->
            val terminalStep = runCatching {
                mangaExtensionManager.updateExtension(extension).first { it.isCompleted() }
            }.getOrNull()
            extension.name.takeIf { terminalStep == InstallStep.Installed }
        }
        if (updated.isEmpty()) {
            if (sharedSkipped.isNotEmpty()) {
                ExtensionUpdateNotifier(context).notifySharedAutoUpdateSkipped(sharedSkipped.map { it.name })
            }
            return
        }

        sourcePreferences.mangaExtensionUpdatesCount()
            .set(mangaExtensionManager.installedExtensionsFlow.first().count { it.hasUpdate })
        ExtensionUpdateNotifier(context).notifyAutoUpdated(updated)
    }

    private suspend fun updateAnimeExtensions(context: Context, installer: BasePreferences.ExtensionInstaller) {
        val installed = animeExtensionManager.installedExtensionsFlow.first()
        val sharedSkipped = installed.filter { it.hasUpdate && it.isShared }
        val candidates = installed.filter { it.hasUpdate }
            .filter { canAutoUpdateExtension(true, installer, isSharedInstall = it.isShared) }
        if (candidates.isEmpty()) {
            if (sharedSkipped.isNotEmpty()) {
                ExtensionUpdateNotifier(context).notifySharedAutoUpdateSkipped(
                    sharedSkipped.map {
                        it.name
                    },
                    anime = true,
                )
            }
            return
        }

        val updated = candidates.mapNotNull { extension ->
            val terminalStep = runCatching {
                animeExtensionManager.updateExtension(extension).first { it.isCompleted() }
            }.getOrNull()
            extension.name.takeIf { terminalStep == InstallStep.Installed }
        }
        if (updated.isEmpty()) {
            if (sharedSkipped.isNotEmpty()) {
                ExtensionUpdateNotifier(context).notifySharedAutoUpdateSkipped(
                    sharedSkipped.map {
                        it.name
                    },
                    anime = true,
                )
            }
            return
        }

        sourcePreferences.animeExtensionUpdatesCount()
            .set(animeExtensionManager.installedExtensionsFlow.first().count { it.hasUpdate })
        ExtensionUpdateNotifier(context).notifyAutoUpdated(updated, anime = true)
    }

    private suspend fun updateNovelExtensions(installer: BasePreferences.ExtensionInstaller) {
        val manager = novelExtensionManager.get() ?: return
        val pending = manager.updatesFlow.first()
        val available = manager.availablePluginsFlow.first()
        val userRepos = sourcePreferences.novelInstalledExtensionRepos().get()
        val candidates = pending.filter { plugin ->
            if (plugin.isKotlinExtension) {
                canAutoUpdateExtension(true, installer, isSharedInstall = false)
            } else {
                // JS plugins are plain files executed inside the app process and carry no
                // signature, so auto-update them only when the plugin comes from a repo the user
                // added and the replacement provides a checksum to verify against.
                plugin.repoUrl in userRepos &&
                    available.any {
                        it.id == plugin.id &&
                            it.versionCode > plugin.versionCode &&
                            it.sha256.isNotBlank()
                    }
            }
        }
        if (candidates.isEmpty()) return

        var updatedAny = false
        candidates.forEach { installed ->
            val replacement = available
                .filter { it.id == installed.id && it.versionCode > installed.versionCode }
                .maxByOrNull { it.versionCode }
                ?: return@forEach
            runCatching { manager.installPlugin(replacement) }
                .onSuccess { updatedAny = true }
                .onFailure { logcat(LogPriority.WARN, it) { "Failed to auto-update novel extension ${installed.id}" } }
        }
        if (!updatedAny) return

        sourcePreferences.novelExtensionUpdatesCount().set(manager.updatesFlow.first().size)
    }

    /**
     * The novel manager is optional in some builds/tests, so resolve it lazily and tolerate absence.
     */
    class NovelExtensionManagerProvider {
        fun get(): NovelExtensionManager? = runCatching { Injekt.get<NovelExtensionManager>() }.getOrNull()
    }
}
