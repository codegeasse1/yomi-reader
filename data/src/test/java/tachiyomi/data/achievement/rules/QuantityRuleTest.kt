package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.rules.QuantityRule
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class QuantityRuleTest {

    private lateinit var context: RuleContext
    private lateinit var rule: QuantityRule

    @BeforeEach
    fun setup() {
        context = mockk()
        rule = QuantityRule(
            achievementId = "read_10_chapters",
            category = AchievementCategory.MANGA,
        )
    }

    @Test
    fun `evaluateDelta returns Update with count when event matches`() = runTest {
        coEvery { context.getChaptersRead(AchievementCategory.MANGA) } returns 5
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)

        val result = rule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.Update(5)
    }

    @Test
    fun `evaluateDelta returns NoChange when event does not match`() = runTest {
        val event = AchievementEvent.EpisodeWatched(animeId = 1L, episodeNumber = 1)

        val result = rule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.NoChange
    }

    @Test
    fun `evaluateFull returns absolute chapters read`() = runTest {
        coEvery { context.getChaptersRead(AchievementCategory.MANGA) } returns 12

        val result = rule.evaluateFull(context = context)

        result shouldBe 12
    }

    @Test
    fun `quantity rule uses chapter count for manga chapter events`() = runTest {
        val context = mockk<RuleContext>()
        coEvery { context.getChaptersRead(AchievementCategory.MANGA) } returns 42

        val rule = QuantityRule("read_10_chapters", AchievementCategory.MANGA)

        rule.evaluateDelta(AchievementEvent.ChapterRead(1L, 1), 0, context) shouldBe RuleResult.Update(42)
    }
}
