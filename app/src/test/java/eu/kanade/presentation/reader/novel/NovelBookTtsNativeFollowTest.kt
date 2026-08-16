package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelBookTtsNativeFollowTest {

    @Test
    fun `a block below the reading band is scrolled up to it`() {
        val delta = novelBookTtsScrollDelta(
            blockTopInWindow = 1_800f,
            viewportTopInWindow = 100f,
            viewportHeightPx = 900f,
        )

        delta shouldBe 1_400f
    }

    @Test
    fun `a block above the reading band is scrolled back down`() {
        val delta = novelBookTtsScrollDelta(
            blockTopInWindow = 120f,
            viewportTopInWindow = 100f,
            viewportHeightPx = 900f,
        )

        delta shouldBe -280f
    }

    @Test
    fun `a block already in the reading band asks for no scroll worth doing`() {
        val delta = novelBookTtsScrollDelta(
            blockTopInWindow = 402f,
            viewportTopInWindow = 100f,
            viewportHeightPx = 900f,
        )

        (kotlin.math.abs(delta) <= NOVEL_BOOK_TTS_SCROLL_EPSILON_PX) shouldBe true
    }

    @Test
    fun `a block that is not laid out yet has no target`() {
        val positions = NovelBookTtsBlockPositions()
        positions.recordViewport(topInWindow = 0f, heightPx = 900f)

        positions.scrollDeltaFor(NovelBlockAnchor(chapterId = 4L, blockIndex = 2)) shouldBe null
    }

    @Test
    fun `a recorded block is targeted and a disposed one is forgotten`() {
        val positions = NovelBookTtsBlockPositions()
        val anchor = NovelBlockAnchor(chapterId = 4L, blockIndex = 2)
        positions.recordViewport(topInWindow = 0f, heightPx = 900f)
        positions.recordBlock(anchor = anchor, topInWindow = 600f)

        positions.scrollDeltaFor(anchor) shouldBe 300f

        positions.forgetBlock(anchor)

        positions.scrollDeltaFor(anchor) shouldBe null
    }

    @Test
    fun `blocks of different chapters never share a target`() {
        val positions = NovelBookTtsBlockPositions()
        positions.recordViewport(topInWindow = 0f, heightPx = 900f)
        positions.recordBlock(NovelBlockAnchor(chapterId = 4L, blockIndex = 2), topInWindow = 600f)

        positions.scrollDeltaFor(NovelBlockAnchor(chapterId = 5L, blockIndex = 2)) shouldBe null
    }
}
