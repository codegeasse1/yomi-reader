package eu.kanade.tachiyomi.data.shikimori

import eu.kanade.tachiyomi.data.anixart.AnixartSourceRateLimiter
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartMatchingCoordinator
import tachiyomi.data.anixart.AnixartTitleSearcher
import tachiyomi.domain.source.manga.service.MangaSourceManager

class ShikimoriMangaSourceSearcher(
    private val sourceManager: MangaSourceManager,
    private val sourceIds: List<Long>,
    private val rateLimiter: AnixartSourceRateLimiter = AnixartSourceRateLimiter(),
    private val maxResultsPerSource: Int = MAX_RESULTS_PER_SOURCE,
    private val sourceTimeoutMs: Long = CatalogueExtensionSearch.SOURCE_TIMEOUT_MS,
) : AnixartTitleSearcher {

    override suspend fun search(query: String): List<AnixartMatcher.SearchCandidate> {
        if (query.isBlank()) return emptyList()
        return coroutineScope {
            sourceIds.map { sourceId ->
                async { searchOnSource(sourceId, query) }
            }
                .awaitAll()
                .flatten()
                .let { AnixartMatchingCoordinator.dedupCandidates(it) }
        }
    }

    private suspend fun searchOnSource(
        sourceId: Long,
        query: String,
    ): List<AnixartMatcher.SearchCandidate> {
        val source = sourceManager.get(sourceId) as? CatalogueSource ?: return emptyList()
        return try {
            rateLimiter.withRateLimit(sourceId) {
                CatalogueExtensionSearch.onExtensionThread {
                    withTimeout(sourceTimeoutMs) {
                        val filters = CatalogueExtensionSearch.safeFilterList { source.getFilterList() }
                        val page = source.getSearchManga(1, query, filters)
                        page.mangas.take(maxResultsPerSource).map { sManga ->
                            AnixartMatcher.SearchCandidate(
                                id = (sourceId.toString() + sManga.url).hashCode().toLong(),
                                sourceId = sourceId,
                                displayTitle = sManga.title,
                                titles = listOf(sManga.title).filter { it.isNotBlank() },
                                url = sManga.url,
                                thumbnailUrl = sManga.thumbnail_url,
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Shikimori manga search failed on source $sourceId for '$query'" }
            emptyList()
        }
    }

    companion object {
        const val MAX_RESULTS_PER_SOURCE = 10
    }
}
