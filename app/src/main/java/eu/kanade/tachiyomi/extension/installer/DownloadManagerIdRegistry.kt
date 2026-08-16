package eu.kanade.tachiyomi.extension.installer

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Internal bridge for legacy DownloadManager-based Manga/Anime flows.
 *
 * Unified installer state should be keyed by packageName. DownloadManager Long ids are stored here
 * only while a system download backend is active.
 */
class DownloadManagerIdRegistry {
    private val idsByPackageName = ConcurrentHashMap<String, Long>()
    private val syntheticIds = AtomicLong(Long.MIN_VALUE)

    fun put(packageName: String, downloadManagerId: Long) {
        idsByPackageName[packageName] = downloadManagerId
    }

    fun get(packageName: String): Long? = idsByPackageName[packageName]

    /**
     * Allocates a negative legacy id for non-DownloadManager downloads.
     *
     * This is a compatibility bridge for existing Manga/Anime state maps that are still keyed by
     * Long ids. It must not be exposed from the unified installer API, which is keyed by packageName.
     */
    fun allocateSyntheticId(packageName: String): Long {
        val id = syntheticIds.getAndIncrement()
        put(packageName, id)
        return id
    }

    fun remove(packageName: String): Long? = idsByPackageName.remove(packageName)

    fun removeByDownloadManagerId(downloadManagerId: Long): String? {
        val packageName = idsByPackageName.entries
            .firstOrNull { it.value == downloadManagerId }
            ?.key
            ?: return null
        idsByPackageName.remove(packageName)
        return packageName
    }

    fun clear() {
        idsByPackageName.clear()
    }
}
