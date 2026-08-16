package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.rules.ChadRule
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class ChadRuleTest {

    private lateinit var context: RuleContext
    private lateinit var rule: ChadRule

    @BeforeEach
    fun setup() {
        context = mockk()
        rule = ChadRule()
    }

    @Test
    fun `evaluateDelta returns Update when 10 completed and 0 ongoing`() = runTest {
        coEvery { context.getCompletedCount(AchievementCategory.BOTH) } returns 10
        coEvery { context.getOngoingCount(AchievementCategory.BOTH) } returns 0
        val event = AchievementEvent.MangaCompleted(mangaId = 1L)

        val result = rule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.Update(1)
    }

    @Test
    fun `evaluateDelta returns NoChange when there are ongoing titles`() = runTest {
        coEvery { context.getCompletedCount(AchievementCategory.BOTH) } returns 10
        coEvery { context.getOngoingCount(AchievementCategory.BOTH) } returns 1
        val event = AchievementEvent.MangaCompleted(mangaId = 1L)

        val result = rule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.NoChange
    }

    @Test
    fun `evaluateFull returns 1 if chad criteria met`() = runTest {
        coEvery { context.getCompletedCount(AchievementCategory.BOTH) } returns 15
        coEvery { context.getOngoingCount(AchievementCategory.BOTH) } returns 0

        val result = rule.evaluateFull(context = context)

        result shouldBe 1
    }

    @Test
    fun `evaluateFull returns 0 if chad criteria not met`() = runTest {
        coEvery { context.getCompletedCount(AchievementCategory.BOTH) } returns 9
        coEvery { context.getOngoingCount(AchievementCategory.BOTH) } returns 0

        val result = rule.evaluateFull(context = context)

        result shouldBe 0
    }
}
