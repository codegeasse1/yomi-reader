package eu.kanade.tachiyomi.data.shikimori

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Runs catalogue extension network calls on the same dedicated thread pool used by
 * global/browse search, and applies the same per-page timeout as browse.
 */
object CatalogueExtensionSearch {
    private val dispatcher: CoroutineDispatcher =
        Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    const val SOURCE_TIMEOUT_MS = 30_000L

    suspend fun <T> onExtensionThread(block: suspend CoroutineScope.() -> T): T =
        withContext(dispatcher, block)

    fun safeFilterList(load: () -> FilterList): FilterList =
        runCatching { load() }.getOrElse { FilterList() }

    fun safeAnimeFilterList(load: () -> AnimeFilterList): AnimeFilterList =
        runCatching { load() }.getOrElse { AnimeFilterList() }
}
