package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderSelectionGestureArbiterTest {

    @Test
    fun `moving beyond slop before timeout never becomes selection`() {
        NovelReaderSelectionGestureArbiter.shouldPromoteSelectionCandidate(
            elapsedMillis = 180L,
            movedDistancePx = 32f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe false
    }

    @Test
    fun `long press within slop becomes selection candidate`() {
        NovelReaderSelectionGestureArbiter.shouldPromoteSelectionCandidate(
            elapsedMillis = 550L,
            movedDistancePx = 6f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe true
    }

    @Test
    fun `short tap within slop stays a plain tap`() {
        NovelReaderSelectionGestureArbiter.shouldHandlePlainTap(
            elapsedMillis = 120L,
            movedDistancePx = 3f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe true
    }

    @Test
    fun `long press no longer counts as a plain tap`() {
        NovelReaderSelectionGestureArbiter.shouldHandlePlainTap(
            elapsedMillis = 550L,
            movedDistancePx = 3f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe false
    }

    @Test
    fun `drag beyond slop no longer counts as a plain tap`() {
        NovelReaderSelectionGestureArbiter.shouldHandlePlainTap(
            elapsedMillis = 120L,
            movedDistancePx = 24f,
            touchSlopPx = 12f,
            longPressTimeoutMillis = 400L,
        ) shouldBe false
    }
}
