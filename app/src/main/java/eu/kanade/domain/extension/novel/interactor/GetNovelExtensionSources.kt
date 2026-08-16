package eu.kanade.domain.extension.novel.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.NovelPluginId
import eu.kanade.tachiyomi.extension.novel.runtime.NovelPluginIdentitySource
import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.source.novel.service.NovelSourceManager

class GetNovelExtensionSources(
    private val preferences: SourcePreferences,
    private val sourceManager: NovelSourceManager,
) {

    fun subscribe(extension: NovelPlugin.Installed): Flow<List<NovelExtensionSourceItem>> {
        return combine(
            preferences.disabledNovelSources().changes(),
            sourceManager.catalogueSources,
        ) { disabledSources, catalogueSources ->
            val extensionSources = catalogueSources.filter { source ->
                (source as? NovelPluginIdentitySource)?.pluginId == extension.id
            }.takeIf { it.isNotEmpty() }
                ?: run {
                    val sourceId = NovelPluginId.toSourceId(extension.id)
                    listOf(catalogueSources.firstOrNull { it.id == sourceId } ?: sourceManager.getOrStub(sourceId))
                }

            extensionSources.map { source ->
                NovelExtensionSourceItem(
                    source = source,
                    enabled = source.id.toString() !in disabledSources,
                    labelAsName = false,
                )
            }
        }
    }
}

data class NovelExtensionSourceItem(
    val source: NovelSource,
    val enabled: Boolean,
    val labelAsName: Boolean,
)
