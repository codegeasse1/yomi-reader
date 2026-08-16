package eu.kanade.tachiyomi.data.shikimori

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Global request spacing for Shikimori REST API during bulk list fetch.
 */
class ShikimoriApiRateLimiter(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
) {
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun <T> withRateLimit(block: suspend () -> T): T {
        return mutex.withLock {
            val wait = minIntervalMs - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0) delay(wait)
            try {
                block()
            } finally {
                lastRequestAt = System.currentTimeMillis()
            }
        }
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 500L
        const val FETCH_CONCURRENCY = 4
    }
}
