package eu.kanade.tachiyomi.data.shikimori

import tachiyomi.data.shikimori.ShikimoriImportMediaType
import tachiyomi.data.shikimori.ShikimoriImportPlanner

class ImportShikimoriExecutor(
    private val importAnimeEntries: ImportShikimoriEntries,
    private val importMangaEntries: ImportShikimoriMangaEntries,
    private val importNovelEntries: ImportShikimoriNovelEntries,
) {

    data class Report(
        val added: Int,
        val alreadyInLibrary: Int,
        val failed: Int,
        val trackerBound: Int,
    )

    suspend fun await(
        mediaType: ShikimoriImportMediaType,
        plan: ShikimoriImportPlanner.Plan,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Report {
        return when (mediaType) {
            ShikimoriImportMediaType.ANIME -> {
                val actions = plan.actions.map {
                    ImportShikimoriEntries.Action(it.entry, it.candidate, it.categoryIds)
                }
                val raw = importAnimeEntries.await(actions, onProgress)
                Report(raw.added, raw.alreadyInLibrary, raw.failed, raw.trackerBound)
            }
            ShikimoriImportMediaType.MANGA -> {
                val actions = plan.actions.map {
                    ImportShikimoriMangaEntries.Action(it.entry, it.candidate, it.categoryIds)
                }
                val raw = importMangaEntries.await(actions, onProgress)
                Report(raw.added, raw.alreadyInLibrary, raw.failed, raw.trackerBound)
            }
            ShikimoriImportMediaType.RANOBE -> {
                val actions = plan.actions.map {
                    ImportShikimoriNovelEntries.Action(it.entry, it.candidate, it.categoryIds)
                }
                val raw = importNovelEntries.await(actions, onProgress)
                Report(raw.added, raw.alreadyInLibrary, raw.failed, raw.trackerBound)
            }
        }
    }
}
