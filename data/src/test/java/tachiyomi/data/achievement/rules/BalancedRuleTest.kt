package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class BalancedRuleTest {

    private lateinit var context: RuleContext

    @BeforeEach
    fun setup() {
        context = mockk()
    }

    @Test
    fun `BalancedRule returns minimum of manga read count and anime watched count`() = runTest {
        val rule = BalancedRule("balanced_fan")
        coEvery { context.getChaptersRead(AchievementCategory.MANGA) } returns 75
        coEvery { context.getChaptersRead(AchievementCategory.ANIME) } returns 60

        rule.evaluateFull(context) shouldBe 60

        val event = AchievementEvent.ChapterRead(1L, 10)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(60)
    }
}
