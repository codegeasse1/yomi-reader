package tachiyomi.data.achievement.handler

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.AchievementTestBase

class PointsManagerTest : AchievementTestBase() {

    private lateinit var pointsManager: PointsManager

    @BeforeEach
    override fun setup() {
        super.setup()
        pointsManager = PointsManager(database)
    }

    @Test
    fun `incrementUnlocked does not corrupt total_achievements`() = runTest {
        // Seed: 3 unlocked, 10 total (the catalog size)
        database.userProfileQueries.updateAchievementCounts(
            user_id = "default",
            unlocked = 3L,
            total = 10L,
            last_updated = 0L,
        )

        pointsManager.incrementUnlocked()
        pointsManager.incrementUnlocked()

        val p = database.userProfileQueries.getDefaultProfile().executeAsOne()
        p.achievements_unlocked shouldBe 5L
        p.total_achievements shouldBe 10L
    }
}
