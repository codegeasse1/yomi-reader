package eu.kanade.tachiyomi.util.manga

import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.toSMangaUpdateRequest
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object MangaMemoRepairHelper {
    /**
     * Ensures that [manga]'s request object has a non-empty `memo` for sources that rely on it (e.g. 1.6 extensions).
     * If [manga.memo] is empty in the database due to legacy persistence bugs, this helper performs a 1-time search
     * to recover the source-provided `memo` and persists it to the database so future calls stay fast and self-healed.
     */
    suspend fun getOrRepairMangaRequest(
        manga: Manga,
        source: MangaSource,
        updateManga: UpdateManga,
    ): SManga {
        val request = manga.toSMangaUpdateRequest()
        if (request.memo.isNotEmpty()) return request

        // memo is an extensions-lib 1.6 concept: 1.4/1.5 sources never populate it, so searching
        // for every empty-memo manga would add a catalogue request to every open/library update.
        val libVersion = Injekt.get<MangaExtensionManager>().getLibVersionForSource(manga.source) ?: return request
        if (libVersion < 1.6) return request

        val catalogueSource = source as? CatalogueSource ?: return request

        return try {
            withContext(Dispatchers.IO) {
                logcat(LogPriority.INFO) {
                    "Attempting memo repair for manga '${manga.title}' (id=${manga.id}) from source '${source.name}'"
                }
                val searchResult = catalogueSource.getSearchManga(1, manga.title, FilterList())
                val matched = searchResult.mangas.firstOrNull { candidate ->
                    candidate.url == manga.url ||
                        (candidate.url.startsWith("/") && manga.url.endsWith(candidate.url)) ||
                        (manga.url.startsWith("/") && candidate.url.endsWith(manga.url))
                } ?: searchResult.mangas.firstOrNull { candidate ->
                    // Some sources return a slightly different URL than what is stored (e.g. a
                    // rotating id). A title match is still a strong signal: 1.6 sources derive
                    // their memo (e.g. the slug) from the title anyway.
                    candidate.title.equals(manga.title, ignoreCase = true)
                }

                if (matched != null && matched.memo.isNotEmpty()) {
                    logcat(LogPriority.INFO) { "Successfully repaired memo for '${manga.title}'" }
                    updateManga.await(MangaUpdate(id = manga.id, memo = matched.memo))
                    manga.copy(memo = matched.memo).toSMangaUpdateRequest()
                } else {
                    request
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "Failed memo repair for manga '${manga.title}'" }
            request
        }
    }
}
