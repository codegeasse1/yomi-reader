package eu.kanade.tachiyomi.data.shikimori

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMEntry
import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.shikimori.ShikimoriImportEntry
import tachiyomi.data.shikimori.ShikimoriImportMediaType
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class FetchShikimoriImportEntries(
    private val trackerManager: TrackerManager,
    private val rateLimiter: ShikimoriApiRateLimiter = ShikimoriApiRateLimiter(),
) {

    class NotLoggedInException : Exception()

    class NetworkException(cause: Throwable? = null) : Exception(cause)

    class RateLimitedException : Exception()

    suspend fun await(mediaType: ShikimoriImportMediaType): List<ShikimoriImportEntry> {
        val shikimori = trackerManager.shikimori
        if (!shikimori.isLoggedIn) throw NotLoggedInException()

        return try {
            val userId = shikimori.api.getCurrentUser()
            when (mediaType) {
                ShikimoriImportMediaType.ANIME -> fetchAnime(shikimori.api, userId)
                ShikimoriImportMediaType.MANGA -> fetchManga(shikimori.api, userId, ranobeOnly = false)
                ShikimoriImportMediaType.RANOBE -> fetchManga(shikimori.api, userId, ranobeOnly = true)
            }
        } catch (e: NotLoggedInException) {
            throw e
        } catch (e: RateLimitedException) {
            throw e
        } catch (e: NetworkException) {
            throw e
        } catch (e: HttpException) {
            if (e.code == 429) throw RateLimitedException()
            throw NetworkException(e)
        } catch (e: IOException) {
            throw NetworkException(e)
        } catch (e: Exception) {
            throw NetworkException(e)
        }
    }

    private suspend fun fetchAnime(api: ShikimoriApi, userId: Int): List<ShikimoriImportEntry> {
        val rates = api.getAllUserAnimeRates(userId)
        val targetIds = rates.map { it.targetId }.distinct()

        // Bulk first (one request per 50 ids). /animes?ids= silently omits some entries
        // (restricted kinds, deleted titles), and the old code dropped every rate that
        // was missing from the bulk answer without a word — an anime list could come
        // back mostly or entirely empty and look like "import is broken". Anything the
        // bulk call skipped is now fetched individually, exactly like the manga path.
        val animeById = targetIds
            .chunked(BULK_CHUNK_SIZE)
            .flatMap { chunk -> rateLimiter.withRateLimit { api.getAnimesByIds(chunk) } }
            .associateBy { it.id }
            .toMutableMap()
        val stillMissing = targetIds.filter { it !in animeById }
        if (stillMissing.isNotEmpty()) {
            animeById.putAll(fetchByIdsParallel(stillMissing) { api.getAnimeById(it) })
        }

        val entries = rates.mapNotNull { rate ->
            val anime = animeById[rate.targetId] ?: return@mapNotNull null
            ShikimoriImportEntry(
                mediaType = ShikimoriImportMediaType.ANIME,
                rateId = rate.id,
                remoteId = rate.targetId,
                name = anime.name,
                russian = anime.russian,
                status = rate.status,
                score = rate.score,
                progress = rate.episodes,
                totalCount = anime.episodes,
                thumbnailUrl = ShikimoriApi.BASE_URL + anime.image.original,
            )
        }
        logUnresolved("anime", rates.size, entries.size)
        return entries
    }

    private suspend fun fetchManga(
        api: ShikimoriApi,
        userId: Int,
        ranobeOnly: Boolean,
    ): List<ShikimoriImportEntry> {
        val rates = api.getAllUserMangaRates(userId)
        val targetIds = rates.map { it.targetId }.distinct()

        // Bulk-first for BOTH manga and ranobe: /mangas?ids= costs one request
        // per 50 entries. Ids missing from the bulk response (ranobe kinds are
        // often excluded from it) are fetched individually as a fallback,
        // instead of doing one request per entry upfront for ranobe.
        val bulk = targetIds
            .chunked(BULK_CHUNK_SIZE)
            .flatMap { chunk -> rateLimiter.withRateLimit { api.getMangasByIds(chunk) } }
            .associateBy { it.id }
            .toMutableMap()
        val stillMissing = targetIds.filter { it !in bulk }
        if (stillMissing.isNotEmpty()) {
            bulk.putAll(fetchByIdsParallel(stillMissing) { api.getMangaById(it) })
        }
        val mangaById: Map<Long, SMEntry> = bulk

        val entries = rates.mapNotNull { rate ->
            val manga = mangaById[rate.targetId] ?: return@mapNotNull null
            val isRanobe = ShikimoriImportEntry.isRanobeKind(manga.kind)
            if (ranobeOnly != isRanobe) return@mapNotNull null
            ShikimoriImportEntry(
                mediaType = if (isRanobe) ShikimoriImportMediaType.RANOBE else ShikimoriImportMediaType.MANGA,
                rateId = rate.id,
                remoteId = rate.targetId,
                name = manga.name,
                russian = manga.russian,
                status = rate.status,
                score = rate.score,
                progress = rate.chapters,
                totalCount = manga.chapters,
                thumbnailUrl = ShikimoriApi.BASE_URL + manga.image.original,
                kind = manga.kind,
            )
        }
        return entries
    }

    /**
     * Per-id fallback for entries the bulk endpoint did not return. A single failing id
     * must not abort the whole import, so everything except rate limiting is logged and
     * skipped instead of propagating out of the [coroutineScope] and cancelling the rest.
     */
    private suspend fun fetchByIdsParallel(
        ids: List<Long>,
        fetch: suspend (Long) -> SMEntry,
    ): Map<Long, SMEntry> = coroutineScope {
        if (ids.isEmpty()) return@coroutineScope emptyMap()
        val result = ConcurrentHashMap<Long, SMEntry>()
        val semaphore = Semaphore(ShikimoriApiRateLimiter.FETCH_CONCURRENCY)
        ids.map { id ->
            async {
                semaphore.withPermit {
                    try {
                        rateLimiter.withRateLimit {
                            val entry = fetch(id)
                            result[entry.id] = entry
                        }
                    } catch (e: HttpException) {
                        if (e.code == 429) throw RateLimitedException()
                        logcat(LogPriority.WARN, e) { "Shikimori entry fetch failed for id=$id" }
                    } catch (e: IOException) {
                        logcat(LogPriority.WARN, e) { "Shikimori entry fetch failed for id=$id" }
                    }
                }
            }
        }.awaitAll()
        result
    }

    private fun logUnresolved(kind: String, rateCount: Int, entryCount: Int) {
        if (entryCount >= rateCount) return
        logcat(LogPriority.WARN) {
            "Shikimori $kind import: ${rateCount - entryCount} of $rateCount list entries " +
                "could not be resolved and were skipped"
        }
    }

    companion object {
        private const val BULK_CHUNK_SIZE = 50
    }
}
