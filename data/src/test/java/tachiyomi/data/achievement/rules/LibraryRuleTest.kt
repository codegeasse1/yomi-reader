package tachiyomi.data.achievement.rules

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.rules.LibraryRule
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class LibraryRuleTest {

    private lateinit var context: RuleContext
    private lateinit var rule: LibraryRule

    @BeforeEach
    fun setup() {
        context = mockk()
        rule = LibraryRule(
            achievementId = "library_collector",
            category = AchievementCategory.BOTH,
        )
    }

    @Test
    fun `evaluateDelta returns Update with count when event matches`() = runTest {
        coEvery { context.getLibraryCount(AchievementCategory.BOTH) } returns 5
        val event = AchievementEvent.LibraryAdded(entryId = 1L, type = AchievementCategory.MANGA)

        val result = rule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.Update(5)
    }

    @Test
    fun `evaluateDelta returns NoChange when event does not match`() = runTest {
        val event = AchievementEvent.ChapterRead(mangaId = 1L, chapterNumber = 1)

        val result = rule.evaluateDelta(event, currentProgress = 0, context = context)

        result shouldBe RuleResult.NoChange
    }

    @Test
    fun `evaluateFull returns absolute library count`() = runTest {
        coEvery { context.getLibraryCount(AchievementCategory.BOTH) } returns 12

        val result = rule.evaluateFull(context = context)

        result shouldBe 12
    }
}
