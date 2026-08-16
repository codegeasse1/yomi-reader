package eu.kanade.tachiyomi.ui.reader.novel

/** Where a migrated reading position was recovered from. */
enum class BookProgressMigrationSource {
    /** The whole-book character offset stored by versions that had no locator. */
    StoredOffset,

    /** A book position packed into the per-chapter progress column by the classic reader. */
    LegacyChapterProgress,

    /** Nothing usable was stored: the book opens at the start of the chapter it is entered from. */
    ChapterStart,
}

/** Result of the one-off conversion of a stored position into a locator. */
data class BookProgressMigrationPlan(
    val locator: BookLocator,
    val source: BookProgressMigrationSource,
)

/**
 * Converts a position stored by an older version into a [BookLocator], once per book.
 *
 * The three sources are tried in order of how much they know:
 * 1. the whole-book character offset of the book row, which is exact as long as the artifact was not
 *    rebuilt in between;
 * 2. a book position packed into the per-chapter progress column, whose section index is an index
 *    into the chapter list and therefore has to be resolved through [chapterIdsInReadingOrder];
 * 3. the first character of the chapter the reader is entering from.
 *
 * Returns null when the row is already a locator ([alreadyMigrated]) or when the artifact cannot
 * resolve any of the candidates, in which case the caller must leave the stored position untouched.
 *
 * The lookups are passed in as lambdas so the rule can be tested without an artifact on disk.
 */
@Suppress("DEPRECATION")
internal fun planBookProgressMigration(
    alreadyMigrated: Boolean,
    storedCharOffset: Long,
    storedChapterId: Long?,
    legacyChapterProgress: Long,
    chapterIdsInReadingOrder: List<Long>,
    openedChapterId: Long,
    locatorOfGlobalOffset: (Int) -> BookLocator?,
    locatorInChapter: (chapterId: Long, fraction: Float) -> BookLocator?,
): BookProgressMigrationPlan? {
    if (alreadyMigrated) return null

    if (storedCharOffset > 0L) {
        val offset = storedCharOffset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        locatorOfGlobalOffset(offset)?.takeIf { it.isResolved }?.let {
            return BookProgressMigrationPlan(it, BookProgressMigrationSource.StoredOffset)
        }
    }

    decodeLegacyBookLocation(legacyChapterProgress)?.let { legacy ->
        val chapterId = chapterIdsInReadingOrder.getOrNull(legacy.chapterIndex)
        if (chapterId != null) {
            locatorInChapter(chapterId, legacy.chapterFraction)?.takeIf { it.isResolved }?.let {
                return BookProgressMigrationPlan(it, BookProgressMigrationSource.LegacyChapterProgress)
            }
        }
    }

    // The chapter of the stored row is still better than the chapter the reader happens to open.
    val fallbackChapterId = storedChapterId?.takeIf { it != BookLocator.NO_CHAPTER_ID } ?: openedChapterId
    locatorInChapter(fallbackChapterId, 0f)?.takeIf { it.isResolved }?.let {
        return BookProgressMigrationPlan(it, BookProgressMigrationSource.ChapterStart)
    }

    return null
}

private val BookLocator.isResolved: Boolean
    get() = chapterId != BookLocator.NO_CHAPTER_ID
