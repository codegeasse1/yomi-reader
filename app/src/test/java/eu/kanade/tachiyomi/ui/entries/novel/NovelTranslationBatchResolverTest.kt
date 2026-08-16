package eu.kanade.tachiyomi.ui.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelTranslationBatchResolverTest {

    @Test
    fun `range scope resolves inclusive chapter interval in visible order`() {
        val chapters = chapters(count = 8)

        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 0,
            chapters = chapters,
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 3,
            rangeEnd = 6,
        )

        resolved shouldBe listOf(3L, 4L, 5L, 6L)
    }

    @Test
    fun `range scope normalizes reversed and out of bounds inputs`() {
        val chapters = chapters(count = 5)

        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 0,
            chapters = chapters,
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 12,
            rangeEnd = 2,
        )

        resolved shouldBe listOf(2L, 3L, 4L, 5L)
    }

    @Test
    fun `range scope returns empty list for empty chapter list`() {
        val resolved = resolveTranslationBatchChapterIds(
            scope = TranslationBatchScope.RANGE,
            limit = 0,
            chapters = emptyList(),
            selectedChapterIds = emptySet(),
            downloadedChapterIds = emptySet(),
            rangeStart = 1,
            rangeEnd = 10,
        )

        resolved shouldBe emptyList()
    }

    private fun chapters(count: Int): List<NovelChapter> {
        return (1..count).map { index ->
            NovelChapter.create().copy(
                id = index.toLong(),
                novelId = 1L,
                chapterNumber = index.toDouble(),
                sourceOrder = index.toLong(),
                read = false,
                url = "https://example.org/ch$index",
                name = "Chapter $index",
            )
        }
    }
}
