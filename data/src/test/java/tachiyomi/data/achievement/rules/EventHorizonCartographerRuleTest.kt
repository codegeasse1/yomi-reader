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

class EventHorizonCartographerRuleTest {

    @Test
    fun `progress requires balanced library and source diversity`() {
        eventHorizonCartographerProgress(
            mangaCount = 12,
            animeCount = 12,
            novelCount = 12,
            sourceDiversity = 12,
        ) shouldBe 100

        eventHorizonCartographerProgress(
            mangaCount = 12,
            animeCount = 12,
            novelCount = 0,
            sourceDiversity = 12,
        ) shouldBe 75
    }

    @Test
    fun `evaluateDelta updates only for library changes`() = runTest {
        val context = mockk<RuleContext>()
        coEvery { context.getLibraryCount(AchievementCategory.MANGA) } returns 12
        coEvery { context.getLibraryCount(AchievementCategory.ANIME) } returns 12
        coEvery { context.getLibraryCount(AchievementCategory.NOVEL) } returns 12
        coEvery { context.getSourceDiversity(AchievementCategory.BOTH) } returns 12
        val rule = EventHorizonCartographerRule()

        rule.evaluateDelta(
            AchievementEvent.LibraryAdded(1L, AchievementCategory.MANGA),
            currentProgress = 0,
            context = context,
        ) shouldBe RuleResult.Update(100)

        rule.evaluateDelta(
            AchievementEvent.ChapterRead(1L, 1),
            currentProgress = 0,
            context = context,
        ) shouldBe RuleResult.NoChange
    }
}
