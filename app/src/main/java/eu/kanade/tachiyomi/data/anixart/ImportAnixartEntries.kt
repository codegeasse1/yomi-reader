package eu.kanade.tachiyomi.data.anixart

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.tachiyomi.animesource.model.SAnime
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anixart.AnixartImportPlanner
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime

/**
 * Executes an [AnixartImportPlanner.Plan] against the real library interactors.
 *
 * Safety model (mirrors the rest of the importer and the achievement recompute):
 *  - MONOTONIC: an anime already in the library keeps its existing categories;
 *    imported categories are MERGED in, never replacing the user's setup.
 *  - Idempotent: re-running over the same export adds nothing new.
 *  - status/score are intentionally projected onto categories only — Aniyomi has
 *    no local status/score field (those belong to trackers).
 */
class ImportAnixartEntries(
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId,
    private val updateAnime: UpdateAnime,
    private val getAnimeCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
) {

    data class Report(
        val added: Int,
        val alreadyInLibrary: Int,
        val failed: Int,
    )

    suspend fun await(
        plan: AnixartImportPlanner.Plan,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Report {
        var added = 0
        var alreadyInLibrary = 0
        var failed = 0
        val total = plan.actions.size

        plan.actions.forEachIndexed { i, action ->
            onProgress(i + 1, total)
            try {
                val candidate = action.candidate
                val existing = getAnimeByUrlAndSourceId.await(candidate.url, candidate.sourceId)
                val wasInLibrary = existing?.favorite == true

                // Persist (or fetch) the local anime.
                val sAnime = SAnime.create().apply {
                    url = candidate.url
                    title = candidate.displayTitle
                    thumbnail_url = candidate.thumbnailUrl
                }
                val localAnime = networkToLocalAnime.await(
                    sAnime.toDomainAnime(candidate.sourceId),
                )

                if (!localAnime.favorite) {
                    updateAnime.awaitUpdateFavorite(localAnime.id, favorite = true)
                }

                if (action.categoryIds.isNotEmpty()) {
                    // MERGE: keep whatever categories the entry already had.
                    val current = getAnimeCategories.await(localAnime.id).map { it.id }.toSet()
                    val merged = current + action.categoryIds
                    if (merged != current) {
                        setAnimeCategories.await(localAnime.id, merged.toList())
                    }
                }

                if (wasInLibrary) alreadyInLibrary++ else added++
            } catch (e: Exception) {
                failed++
                logcat(LogPriority.ERROR, e) { "Anixart import failed for '${action.candidate.displayTitle}'" }
            }
        }
        return Report(added = added, alreadyInLibrary = alreadyInLibrary, failed = failed)
    }
}
