package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class NovelReaderAutoScrollPolicyTest {

    @AfterEach
    fun tearDown() {
        NovelReaderAutoScrollHandoffPolicy.cancel()
    }

    @Test
    fun `end detection is blocked until content is ready and laid out`() {
        val state = resolveNovelAutoScrollEndState(
            canScrollForward = false,
            scrollConsumedPx = 0f,
            isContentReady = false,
            hasCompletedInitialLayout = false,
            hasRenderableItems = false,
            previousStableEndFrameCount = 1,
        )

        state.isAtEnd shouldBe false
        state.stableEndFrameCount shouldBe 0
        state.shouldEnterDwell shouldBe false
        state.shouldAdvanceNow shouldBe false
    }

    @Test
    fun `stable end detection requires multiple frames`() {
        val first = resolveNovelAutoScrollEndState(
            canScrollForward = false,
            scrollConsumedPx = 0f,
            isContentReady = true,
            hasCompletedInitialLayout = true,
            hasRenderableItems = true,
            previousStableEndFrameCount = 0,
            requiredStableFrames = 2,
        )
        val second = resolveNovelAutoScrollEndState(
            canScrollForward = false,
            scrollConsumedPx = 0f,
            isContentReady = true,
            hasCompletedInitialLayout = true,
            hasRenderableItems = true,
            previousStableEndFrameCount = first.stableEndFrameCount,
            requiredStableFrames = 2,
        )

        first.shouldEnterDwell shouldBe false
        second.shouldEnterDwell shouldBe true
        second.shouldAdvanceNow shouldBe true
    }

    @Test
    fun `chapter end behavior controls advance and continuation independently from swipe`() {
        shouldAutoScrollAdvanceToNextChapter(
            NovelAutoScrollChapterEndBehavior.StopAtEnd,
            hasNextChapter = true,
        ) shouldBe false
        shouldAutoScrollAdvanceToNextChapter(
            NovelAutoScrollChapterEndBehavior.AdvanceAndStop,
            hasNextChapter = true,
        ) shouldBe true
        shouldAutoScrollAdvanceToNextChapter(
            NovelAutoScrollChapterEndBehavior.ContinuousReading,
            hasNextChapter = true,
        ) shouldBe true
        shouldAutoScrollContinueAcrossChapters(
            NovelAutoScrollChapterEndBehavior.AdvanceAndStop,
        ) shouldBe false
        shouldAutoScrollContinueAcrossChapters(
            NovelAutoScrollChapterEndBehavior.ContinuousReading,
        ) shouldBe true
    }

    @Test
    fun `auto scroll prefetch uses internal threshold only for advancing modes`() {
        resolveAutoScrollPrefetchNeeded(
            currentIndex = 84,
            totalItems = 100,
            behavior = NovelAutoScrollChapterEndBehavior.ContinuousReading,
        ) shouldBe true
        resolveAutoScrollPrefetchNeeded(
            currentIndex = 83,
            totalItems = 100,
            behavior = NovelAutoScrollChapterEndBehavior.ContinuousReading,
        ) shouldBe false
        resolveAutoScrollPrefetchNeeded(
            currentIndex = 99,
            totalItems = 100,
            behavior = NovelAutoScrollChapterEndBehavior.StopAtEnd,
        ) shouldBe false
    }

    @Test
    fun `speed factor decelerates in cooldown and accelerates after cooldown`() {
        resolveAutoScrollSpeedFactor(
            currentFactor = 1f,
            inCooldown = true,
            delta = 0.2f,
        ) shouldBe 0.8f
        resolveAutoScrollSpeedFactor(
            currentFactor = 0.8f,
            inCooldown = false,
            delta = 0.2f,
        ) shouldBe 1f
    }

    @Test
    fun `handoff policy consumes matching target once and expires stale requests`() {
        NovelReaderAutoScrollHandoffPolicy.prepareHandoff(
            fromChapterId = 1L,
            targetChapterId = 2L,
            speed = 77,
            requestedAtMs = 1_000L,
        )

        val consumed = NovelReaderAutoScrollHandoffPolicy.consumeIfMatches(
            currentChapterId = 2L,
            nowMs = 2_000L,
        )
        consumed?.fromChapterId shouldBe 1L
        consumed?.targetChapterId shouldBe 2L
        consumed?.speed shouldBe 77
        NovelReaderAutoScrollHandoffPolicy.consumeIfMatches(2L, nowMs = 2_000L) shouldBe null

        NovelReaderAutoScrollHandoffPolicy.prepareHandoff(
            fromChapterId = 2L,
            targetChapterId = 3L,
            speed = 40,
            requestedAtMs = 1_000L,
        )
        NovelReaderAutoScrollHandoffPolicy.consumeIfMatches(
            currentChapterId = 3L,
            nowMs = AUTO_SCROLL_HANDOFF_TTL_MS + 1_001L,
        ) shouldBe null
    }

    @Test
    fun `web view near end uses distance threshold`() {
        resolveWebViewAutoScrollNearEnd(
            totalScrollablePx = 1_000,
            scrollYPx = 800,
            endOffsetPx = 199,
        ) shouldBe false
        resolveWebViewAutoScrollNearEnd(
            totalScrollablePx = 1_000,
            scrollYPx = 800,
            endOffsetPx = 200,
        ) shouldBe true
        resolveWebViewAutoScrollNearEnd(
            totalScrollablePx = 0,
            scrollYPx = 0,
            endOffsetPx = 0,
        ) shouldBe true
    }
}
