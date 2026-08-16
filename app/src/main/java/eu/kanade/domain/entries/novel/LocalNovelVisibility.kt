package eu.kanade.domain.entries.novel

import tachiyomi.source.local.entries.novel.LocalNovelSource

/**
 * Hub/history visibility for local-novel entries.
 *
 * Hides ghosts that still sit in novel DB/history after the on-disk entry
 * under `localnovel/` is gone, or when the cover clearly points at manga
 * `local/` storage (cross-contamination with LocalMangaSource).
 */
object LocalNovelVisibility {

    fun isLocalSource(sourceId: Long): Boolean = sourceId == LocalNovelSource.ID

    /**
     * @param hasSupportedContent FS check for [url]; only called for local sources
     * when the cover is not already known to be a foreign manga-local path.
     */
    fun shouldShowLocalNovelEntry(
        sourceId: Long,
        url: String?,
        coverUrl: String?,
        hasSupportedContent: (entryUrl: String) -> Boolean,
    ): Boolean {
        if (!isLocalSource(sourceId)) return true
        if (LocalNovelIntegrity.isMangaLocalStorageCoverUrl(coverUrl)) return false
        if (url.isNullOrBlank()) return false
        return hasSupportedContent(url)
    }

    /**
     * History rows may only have cover + sourceId until the novel url is loaded.
     * Foreign manga-local covers are sufficient to hide immediately.
     */
    fun shouldHideLocalHistoryByCover(sourceId: Long, coverUrl: String?): Boolean {
        return isLocalSource(sourceId) && LocalNovelIntegrity.isMangaLocalStorageCoverUrl(coverUrl)
    }
}
