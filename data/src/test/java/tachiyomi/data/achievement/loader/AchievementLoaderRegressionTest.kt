package tachiyomi.data.achievement.loader

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.AchievementTestBase
import tachiyomi.data.achievement.repository.AchievementRepositoryImpl
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementProgress
import tachiyomi.domain.achievement.model.AchievementType

class AchievementLoaderRegressionTest : AchievementTestBase() {

    private lateinit var repository: AchievementRepositoryImpl

    @org.junit.jupiter.api.BeforeEach
    override fun setup() {
        super.setup()
        repository = AchievementRepositoryImpl(database)
    }

    @Test
    fun `locale refresh preserves existing progress`() = runTest {
        val achievement = Achievement(
            id = "first_chapter",
            type = AchievementType.EVENT,
            category = AchievementCategory.MANGA,
            threshold = 1,
            points = 10,
            title = "First Chapter",
        )
        repository.insertAchievement(achievement)
        repository.insertOrUpdateProgress(
            AchievementProgress(
                achievementId = "first_chapter",
                progress = 1,
                maxProgress = 1,
                isUnlocked = true,
                unlockedAt = 42L,
            ),
        )

        repository.insertAchievement(achievement.copy(title = "Premier Chapitre"))

        val progress = repository.getProgress("first_chapter").first()
        progress?.isUnlocked shouldBe true
        progress?.unlockedAt shouldBe 42L
        progress?.progress shouldBe 1
    }

    @Test
    fun `reward backfill preserves existing progress`() = runTest {
        val achievement = Achievement(
            id = "secret_goku",
            type = AchievementType.SECRET,
            category = AchievementCategory.SECRET,
            threshold = 9000,
            points = 500,
            title = "Not Even My Final Form!",
        )
        repository.insertAchievement(achievement)
        repository.insertOrUpdateProgress(
            AchievementProgress(
                achievementId = "secret_goku",
                progress = 9000,
                maxProgress = 9000,
                isUnlocked = true,
                unlockedAt = 100L,
            ),
        )

        repository.insertAchievement(
            achievement.copy(
                title = "Not Even My Final Form!",
            ),
        )

        val progress = repository.getProgress("secret_goku").first()
        progress?.isUnlocked shouldBe true
        progress?.unlockedAt shouldBe 100L
    }

    @Test
    fun `reloading same achievement preserves version and createdAt`() = runTest {
        val original = Achievement(
            id = "persistent_1",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 100,
            points = 50,
            title = "Original Title",
            version = 5,
            createdAt = 111111L,
        )
        repository.insertAchievement(original)

        repository.insertAchievement(original.copy(title = "Updated Title"))

        val restored = repository.getAll().first().single()
        restored.version shouldBe 5
        restored.createdAt shouldBe 111111L
    }
}
