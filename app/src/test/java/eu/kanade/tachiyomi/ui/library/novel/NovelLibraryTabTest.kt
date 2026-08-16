package eu.kanade.tachiyomi.ui.library.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelLibraryTabTest {

    @Test
    fun `shows page tabs when search is active even if category tabs are disabled`() {
        shouldShowNovelPageTabs(
            showCategoryTabs = false,
            searchQuery = "search",
        ) shouldBe true
    }

    @Test
    fun `hides page tabs when category tabs are disabled and search is empty`() {
        shouldShowNovelPageTabs(
            showCategoryTabs = false,
            searchQuery = null,
        ) shouldBe false
    }
}
