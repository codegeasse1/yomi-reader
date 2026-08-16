package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
class BookProgressMigrationTest {

    // Legacy book positions were stored as marker + chapterIndex * 1000 + permille.
    private fun legacyValue(chapterIndex: Int, permille: Int): Long =
        6_100_000_000L + (chapterIndex.toLong() * 1_000L) + permille.toLong()

    private val chapterIds = listOf(10L, 20L, 30L)

    // Each chapter is 1000 chars long and they follow each other in the body.
    private fun locatorInChapter(chapterId: Long, fraction: Float): BookLocator? {
        val order = chapterIds.indexOf(chapterId)
        if (order < 0) return null
        return BookLocator(
            chapterId = chapterId,
            blockIndex = order,
            charOffset = (999 * fraction).toInt(),
        )
    }

    private fun locatorOfGlobalOffset(offset: Int): BookLocator? {
        val order = (offset / 1_000).coerceIn(0, chapterIds.lastIndex)
        return BookLocator(
            chapterId = chapterIds[order],
            blockIndex = order,
            charOffset = offset - (order * 1_000),
        )
    }

    private fun plan(
        alreadyMigrated: Boolean = false,
        storedCharOffset: Long = 0L,
        storedChapterId: Long? = null,
        legacyChapterProgress: Long = 0L,
        openedChapterId: Long = 10L,
        locatorInChapter: (Long, Float) -> BookLocator? = ::locatorInChapter,
        locatorOfGlobalOffset: (Int) -> BookLocator? = ::locatorOfGlobalOffset,
    ) = planBookProgressMigration(
        alreadyMigrated = alreadyMigrated,
        storedCharOffset = storedCharOffset,
        storedChapterId = storedChapterId,
        legacyChapterProgress = legacyChapterProgress,
        chapterIdsInReadingOrder = chapterIds,
        openedChapterId = openedChapterId,
        locatorOfGlobalOffset = locatorOfGlobalOffset,
        locatorInChapter = locatorInChapter,
    )

    @Test
    fun `a migrated row is left alone`() {
        plan(alreadyMigrated = true, storedCharOffset = 1_500L) shouldBe null
    }

    @Test
    fun `a stored whole-book offset wins`() {
        val migration = plan(storedCharOffset = 1_500L, legacyChapterProgress = legacyValue(2, 500))

        migration?.source shouldBe BookProgressMigrationSource.StoredOffset
        migration?.locator shouldBe BookLocator(chapterId = 20L, blockIndex = 1, charOffset = 500)
    }

    @Test
    fun `a legacy chapter position is resolved through the chapter list`() {
        val migration = plan(legacyChapterProgress = legacyValue(chapterIndex = 2, permille = 500))

        migration?.source shouldBe BookProgressMigrationSource.LegacyChapterProgress
        migration?.locator?.chapterId shouldBe 30L
        migration?.locator?.charOffset shouldBe 499
    }

    @Test
    fun `a legacy value pointing past the chapter list falls back to the chapter start`() {
        val migration = plan(legacyChapterProgress = legacyValue(chapterIndex = 99, permille = 500))

        migration?.source shouldBe BookProgressMigrationSource.ChapterStart
        migration?.locator shouldBe BookLocator(chapterId = 10L, blockIndex = 0, charOffset = 0)
    }

    @Test
    fun `other progress encodings are not read as book positions`() {
        // A plain page number and a native scroll value must not be mistaken for a book location.
        plan(legacyChapterProgress = 7L)?.source shouldBe BookProgressMigrationSource.ChapterStart
        plan(legacyChapterProgress = 5_000_000_120L)?.source shouldBe BookProgressMigrationSource.ChapterStart
    }

    @Test
    fun `the stored chapter beats the chapter being opened`() {
        val migration = plan(storedChapterId = 30L, openedChapterId = 10L)

        migration?.source shouldBe BookProgressMigrationSource.ChapterStart
        migration?.locator?.chapterId shouldBe 30L
    }

    @Test
    fun `nothing is written when the artifact cannot resolve the position`() {
        plan(
            storedCharOffset = 1_500L,
            locatorInChapter = { _, _ -> null },
            locatorOfGlobalOffset = { null },
        ) shouldBe null
    }

    @Test
    fun `an unresolved locator is not accepted as a migration result`() {
        val migration = plan(
            storedCharOffset = 1_500L,
            locatorOfGlobalOffset = {
                BookLocator(chapterId = BookLocator.NO_CHAPTER_ID, blockIndex = 0, charOffset = 12)
            },
        )

        // The offset resolved to no chapter at all, so the chapter fallback is used instead.
        migration?.source shouldBe BookProgressMigrationSource.ChapterStart
        migration?.locator?.chapterId shouldBe 10L
    }

    @Test
    fun `legacy values outside the reserved range are ignored`() {
        decodeLegacyBookLocation(6_099_999_999L) shouldBe null
        decodeLegacyBookLocation(7_000_000_000L) shouldBe null
        decodeLegacyBookLocation(legacyValue(12, 250))?.chapterIndex shouldBe 12
        decodeLegacyBookLocation(legacyValue(12, 250))?.chapterFraction shouldBe 0.25f
    }
}
