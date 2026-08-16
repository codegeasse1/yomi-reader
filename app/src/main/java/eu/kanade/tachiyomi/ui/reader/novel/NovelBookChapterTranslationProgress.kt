package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Translation progress of one chapter of the open book.
 *
 * Over a book the reader stays in a single session while the text of many chapters passes under the
 * reading position, so a single "is translating / percent" pair cannot describe it: the queue works
 * on one chapter while the reader is already in the next one. The reader keeps one of these per
 * chapter it has seen a queue update for, and the overlay shows the entry of the chapter under the
 * reading position plus how many other chapters are still running in the background.
 */
data class NovelBookChapterTranslationProgress(
    val chapterId: Long,
    val isTranslating: Boolean = false,
    val progress: Int = 0,
    val isDone: Boolean = false,
) {
    val percent: Int get() = progress.coerceIn(0, 100)
}

/** Entry the overlay indicator has to describe, i.e. the chapter under the reading position. */
fun Map<Long, NovelBookChapterTranslationProgress>.overlayIndicatorFor(
    chapterId: Long?,
): NovelBookChapterTranslationProgress? = chapterId?.let { this[it] }

/** Number of other chapters of the book still being translated behind or ahead of the reader. */
fun Map<Long, NovelBookChapterTranslationProgress>.backgroundTranslatingCount(
    activeChapterId: Long?,
): Int = count { (chapterId, entry) -> chapterId != activeChapterId && entry.isTranslating }
