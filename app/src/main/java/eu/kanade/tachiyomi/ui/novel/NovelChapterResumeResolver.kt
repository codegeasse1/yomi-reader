package eu.kanade.tachiyomi.ui.novel

import tachiyomi.domain.book.novel.model.NovelBookState
import tachiyomi.domain.items.novelchapter.model.NovelChapter

internal val novelReadingOrderComparator =
    compareBy<NovelChapter> { it.chapterNumber }
        .thenBy { it.sourceOrder }
        .thenBy { it.id }

internal fun List<NovelChapter>.sortedByNovelReadingOrder(): List<NovelChapter> {
    return sortedWith(novelReadingOrderComparator)
}

internal fun resolveNovelResumeChapter(
    chapters: List<NovelChapter>,
    fromChapterId: Long? = null,
): NovelChapter? = resolveNovelResumeChapter(chapters, fromChapterId, bookState = null)

/**
 * Resolves the chapter a "continue reading" action should open.
 *
 * When the title is read as a compiled book, the reading position lives in [NovelBookState] as a
 * whole-book character offset and the chapter column only carries the encoded book location. The
 * per-chapter heuristics below cannot read either, so the book's [NovelBookState.lastChapterId] wins
 * and the reader opens the book exactly there. Titles without a book fall back to the per-chapter
 * logic.
 */
internal fun resolveNovelResumeChapter(
    chapters: List<NovelChapter>,
    fromChapterId: Long?,
    bookState: NovelBookState?,
): NovelChapter? {
    if (bookState?.enabled == true) {
        bookState.lastChapterId?.let { bookChapterId ->
            chapters.firstOrNull { it.id == bookChapterId }?.let { return it }
        }
    }

    val sortedChapters = chapters.sortedByNovelReadingOrder()
    if (sortedChapters.isEmpty()) return null

    if (fromChapterId != null) {
        val currentIndex = sortedChapters.indexOfFirst { it.id == fromChapterId }
        if (currentIndex >= 0) {
            return sortedChapters[currentIndex]
        }
    }

    sortedChapters.firstOrNull { it.lastPageRead > 0L && !it.read }?.let { return it }

    val lastReadIndex = sortedChapters.indexOfLast { it.read || it.lastPageRead > 0L }
    if (lastReadIndex >= 0) {
        sortedChapters.drop(lastReadIndex + 1).firstOrNull { !it.read }?.let { return it }
        return sortedChapters[lastReadIndex]
    }

    return sortedChapters.firstOrNull { !it.read } ?: sortedChapters.firstOrNull()
}
