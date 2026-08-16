package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChapterTransitionTest {

    private val current = ReaderChapter(ChapterImpl().apply { id = 1L })
    private val adjacent = ReaderChapter(ChapterImpl().apply { id = 2L })

    @Test
    fun `ordinary adjacent chapter hides transition info when setting is disabled`() {
        shouldShowChapterTransitionInfo(
            alwaysShowChapterTransition = false,
            hasMissingChapters = false,
            destinationChapter = adjacent,
        ) shouldBe false
    }

    @Test
    fun `transition info is shown when setting is enabled`() {
        shouldShowChapterTransitionInfo(
            alwaysShowChapterTransition = true,
            hasMissingChapters = false,
            destinationChapter = adjacent,
        ) shouldBe true
    }

    @Test
    fun `transition info is shown for missing chapters`() {
        shouldShowChapterTransitionInfo(
            alwaysShowChapterTransition = false,
            hasMissingChapters = true,
            destinationChapter = adjacent,
        ) shouldBe true
    }

    @Test
    fun `transition info is shown at end of reader`() {
        shouldShowChapterTransitionInfo(
            alwaysShowChapterTransition = false,
            hasMissingChapters = false,
            destinationChapter = null,
        ) shouldBe true
    }

    @Test
    fun `hidden transition keeps loading indicator until adapter replaces it`() {
        shouldShowChapterTransitionLoading(showInfo = false, ReaderChapter.State.Wait) shouldBe true
        shouldShowChapterTransitionLoading(showInfo = false, ReaderChapter.State.Loading) shouldBe true
        shouldShowChapterTransitionLoading(
            showInfo = false,
            state = ReaderChapter.State.Loaded(emptyList()),
        ) shouldBe true
    }

    @Test
    fun `visible transition only adds loading indicator while loading`() {
        shouldShowChapterTransitionLoading(showInfo = true, ReaderChapter.State.Wait) shouldBe false
        shouldShowChapterTransitionLoading(showInfo = true, ReaderChapter.State.Loading) shouldBe true
        shouldShowChapterTransitionLoading(
            showInfo = true,
            state = ReaderChapter.State.Loaded(emptyList()),
        ) shouldBe false
    }

    @Test
    fun `show info participates in transition identity`() {
        val hidden = ChapterTransition.Next(current, adjacent, showInfo = false)
        val visible = ChapterTransition.Next(current, adjacent, showInfo = true)

        (hidden == visible) shouldBe false
    }
}
