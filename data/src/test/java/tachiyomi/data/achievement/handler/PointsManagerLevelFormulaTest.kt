package tachiyomi.data.achievement.handler

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.UserProfile

class PointsManagerLevelFormulaTest {

    private val pointsManager = PointsManagerTester()

    private class PointsManagerTester {
        fun calculateLevel(points: Int): Int {
            return UserProfile.getLevelFromXP(points)
        }
    }

    @Test
    fun `level 1 at 0 XP`() {
        pointsManager.calculateLevel(0) shouldBe 1
    }

    @Test
    fun `level 1 at 99 XP`() {
        pointsManager.calculateLevel(99) shouldBe 1
    }

    @Test
    fun `level 2 at 283 XP`() {
        val xpForLevel2 = UserProfile.getXPForLevel(2)
        pointsManager.calculateLevel(xpForLevel2) shouldBe 2
    }

    @Test
    fun `level still 2 just below level 3`() {
        val xpForLevel2 = UserProfile.getXPForLevel(2)
        val xpForLevel3 = UserProfile.getXPForLevel(3)
        pointsManager.calculateLevel(xpForLevel2 + xpForLevel3 - 1) shouldBe 2
    }

    @Test
    fun `level 3 at cumulative XP for level 3`() {
        val xpNeeded = UserProfile.getXPForLevel(2) + UserProfile.getXPForLevel(3)
        pointsManager.calculateLevel(xpNeeded) shouldBe 3
    }

    @Test
    fun `level at 1000 XP`() {
        val level = pointsManager.calculateLevel(1000)
        level shouldBe UserProfile.getLevelFromXP(1000)
    }

    @Test
    fun `level at 5000 XP`() {
        val level = pointsManager.calculateLevel(5000)
        level shouldBe UserProfile.getLevelFromXP(5000)
    }

    @Test
    fun `getXPForLevel is monotonically increasing`() {
        for (level in 1..20) {
            UserProfile.getXPForLevel(level + 1) shouldBeGreaterThan UserProfile.getXPForLevel(level)
        }
    }

    private infix fun Int.shouldBeGreaterThan(other: Int) {
        assert(this > other) { "Expected $this > $other" }
    }
}
