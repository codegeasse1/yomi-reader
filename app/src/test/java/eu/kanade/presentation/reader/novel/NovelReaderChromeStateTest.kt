package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderChromeStateTest {

    @Test
    fun `book mode counts towards the end of the novel and drops page ticks`() {
        // Page ticks mark positions the book flow does not stop at, so showing them in book mode
        // pointed the reader at chapter boundaries that are no longer boundaries.
        val chrome = resolveReaderChromeState(
            bookModeEnabled = true,
            readingProgressPercent = 42,
            usePageReader = true,
            pageIndex = 3,
            pageCount = 10,
            showScrollPercentage = true,
        )

        chrome.progressPercent shouldBe 42
        chrome.railTopLabel shouldBe "42%"
        chrome.railBottomLabel shouldBe "100%"
        chrome.tickFractions shouldBe emptyList()
    }

    @Test
    fun `the page reader keeps its page labels and ticks outside book mode`() {
        val chrome = resolveReaderChromeState(
            bookModeEnabled = false,
            readingProgressPercent = 40,
            usePageReader = true,
            pageIndex = 3,
            pageCount = 10,
            showScrollPercentage = true,
        )

        chrome.railTopLabel shouldBe resolveReaderPageRailLabels(pageIndex = 3, pageCount = 10).first
        chrome.railBottomLabel shouldBe resolveReaderPageRailLabels(pageIndex = 3, pageCount = 10).second
        chrome.tickFractions shouldBe resolveReaderVerticalSeekbarTickFractions(10)
    }

    @Test
    fun `scrolling falls back to the percentage labels and no ticks`() {
        val chrome = resolveReaderChromeState(
            bookModeEnabled = false,
            readingProgressPercent = 77,
            usePageReader = false,
            pageIndex = 0,
            pageCount = 0,
            showScrollPercentage = true,
        )

        val expected = verticalSeekbarLabels(readingProgressPercent = 77, showScrollPercentage = true)
        chrome.railTopLabel shouldBe expected.first
        chrome.railBottomLabel shouldBe expected.second
        chrome.tickFractions shouldBe emptyList()
    }

    @Test
    fun `a progress reading outside the range is clamped before it reaches the chrome`() {
        resolveReaderChromeState(
            bookModeEnabled = true,
            readingProgressPercent = 140,
            usePageReader = false,
            pageIndex = 0,
            pageCount = 0,
            showScrollPercentage = true,
        ).railTopLabel shouldBe "100%"

        resolveReaderChromeState(
            bookModeEnabled = true,
            readingProgressPercent = -5,
            usePageReader = false,
            pageIndex = 0,
            pageCount = 0,
            showScrollPercentage = true,
        ).railTopLabel shouldBe "0%"
    }
}
