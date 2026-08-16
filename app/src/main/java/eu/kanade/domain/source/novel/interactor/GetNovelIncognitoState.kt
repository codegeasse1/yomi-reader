package eu.kanade.domain.source.novel.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.interactor.IncognitoStateLogic
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class GetNovelIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: SourcePreferences,
    private val extensionManager: NovelExtensionManager,
) {
    fun await(sourceId: Long?): Boolean {
        if (basePreferences.incognitoMode().get()) return true
        if (sourceId == null) return false
        val pluginId = extensionManager.getPluginId(sourceId) ?: return false
        return IncognitoStateLogic.resolve(
            globalIncognito = false,
            policy = sourcePreferences.incognitoPolicy().get(),
            isNsfw = extensionManager.isNsfwForSource(sourceId),
            inExtensionSet = pluginId in sourcePreferences.incognitoNovelExtensions().get(),
        )
    }

    fun subscribe(sourceId: Long?): Flow<Boolean> {
        if (sourceId == null) return basePreferences.incognitoMode().changes()
        return combine(
            basePreferences.incognitoMode().changes(),
            sourcePreferences.incognitoPolicy().changes(),
            sourcePreferences.incognitoNovelExtensions().changes(),
            extensionManager.getPluginIdAsFlow(sourceId),
            extensionManager.isNsfwForSourceAsFlow(sourceId),
        ) { globalIncognito, policy, incognitoExtensions, pluginId, isNsfw ->
            IncognitoStateLogic.resolve(
                globalIncognito = globalIncognito,
                policy = policy,
                isNsfw = isNsfw,
                inExtensionSet = pluginId != null && pluginId in incognitoExtensions,
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
