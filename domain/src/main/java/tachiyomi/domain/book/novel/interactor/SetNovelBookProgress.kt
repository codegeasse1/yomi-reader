package tachiyomi.domain.book.novel.interactor

import tachiyomi.domain.book.novel.repository.NovelBookStateRepository

/**
 * Stores the reading position of a book.
 *
 * The position is a locator: the chapter the reader is inside plus the character offset within that
 * chapter, with the artifact block kept as a lookup hint. [charOffset] is the same position
 * expressed as a whole-book offset and is written along with it so the library progress bar and the
 * novel screen keep a single number to read.
 */
class SetNovelBookProgress(
    private val repository: NovelBookStateRepository,
) {
    suspend fun await(
        novelId: Long,
        charOffset: Long,
        lastChapterId: Long?,
        blockIndex: Int = 0,
        chapterCharOffset: Int = 0,
        now: Long = System.currentTimeMillis(),
    ) {
        repository.setBookProgress(
            novelId = novelId,
            charOffset = charOffset.coerceAtLeast(0L),
            lastChapterId = lastChapterId,
            blockIndex = blockIndex.coerceAtLeast(0),
            chapterCharOffset = chapterCharOffset.coerceAtLeast(0),
            updatedAt = now,
        )
    }
}
