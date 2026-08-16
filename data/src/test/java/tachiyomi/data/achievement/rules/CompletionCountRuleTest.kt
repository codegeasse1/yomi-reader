package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class CompletionCountRuleTest {

    @Test
    fun `evaluateFull uses completed title count`() = runTest {
        val context = mockk<RuleContext>()
        coEvery { context.getCompletedCount(AchievementCategory.MANGA) } returns 12

        val rule = CompletionCountRule(
            achievementId = "complete_10_manga",
            category = AchievementCategory.MANGA,
        )

        rule.evaluateFull(context) shouldBe 12
    }

    @Test
    fun `evaluateDelta ignores non completion events`() = runTest {
        val context = mockk<RuleContext>(relaxed = true)
        val rule = CompletionCountRule("complete_10_manga", AchievementCategory.MANGA)

        rule.evaluateDelta(AchievementEvent.ChapterRead(1L, 1), 0, context) shouldBe RuleResult.NoChange
    }

    @Test
    fun `evaluateDelta uses completed count on completion event`() = runTest {
        val context = mockk<RuleContext>()
        coEvery { context.getCompletedCount(AchievementCategory.MANGA) } returns 10

        val rule = CompletionCountRule("complete_10_manga", AchievementCategory.MANGA)

        rule.evaluateDelta(AchievementEvent.MangaCompleted(1L), 0, context) shouldBe RuleResult.Update(10)
    }
}
