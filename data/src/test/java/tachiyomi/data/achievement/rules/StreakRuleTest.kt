package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.rules.StreakRule
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class StreakRuleTest {

    private lateinit var context: RuleContext
    private lateinit var rule: StreakRule

    @BeforeEach
    fun setup() {
        context = mockk()
        rule = StreakRule(achievementId = "streak_3_days")
    }

    @Test
    fun `evaluateDelta returns Update with streak when read or watch event occurs`() = runTest {
        coEvery { context.getCurrentStreak() } returns 4
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)

        val result = rule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.Update(4)
    }

    @Test
    fun `evaluateDelta returns NoChange for unrelated events`() = runTest {
        val event = AchievementEvent.AppStart(hourOfDay = 8)

        val result = rule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.NoChange
    }

    @Test
    fun `evaluateFull returns absolute current streak`() = runTest {
        coEvery { context.getCurrentStreak() } returns 7

        val result = rule.evaluateFull(context = context)

        result shouldBe 7
    }
}
