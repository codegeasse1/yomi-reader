package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsWordRange
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Book mode addresses blocks by `(chapterId, blockIndex)`, so a chapter-local index must never be
 * required for the highlight to be drawn.
 */
class NovelBookTtsHighlightAnchorTest {

    @Test
    fun `an anchor alone is a complete address`() {
        val state = NovelReaderTtsHighlightState(
            sourceBlockIndex = null,
            utteranceText = "alpha beta",
            mode = NovelTtsHighlightMode.ESTIMATED,
            blockAnchor = NovelBlockAnchor(chapterId = 7L, blockIndex = 3),
        )

        state.isEnabled shouldBe true
    }

    @Test
    fun `a block addressed only by anchor is still highlighted`() {
        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString("alpha beta"),
            blockText = "alpha beta",
            sourceBlockIndex = 99,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = null,
                utteranceText = "alpha beta",
                mode = NovelTtsHighlightMode.ESTIMATED,
                blockAnchor = NovelBlockAnchor(chapterId = 7L, blockIndex = 3),
            ),
            highlightColor = Color.Yellow,
            blockAnchor = NovelBlockAnchor(chapterId = 7L, blockIndex = 3),
        )

        rendered.spanStyles.any { it.item.background == Color.Yellow } shouldBe true
    }

    @Test
    fun `the same block index in another chapter is not highlighted`() {
        val rendered = applyNovelReaderTtsHighlight(
            text = AnnotatedString("alpha beta"),
            blockText = "alpha beta",
            sourceBlockIndex = 3,
            highlightState = NovelReaderTtsHighlightState(
                sourceBlockIndex = 3,
                utteranceText = "alpha beta",
                mode = NovelTtsHighlightMode.ESTIMATED,
                blockAnchor = NovelBlockAnchor(chapterId = 7L, blockIndex = 3),
            ),
            highlightColor = Color.Yellow,
            blockAnchor = NovelBlockAnchor(chapterId = 8L, blockIndex = 3),
        )

        rendered.spanStyles.isEmpty() shouldBe true
    }

    @Test
    fun `a moving word range inside one utterance keeps the block highlighted`() {
        val anchor = NovelBlockAnchor(chapterId = 7L, blockIndex = 3)
        val words = listOf(
            NovelTtsWordRange(wordIndex = 0, text = "alpha", startChar = 0, endChar = 5),
            NovelTtsWordRange(wordIndex = 1, text = "beta", startChar = 6, endChar = 10),
        )

        words.forEach { word ->
            val rendered = applyNovelReaderTtsHighlight(
                text = AnnotatedString("alpha beta"),
                blockText = "alpha beta",
                sourceBlockIndex = 3,
                highlightState = NovelReaderTtsHighlightState(
                    sourceBlockIndex = null,
                    utteranceText = "alpha beta",
                    wordRange = word,
                    mode = NovelTtsHighlightMode.ESTIMATED,
                    blockAnchor = anchor,
                ),
                highlightColor = Color.Yellow,
                blockAnchor = anchor,
            )

            rendered.spanStyles.any { it.item.background == Color.Yellow } shouldBe true
        }
    }
}
