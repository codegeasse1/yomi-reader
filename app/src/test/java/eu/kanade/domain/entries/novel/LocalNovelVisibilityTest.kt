package eu.kanade.domain.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.source.local.entries.novel.LocalNovelSource

class LocalNovelVisibilityTest {

    @Test
    fun `non-local sources always visible`() {
        LocalNovelVisibility.shouldShowLocalNovelEntry(
            sourceId = 123L,
            url = "any",
            coverUrl = "content://.../local/foo/cover.jpg",
            hasSupportedContent = { error("should not check FS") },
        ) shouldBe true
    }

    @Test
    fun `hides local entry with manga local cover without FS check`() {
        var fsCalls = 0
        LocalNovelVisibility.shouldShowLocalNovelEntry(
            sourceId = LocalNovelSource.ID,
            url = "procachka",
            coverUrl = "content://.../local/procachka/cover.jpg",
            hasSupportedContent = {
                fsCalls++
                true
            },
        ) shouldBe false
        fsCalls shouldBe 0
    }

    @Test
    fun `hides local entry missing on disk`() {
        LocalNovelVisibility.shouldShowLocalNovelEntry(
            sourceId = LocalNovelSource.ID,
            url = "procachka",
            coverUrl = "content://.../localnovel/procachka/cover.jpg",
            hasSupportedContent = { false },
        ) shouldBe false
    }

    @Test
    fun `shows local entry with supported content`() {
        LocalNovelVisibility.shouldShowLocalNovelEntry(
            sourceId = LocalNovelSource.ID,
            url = "mybook.epub",
            coverUrl = null,
            hasSupportedContent = { it == "mybook.epub" },
        ) shouldBe true
    }

    @Test
    fun `history cover heuristic`() {
        LocalNovelVisibility.shouldHideLocalHistoryByCover(
            sourceId = LocalNovelSource.ID,
            coverUrl = "file:///sdcard/local/procachka/cover.jpg",
        ) shouldBe true

        LocalNovelVisibility.shouldHideLocalHistoryByCover(
            sourceId = LocalNovelSource.ID,
            coverUrl = "file:///sdcard/localnovel/book/cover.jpg",
        ) shouldBe false

        LocalNovelVisibility.shouldHideLocalHistoryByCover(
            sourceId = 99L,
            coverUrl = "file:///sdcard/local/procachka/cover.jpg",
        ) shouldBe false
    }
}
