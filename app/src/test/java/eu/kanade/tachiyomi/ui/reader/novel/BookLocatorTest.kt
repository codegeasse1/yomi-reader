package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BookLocatorTest {

    private fun spine(): NovelBookSpine = NovelBookSpine(
        listOf(
            // Chapter 1 is long enough to span two blocks, block 2 straddles chapters 2 and 3.
            NovelBookSection(
                chapterId = 1L,
                index = 0,
                name = "One",
                charCount = 100,
                isMeasured = true,
                chapterIds = listOf(1L),
            ),
            NovelBookSection(
                chapterId = 1L,
                index = 1,
                name = "One",
                charCount = 100,
                isMeasured = true,
                chapterIds = listOf(1L),
            ),
            NovelBookSection(
                chapterId = 2L,
                index = 2,
                name = "Two",
                charCount = 100,
                isMeasured = true,
                chapterIds = listOf(2L, 3L),
            ),
        ),
    )

    @Test
    fun `a section is addressed by its index, never by a chapter id`() {
        val sections = spine().sections

        sections.map { it.loaderKey } shouldBe listOf(0L, 1L, 2L)
    }

    @Test
    fun `a chapter is found in every section that covers it`() {
        val spine = spine()

        // A chapter spanning two blocks resolves to the first block holding it.
        spine.indexOf(1L) shouldBe 0
        // A chapter that only appears as the second chapter of a block is still found; with the old
        // synthetic section keys this returned -1 and broke TTS and translation lookups.
        spine.indexOf(3L) shouldBe 2
        spine.indexOf(99L) shouldBe -1
    }

    @Test
    fun `an unknown locator falls back to the start of the book`() {
        BookLocator.UNKNOWN.chapterId shouldBe BookLocator.NO_CHAPTER_ID
        BookLocator.UNKNOWN.charOffset shouldBe 0
        BookLocator.UNKNOWN.blockIndex shouldBe 0
    }
}
