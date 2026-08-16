package eu.kanade.domain.entries.novel

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Pure helpers for detecting and recovering from "ghost" local-novel rows:
 * DB entries (history/library) that no longer map to supported files under
 * `localnovel/`, often with a cover URI stolen from manga `local/`.
 */
object LocalNovelIntegrity {

    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Local novel chapter lists are filesystem-backed and cheap to rescan.
     * Always re-sync them on open so deleted/unsupported files cannot leave
     * cached empty chapters that [skip-source-refresh] would otherwise keep.
     */
    fun shouldForceLocalChapterResync(isLocalSource: Boolean): Boolean = isLocalSource

    /**
     * Cover URLs that live under manga local storage (`…/local/…`) but not
     * novel local (`…/localnovel/…`) or anime local (`…/localanime/…`).
     *
     * Matches both plain paths and SAF `content://` document IDs
     * (including percent-encoded `/local/`).
     */
    fun isMangaLocalStorageCoverUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        // Remote HTTP covers never come from on-device manga local storage.
        if (url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }

        val candidates = buildList {
            add(url)
            runCatching {
                URLDecoder.decode(url, StandardCharsets.UTF_8.name())
            }.getOrNull()?.let { add(it) }
        }

        return candidates.any { candidate ->
            containsMangaLocalSegment(candidate.replace('\\', '/').lowercase())
        }
    }

    /**
     * Chapter HTML that has no readable text (e.g. LocalNovel empty-body fallback
     * for unsupported formats like PDF). History must not be written for these.
     */
    fun isEmptyChapterHtml(html: String?): Boolean {
        if (html.isNullOrBlank()) return true
        val text = HTML_TAG_REGEX.replace(html, " ")
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        return text.isEmpty()
    }

    fun shouldRecordHistoryForChapterHtml(html: String?): Boolean = !isEmptyChapterHtml(html)

    /**
     * When a local source returns zero chapters, existing DB chapters are ghosts
     * and must be purged (history cascades via FK).
     */
    fun shouldPurgeChaptersOnEmptyLocalSource(
        isLocalSource: Boolean,
        sourceChapterCount: Int,
        cachedChapterCount: Int,
    ): Boolean {
        return isLocalSource && sourceChapterCount == 0 && cachedChapterCount > 0
    }

    /**
     * Thumbnail to persist for a local novel: drop foreign manga-local covers so
     * the entry no longer masquerades as the PDF title art under `local/`.
     */
    fun sanitizeLocalNovelThumbnailUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return if (isMangaLocalStorageCoverUrl(url)) "" else url
    }

    private fun containsMangaLocalSegment(path: String): Boolean {
        var index = 0
        while (index < path.length) {
            val found = path.indexOf("/local", index)
            if (found < 0) return false
            val after = path.substring(found + "/local".length)
            when {
                after.startsWith("novel") || after.startsWith("anime") -> {
                    index = found + 1
                }
                after.isEmpty() ||
                    after.startsWith('/') ||
                    after.startsWith('?') ||
                    after.startsWith('#') ||
                    after.startsWith('%') -> {
                    return true
                }
                else -> index = found + 1
            }
        }
        return false
    }
}
