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

class TimeBasedRuleTest {

    private lateinit var context: RuleContext

    @BeforeEach
    fun setup() {
        context = mockk()
    }

    @Test
    fun `night_owl triggers when session in time range 2 to 5`() = runTest {
        val rule = TimeBasedRule("night_owl")
        coEvery { context.hasSessionInTimeRange(2, 5) } returns true

        rule.evaluateFull(context) shouldBe 1

        val event = AchievementEvent.AppStart(3)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }

    @Test
    fun `marathon_reader triggers when session duration is 2 hours or more`() = runTest {
        val rule = TimeBasedRule("marathon_reader")
        coEvery { context.getMaxSessionDuration() } returns 2 * 60 * 60 * 1000L

        rule.evaluateFull(context) shouldBe 1

        val event = AchievementEvent.SessionEnd(2 * 60 * 60 * 1000L)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(1)
    }
}
