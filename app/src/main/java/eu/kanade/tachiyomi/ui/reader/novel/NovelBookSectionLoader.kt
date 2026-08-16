package eu.kanade.tachiyomi.ui.reader.novel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of preparing a single book-mode section.
 *
 * Sections are addressed by their spine index (the `sectionKey`), never by a chapter id: one block
 * can hold two chapters and one chapter can span several blocks, so a chapter id is not a unique
 * section address.
 */
internal sealed interface NovelBookSectionResult {
    val sectionKey: Long

    data class Ready(
        override val sectionKey: Long,
        val html: String,
        val baseUrl: String? = null,
    ) : NovelBookSectionResult

    data class Failed(override val sectionKey: Long, val message: String?) : NovelBookSectionResult
}

/**
 * Loads ready-to-insert section HTML for book mode.
 *
 * The loader is intentionally free of Android and repository dependencies: the caller supplies a
 * [fetchSectionHtml] lambda that resolves (download/network/cache) and normalizes a section into
 * reader-ready HTML. Results are stored in [store], which keeps a small resident set in memory and
 * the rest on disk.
 *
 * Concurrent requests for the same section share a single load, and the number of parallel loads is
 * capped so that prefetching never starves the section the reader is actually waiting for.
 */
internal class NovelBookSectionLoader(
    private val store: NovelBookSectionStore,
    private val maxConcurrentLoads: Int = DEFAULT_MAX_CONCURRENT_LOADS,
    private val fetchSectionBaseUrl: (Long) -> String? = { null },
    private val fetchSectionHtml: suspend (Long) -> String,
) {
    private val semaphore = Semaphore(maxConcurrentLoads.coerceAtLeast(1))
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<Long, CompletableDeferred<NovelBookSectionResult>>()

    /**
     * Per section load order.
     *
     * A forced reload deliberately runs next to the attempt it replaces, so both loads have to be
     * serialized: otherwise the older fetch could finish last and write its stale HTML into the store
     * on top of the fresh one.
     */
    private val sectionLocks = ConcurrentHashMap<Long, Mutex>()
    private val failed = mutableSetOf<Long>()

    val inFlightSectionKeys: Set<Long>
        get() = inFlight.keys.toSet()

    val failedSectionKeys: Set<Long>
        get() = failed.toSet()

    fun isPrepared(sectionKey: Long): Boolean = store.isPrepared(sectionKey)

    fun preparedHtml(sectionKey: Long): String? = store.get(sectionKey)

    fun release(sectionKey: Long) {
        store.release(sectionKey)
    }

    /**
     * Forgets a prepared section in memory and on disk, so the next [prepare] rebuilds it.
     *
     * Used when the section markup itself became invalid (rendering variant changed, book rebuilt),
     * which [release] must not do because it also runs for ordinary window pruning.
     */
    fun invalidate(sectionKey: Long) {
        store.invalidate(sectionKey)
    }

    fun clear() {
        store.clear()
        failed.clear()
    }

    /**
     * Ensures the section with spine index [sectionKey] is prepared and returns its HTML.
     *
     * Already prepared sections resolve immediately from memory or disk unless [forceReload] is set.
     */
    suspend fun prepare(sectionKey: Long, forceReload: Boolean = false): NovelBookSectionResult {
        if (!forceReload) {
            val cached = cachedResult(sectionKey)
            if (cached != null) {
                mutex.withLock { failed.remove(sectionKey) }
                return cached
            }
            val alreadyRunning = mutex.withLock { inFlight[sectionKey] }
            if (alreadyRunning != null) return alreadyRunning.await()
        } else {
            // A forced reload must neither join the attempt it is meant to replace nor be answered
            // by the copy that attempt already stored, so the cached section goes first.
            store.invalidate(sectionKey)
        }
        val pending = CompletableDeferred<NovelBookSectionResult>()
        val running = mutex.withLock {
            val current = inFlight[sectionKey]
            if (current == null || forceReload) {
                inFlight[sectionKey] = pending
                failed.remove(sectionKey)
                null
            } else {
                current
            }
        }
        if (running != null) return running.await()
        var result: NovelBookSectionResult? = null
        try {
            result = try {
                semaphore.withPermit {
                    sectionLock(sectionKey).withLock {
                        val cached = if (forceReload) null else cachedResult(sectionKey)
                        cached ?: fetchAndStore(sectionKey)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NovelBookSectionResult.Failed(sectionKey, e.message)
            }
            return result
        } finally {
            val settled = result
            withContext(NonCancellable) {
                mutex.withLock {
                    // Only the newest attempt owns the entry: a forced reload replaced it on purpose.
                    if (inFlight[sectionKey] === pending) {
                        inFlight.remove(sectionKey)
                    }
                    when {
                        settled == null -> Unit
                        settled is NovelBookSectionResult.Failed -> failed.add(sectionKey)
                        else -> failed.remove(sectionKey)
                    }
                }
            }
            if (settled != null) {
                pending.complete(settled)
            } else {
                pending.cancel()
            }
        }
    }

    suspend fun retry(sectionKey: Long): NovelBookSectionResult = prepare(sectionKey, forceReload = true)

    private suspend fun fetchAndStore(sectionKey: Long): NovelBookSectionResult {
        val html = fetchSectionHtml(sectionKey)
        if (html.isBlank()) return NovelBookSectionResult.Failed(sectionKey, EMPTY_SECTION_MESSAGE)
        val baseUrl = fetchSectionBaseUrl(sectionKey)
        store.put(sectionKey, html, baseUrl = baseUrl)
        return NovelBookSectionResult.Ready(sectionKey, html, baseUrl)
    }

    private fun cachedResult(sectionKey: Long): NovelBookSectionResult.Ready? =
        store.getPrepared(sectionKey)?.let { prepared ->
            NovelBookSectionResult.Ready(
                sectionKey = sectionKey,
                html = prepared.html,
                baseUrl = prepared.baseUrl,
            )
        }

    private fun sectionLock(sectionKey: Long): Mutex = sectionLocks.getOrPut(sectionKey) { Mutex() }

    companion object {
        const val DEFAULT_MAX_CONCURRENT_LOADS = 2
        const val EMPTY_SECTION_MESSAGE = "Chapter content is empty"
    }
}
