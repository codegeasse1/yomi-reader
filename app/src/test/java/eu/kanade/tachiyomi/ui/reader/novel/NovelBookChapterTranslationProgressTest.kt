package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookChapterTranslationProgressTest {

    private val progress = mapOf(
        1L to NovelBookChapterTranslationProgress(chapterId = 1L, isDone = true, progress = 100),
        2L to NovelBookChapterTranslationProgress(chapterId = 2L, isTranslating = true, progress = 40),
        3L to NovelBookChapterTranslationProgress(chapterId = 3L, isTranslating = true, progress = 0),
    )

    @Test
    fun `the indicator describes the chapter under the reading position`() {
        progress.overlayIndicatorFor(2L)?.percent shouldBe 40
        progress.overlayIndicatorFor(1L)?.isDone shouldBe true
    }

    @Test
    fun `a chapter without a queue update has no indicator`() {
        progress.overlayIndicatorFor(99L) shouldBe null
        progress.overlayIndicatorFor(null) shouldBe null
    }

    @Test
    fun `the background count excludes the chapter being read and finished chapters`() {
        progress.backgroundTranslatingCount(activeChapterId = 2L) shouldBe 1
        progress.backgroundTranslatingCount(activeChapterId = 1L) shouldBe 2
        progress.backgroundTranslatingCount(activeChapterId = null) shouldBe 2
        emptyMap<Long, NovelBookChapterTranslationProgress>().backgroundTranslatingCount(1L) shouldBe 0
    }

    @Test
    fun `the reported percent is clamped`() {
        NovelBookChapterTranslationProgress(chapterId = 1L, progress = 140).percent shouldBe 100
        NovelBookChapterTranslationProgress(chapterId = 1L, progress = -5).percent shouldBe 0
    }
}
