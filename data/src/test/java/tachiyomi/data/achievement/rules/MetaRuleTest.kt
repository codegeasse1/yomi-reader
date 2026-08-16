package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class MetaRuleTest {

    private lateinit var context: RuleContext

    @BeforeEach
    fun setup() {
        context = mockk()
    }

    @Test
    fun `MetaRule achievement_hunter progress matches standard unlocked achievements count`() = runTest {
        val rule = MetaRule("achievement_hunter")
        coEvery { context.getUnlockedAchievementsCountExcluding(MetaRule.META_IDS) } returns 15

        rule.evaluateFull(context) shouldBe 15

        val event = AchievementEvent.AppStart(12)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(15)
    }
}
