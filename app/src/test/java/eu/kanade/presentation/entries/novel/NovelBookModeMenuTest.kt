package eu.kanade.presentation.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookModeMenuTest {

    @Test
    fun `book mode exposes book as current and chapters as target`() {
        resolveNovelBookModeMenu(readAsBook = true) shouldBe NovelBookModeMenu(
            current = NovelBookReadingMode.BOOK,
            target = NovelBookReadingMode.CHAPTERS,
        )
    }

    @Test
    fun `chapter mode exposes chapters as current and book as target`() {
        resolveNovelBookModeMenu(readAsBook = false) shouldBe NovelBookModeMenu(
            current = NovelBookReadingMode.CHAPTERS,
            target = NovelBookReadingMode.BOOK,
        )
    }
}
