package eu.kanade.tachiyomi.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Small in-memory TTL cache used to avoid duplicate source-detail fetches when a title screen
 * is re-opened shortly after a refresh. Bounded to [maxSize] entries (evicts the oldest) so a
 * single ScreenModel serving many titles (e.g. the novel carousel) cannot grow unboundedly.
 * Thread-safe: reads/writes may happen from any IO coroutine.
 */
class TtlCache<K, V>(
    private val ttlMs: Long,
    private val maxSize: Int = 24,
) {

    private val entries = ConcurrentHashMap<K, Pair<Long, V>>()

    /** Returns the cached value if it is still within the TTL, otherwise removes and returns null. */
    operator fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.first >= ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.second
    }

    /** Stores [value] under [key], evicting the oldest entry when at capacity. */
    fun put(key: K, value: V) {
        if (entries.size >= maxSize && !entries.containsKey(key)) {
            entries.minByOrNull { it.value.first }?.key?.let(entries::remove)
        }
        entries[key] = System.currentTimeMillis() to value
    }

    fun remove(key: K) {
        entries.remove(key)
    }
}
