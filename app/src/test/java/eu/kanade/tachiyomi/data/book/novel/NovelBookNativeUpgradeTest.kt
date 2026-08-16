package eu.kanade.tachiyomi.data.book.novel

import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookNativeUpgradeTest {

    private fun index(chapterCount: Int, charsPerChapter: Int): NovelBookIndex {
        var charStart = 0
        var byteStart = 0L
        val chapters = (0 until chapterCount).map { position ->
            val entry = NovelBookChapterEntry(
                chapterId = position.toLong(),
                order = position,
                title = "Chapter $position",
                anchorId = "nb-ch-$position",
                charStart = charStart,
                charLength = charsPerChapter,
                byteStart = byteStart,
                byteLength = charsPerChapter,
            )
            charStart += charsPerChapter
            byteStart += charsPerChapter
            entry
        }
        return NovelBookIndex(chapters)
    }

    @Test
    fun `the default render window fits the native renderer's memory budget`() {
        // With pre-compiled blocks a window is a list of live objects, not a string, so the old
        // 200k character window is far too large to keep in memory.
        NovelBookBlockPlanner.DEFAULT_TARGET_CHARS shouldBeLessThanOrEqual 40_000

        val blocks = NovelBookBlockPlanner.plan(index(chapterCount = 40, charsPerChapter = 10_000))

        blocks.size shouldBe 10
        blocks.all { block -> block.charLength <= NovelBookBlockPlanner.DEFAULT_TARGET_CHARS } shouldBe true
    }

    @Test
    fun `a chapter longer than the window still gets its own whole block`() {
        // Chapter boundaries win over the size target, otherwise a long chapter would be cut in half
        // and the reader would show a section that starts mid sentence.
        val blocks = NovelBookBlockPlanner.plan(index(chapterCount = 2, charsPerChapter = 120_000))

        blocks.size shouldBe 2
        blocks.first().charLength shouldBe 120_000
    }

    @Test
    fun `progress and chapter lookup are unaffected by the smaller window`() {
        val bookIndex = index(chapterCount = 10, charsPerChapter = 10_000)
        val blocks = NovelBookBlockPlanner.plan(bookIndex)

        NovelBookBlockPlanner.chapterAt(bookIndex, 45_000)?.chapterId shouldBe 4L
        NovelBookBlockPlanner.blockAt(blocks, 45_000)!!.let { block ->
            (block.charStart <= 45_000 && 45_000 < block.charEnd) shouldBe true
        }
    }
}
