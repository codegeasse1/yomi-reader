package eu.kanade.tachiyomi.ui.reader.novel

import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a spine section into the markup book mode appends to the reader document.
 *
 * The transformation chain itself lives in [BookSectionRepository]; this class only maps a spine
 * index to a chapter, remembers the base URL that chapter resolved to and hands the section over to
 * the repository. Sections are addressed by their spine index, never by a chapter id, because a
 * section is not the same thing as a chapter.
 */
internal class NovelBookSectionHtmlResolver(
    private val currentSpine: () -> NovelBookSpine,
    private val repository: BookSectionRepository,
) {

    private val resolvedBaseUrls = ConcurrentHashMap<Long, String>()

    fun resolvedBaseUrl(sectionKey: Long): String? = resolvedBaseUrls[sectionKey]

    /**
     * Resolves the section HTML for the section with spine index [sectionKey]. Returns a blank
     * string when the section has no usable content, which the loader reports as a failed section
     * so it can be retried inline.
     */
    suspend fun resolve(sectionKey: Long): String {
        val section = currentSpine().sectionAt(sectionKey.toInt()) ?: return ""
        val content = repository.prepareChapterSection(section)
        content.baseUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { resolvedBaseUrls[sectionKey] = it }
            ?: resolvedBaseUrls.remove(sectionKey)
        return content.html
    }
}
