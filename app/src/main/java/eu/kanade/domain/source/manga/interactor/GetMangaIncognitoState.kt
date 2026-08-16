package eu.kanade.domain.source.manga.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.interactor.IncognitoStateLogic
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetMangaIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val extensionManager: MangaExtensionManager,
) {
    fun await(sourceId: Long?): Boolean {
        if (basePreferences.incognitoMode().get()) return true
        if (sourceId == null) return false
        val extensionPackage = extensionManager.getExtensionPackage(sourceId) ?: return false
        return IncognitoStateLogic.resolve(
            globalIncognito = false,
            policy = sourcePreferences.incognitoPolicy().get(),
            isNsfw = extensionManager.isNsfwForSource(sourceId),
            inExtensionSet = extensionPackage in sourcePreferences.incognitoMangaExtensions().get(),
        )
    }

    fun subscribe(sourceId: Long?): Flow<Boolean> {
        if (sourceId == null) return basePreferences.incognitoMode().changes()
        return combine(
            basePreferences.incognitoMode().changes(),
            sourcePreferences.incognitoPolicy().changes(),
            sourcePreferences.incognitoMangaExtensions().changes(),
            extensionManager.getExtensionPackageAsFlow(sourceId),
            extensionManager.isNsfwForSourceAsFlow(sourceId),
        ) { globalIncognito, policy, incognitoExtensions, extensionPackage, isNsfw ->
            IncognitoStateLogic.resolve(
                globalIncognito = globalIncognito,
                policy = policy,
                isNsfw = isNsfw,
                inExtensionSet = extensionPackage != null && extensionPackage in incognitoExtensions,
            )
        }
            .distinctUntilChanged()
    }

    /**
     * Whether reading history and progress should be paused.
     * Library entries are always tracked unless global incognito is on.
     */
    fun shouldPauseHistory(sourceId: Long?, inLibrary: Boolean): Boolean {
        if (basePreferences.incognitoMode().get()) return true
        if (inLibrary) return false
        return await(sourceId)
    }
}
