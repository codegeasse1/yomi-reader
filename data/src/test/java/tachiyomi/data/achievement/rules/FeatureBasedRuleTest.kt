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

class FeatureBasedRuleTest {

    private lateinit var context: RuleContext

    @BeforeEach
    fun setup() {
        context = mockk()
    }

    @Test
    fun `FeatureBasedRule backup_master evaluates progress matching backup count`() = runTest {
        val rule = FeatureBasedRule("backup_master")
        coEvery { context.getFeatureCount(AchievementEvent.Feature.BACKUP) } returns 3

        rule.evaluateFull(context) shouldBe 3

        val event = AchievementEvent.FeatureUsed(AchievementEvent.Feature.BACKUP)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(3)
    }

    @Test
    fun `FeatureBasedRule search_user evaluates search and advanced search count combined`() = runTest {
        val rule = FeatureBasedRule("search_user")
        coEvery { context.getFeatureCount(AchievementEvent.Feature.SEARCH) } returns 12
        coEvery { context.getFeatureCount(AchievementEvent.Feature.ADVANCED_SEARCH) } returns 8

        rule.evaluateFull(context) shouldBe 20

        val event = AchievementEvent.FeatureUsed(AchievementEvent.Feature.SEARCH)
        rule.evaluateDelta(event, 0, context) shouldBe RuleResult.Update(20)
    }
}
