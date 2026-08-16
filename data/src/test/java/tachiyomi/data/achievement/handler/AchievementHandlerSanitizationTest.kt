package tachiyomi.data.achievement.handler

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.UnlockableManager
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.Reward
import tachiyomi.domain.achievement.model.RewardType
import tachiyomi.domain.achievement.repository.AchievementRepository

class AchievementHandlerSanitizationTest {

    private val repository = mockk<AchievementRepository>(relaxed = true)
    private val unlockableManager = mockk<UnlockableManager>(relaxed = true)

    private fun achievementWithRewards(
        id: String,
        rewards: List<Reward> = emptyList(),
    ) = Achievement(
        id = id,
        type = AchievementType.EVENT,
        category = AchievementCategory.MANGA,
        threshold = 1,
        points = 10,
        title = "Test",
        rewards = rewards.ifEmpty { null },
    )

    @Test
    fun `sanitizeFirstAchievement locks treasury unlockables for invalid unlock`() = runTest {
        val achievement = achievementWithRewards(
            id = "first_chapter",
            rewards = listOf(
                Reward(type = RewardType.AURA, id = "profile_effect_test", title = "Test Effect"),
            ),
        )
        val unlockedProgress = AchievementProgress(
            achievementId = "first_chapter",
            progress = 1,
            maxProgress = 1,
            isUnlocked = true,
            unlockedAt = 123L,
        )

        every { repository.getProgress("first_chapter") } returns flowOf(unlockedProgress)
        every { repository.getAll() } returns flowOf(listOf(achievement))

        val handler = AchievementHandler(
            eventBus = mockk(relaxed = true),
            repository = repository,
            diversityChecker = mockk(relaxed = true),
            streakChecker = mockk(relaxed = true),
            timeBasedChecker = mockk(relaxed = true),
            featureBasedChecker = mockk(relaxed = true),
            featureCollector = mockk(relaxed = true),
            pointsManager = mockk(relaxed = true),
            unlockableManager = unlockableManager,
            mangaHandler = mockk(relaxed = true),
            animeHandler = mockk(relaxed = true),
            novelHandler = mockk(relaxed = true),
            mangaRepository = mockk(relaxed = true),
            animeRepository = mockk(relaxed = true),
            novelRepository = mockk(relaxed = true),
            userProfileManager = mockk(relaxed = true),
            activityDataRepository = mockk(relaxed = true),
            ruleRegistry = mockk(relaxed = true),
        )

        handler.sanitizeFirstAchievement("first_chapter", hasRelevantHistory = false)

        verify { unlockableManager.lockUnlockablesForAchievement(achievement) }
    }

    @Test
    fun `sanitizeFirstAchievement does not lock treasury when history exists`() = runTest {
        val achievement = achievementWithRewards(id = "first_chapter")
        val unlockedProgress = AchievementProgress(
            achievementId = "first_chapter",
            progress = 1,
            maxProgress = 1,
            isUnlocked = true,
            unlockedAt = 123L,
        )

        every { repository.getProgress("first_chapter") } returns flowOf(unlockedProgress)

        val handler = AchievementHandler(
            eventBus = mockk(relaxed = true),
            repository = repository,
            diversityChecker = mockk(relaxed = true),
            streakChecker = mockk(relaxed = true),
            timeBasedChecker = mockk(relaxed = true),
            featureBasedChecker = mockk(relaxed = true),
            featureCollector = mockk(relaxed = true),
            pointsManager = mockk(relaxed = true),
            unlockableManager = unlockableManager,
            mangaHandler = mockk(relaxed = true),
            animeHandler = mockk(relaxed = true),
            novelHandler = mockk(relaxed = true),
            mangaRepository = mockk(relaxed = true),
            animeRepository = mockk(relaxed = true),
            novelRepository = mockk(relaxed = true),
            userProfileManager = mockk(relaxed = true),
            activityDataRepository = mockk(relaxed = true),
            ruleRegistry = mockk(relaxed = true),
        )

        handler.sanitizeFirstAchievement("first_chapter", hasRelevantHistory = true)

        verify(exactly = 0) { unlockableManager.lockUnlockablesForAchievement(any()) }
    }
}
