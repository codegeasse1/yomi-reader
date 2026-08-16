package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.rules.DiversityRule
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class DiversityRuleTest {

    private lateinit var context: RuleContext
    private lateinit var genreRule: DiversityRule
    private lateinit var sourceRule: DiversityRule

    @BeforeEach
    fun setup() {
        context = mockk()
        genreRule = DiversityRule(
            achievementId = "genre_diversity_starter",
            category = AchievementCategory.MANGA,
        )
        sourceRule = DiversityRule(
            achievementId = "source_diversity_starter",
            category = AchievementCategory.MANGA,
        )
    }

    @Test
    fun `evaluateDelta returns Update with genre diversity count when genre rule matches on library add`() = runTest {
        coEvery { context.getGenreDiversity(AchievementCategory.MANGA) } returns 5
        val event = AchievementEvent.LibraryAdded(entryId = 1L, type = AchievementCategory.MANGA)

        val result = genreRule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.Update(5)
    }

    @Test
    fun `evaluateDelta returns Update with source diversity count when source rule matches on library add`() = runTest {
        coEvery { context.getSourceDiversity(AchievementCategory.MANGA) } returns 3
        val event = AchievementEvent.LibraryAdded(entryId = 1L, type = AchievementCategory.MANGA)

        val result = sourceRule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.Update(3)
    }

    @Test
    fun `evaluateDelta returns NoChange for unrelated events`() = runTest {
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)

        val result = genreRule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.NoChange
    }

    @Test
    fun `evaluateFull returns absolute genre count`() = runTest {
        coEvery { context.getGenreDiversity(AchievementCategory.MANGA) } returns 8

        val result = genreRule.evaluateFull(context = context)

        result shouldBe 8
    }
}
