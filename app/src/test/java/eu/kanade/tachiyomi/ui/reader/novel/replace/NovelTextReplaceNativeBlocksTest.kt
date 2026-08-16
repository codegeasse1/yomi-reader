package eu.kanade.tachiyomi.ui.reader.novel.replace

import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlock
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlockKind
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeSegment
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookAnchoredNativeBlock
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelTextReplaceNativeBlocksTest {

    private val contentRule = ReplaceRule(
        pattern = "\\b12\\b",
        replacement = "XII",
        isRegex = true,
        scopeContent = true,
        scopeTitle = false,
    )

    private val titleRule = ReplaceRule(
        pattern = "Гл\\.",
        replacement = "Глава",
        isRegex = true,
        scopeTitle = true,
        scopeContent = false,
    )

    private fun anchored(
        text: String,
        kind: NovelBookNativeBlockKind = NovelBookNativeBlockKind.PARAGRAPH,
        isChapterHeading: Boolean = false,
        bold: Boolean = false,
    ) = NovelBookAnchoredNativeBlock(
        block = NovelBookNativeBlock(
            chapterId = 1L,
            charStart = 0,
            charLength = text.length,
            kind = kind,
            isChapterHeading = isChapterHeading,
            segments = listOf(NovelBookNativeSegment(t = text, b = bold)),
        ),
        blockIndex = 0,
    )

    @Test
    fun `chapter heading gets title and content rules, body gets content rules only`() {
        val blocks = listOf(
            anchored("Гл. 12", kind = NovelBookNativeBlockKind.HEADING, isChapterHeading = true),
            anchored("Текст 12."),
        )

        val replaced = applyReplaceRulesToNativeBlocks(blocks, listOf(titleRule, contentRule))

        replaced[0].block.segments[0].t shouldBe "Глава XII"
        replaced[1].block.segments[0].t shouldBe "Текст XII."
    }

    @Test
    fun `title rules do not touch body blocks or content headings`() {
        val blocks = listOf(
            anchored("Гл. 1", kind = NovelBookNativeBlockKind.HEADING, isChapterHeading = true),
            anchored("Гл. 2"),
            anchored("Гл. 3", kind = NovelBookNativeBlockKind.HEADING),
        )

        val replaced = applyReplaceRulesToNativeBlocks(blocks, listOf(titleRule))

        replaced[0].block.segments[0].t shouldBe "Глава 1"
        replaced[1].block.segments[0].t shouldBe "Гл. 2"
        replaced[2].block.segments[0].t shouldBe "Гл. 3"
    }

    @Test
    fun `segment styles are preserved`() {
        val blocks = listOf(anchored("12 страниц", bold = true))

        val replaced = applyReplaceRulesToNativeBlocks(blocks, listOf(contentRule))

        val segment = replaced[0].block.segments[0]
        segment.t shouldBe "XII страниц"
        segment.b shouldBe true
    }

    @Test
    fun `rule and image blocks are untouched`() {
        val blocks = listOf(
            anchored("—", kind = NovelBookNativeBlockKind.RULE),
            NovelBookAnchoredNativeBlock(
                block = NovelBookNativeBlock(
                    chapterId = 1L,
                    charStart = 0,
                    charLength = 0,
                    kind = NovelBookNativeBlockKind.IMAGE,
                    imageUrl = "img-12.png",
                ),
                blockIndex = 1,
            ),
        )

        val replaced = applyReplaceRulesToNativeBlocks(blocks, listOf(contentRule))

        replaced shouldBe blocks
    }

    @Test
    fun `both-scoped rule applies to chapter heading exactly once`() {
        val bothRule = ReplaceRule(pattern = "a", replacement = "aa", isRegex = false)

        val blocks = listOf(
            anchored("a", kind = NovelBookNativeBlockKind.HEADING, isChapterHeading = true),
            anchored("a"),
        )

        val replaced = applyReplaceRulesToNativeBlocks(blocks, listOf(bothRule))

        replaced[0].block.segments[0].t shouldBe "aa"
        replaced[1].block.segments[0].t shouldBe "aa"
    }

    @Test
    fun `empty rules or blocks return input unchanged`() {
        val blocks = listOf(anchored("Гл. 12"))
        applyReplaceRulesToNativeBlocks(blocks, emptyList()) shouldBe blocks
        applyReplaceRulesToNativeBlocks(emptyList(), listOf(contentRule)) shouldBe emptyList()
    }
}
