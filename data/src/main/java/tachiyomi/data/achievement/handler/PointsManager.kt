package tachiyomi.data.achievement.handler

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.data.achievement.User_profile
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.domain.achievement.model.UserPoints
import tachiyomi.domain.achievement.model.UserProfile

class PointsManager(
    private val database: AchievementsDatabase,
) {

    private val mutationMutex = Mutex()

    init {
        // Initialize stats on creation
        initializeStats()
    }

    private fun initializeStats() {
        // Create default profile ONLY if doesn't exist (won't overwrite restored data)
        database.userProfileQueries.insertProfileIfNotExists(
            user_id = "default",
            username = null,
            level = 1,
            current_xp = 0,
            xp_to_next_level = 100,
            total_xp = 0,
            titles = "[]",
            badges = "[]",
            unlocked_themes = "[]",
            achievements_unlocked = 0,
            total_achievements = 0,
            join_date = System.currentTimeMillis(),
            last_updated = System.currentTimeMillis(),
        )
    }

    suspend fun addPoints(points: Int) {
        if (points > 0) {
            mutationMutex.withLock {
                withContext(Dispatchers.IO) {
                    database.userProfileQueries.addXPAtomic(
                        user_id = "default",
                        xp_delta = points.toLong(),
                        last_updated = System.currentTimeMillis(),
                    )
                    recalculateLevel()
                }
            }
        }
    }

    suspend fun incrementUnlocked() {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                database.userProfileQueries.incrementAchievementUnlocked(
                    user_id = "default",
                    last_updated = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun recalculateLevel() {
        val profile = database.userProfileQueries.getDefaultProfile().executeAsOneOrNull() ?: return
        val newLevel = calculateLevel(profile.total_xp.toInt())
        if (profile.level.toInt() != newLevel) {
            val xpSpentForCurrentLevel = (1..newLevel).sumOf { UserProfile.getXPForLevel(it) }
            val currentXP = (profile.total_xp.toInt() - xpSpentForCurrentLevel).coerceAtLeast(0)
            val xpToNextLevel = UserProfile.getXPForLevel(newLevel + 1)
            database.userProfileQueries.updateXP(
                user_id = "default",
                total_xp = profile.total_xp,
                current_xp = currentXP.toLong(),
                level = newLevel.toLong(),
                xp_to_next_level = xpToNextLevel.toLong(),
                last_updated = System.currentTimeMillis(),
            )
        }
    }

    fun subscribeToPoints(): Flow<UserPoints> {
        return database.userProfileQueries.getDefaultProfile()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { profile: User_profile? ->
                if (profile != null) {
                    UserPoints(
                        totalPoints = profile.total_xp.toInt(),
                        level = profile.level.toInt(),
                        achievementsUnlocked = profile.achievements_unlocked.toInt(),
                    )
                } else {
                    UserPoints()
                }
            }
    }

    suspend fun getCurrentPoints(): UserPoints {
        return withContext(Dispatchers.IO) {
            try {
                val profile: User_profile? = database.userProfileQueries.getDefaultProfile()
                    .executeAsOneOrNull()

                if (profile != null) {
                    UserPoints(
                        totalPoints = profile.total_xp.toInt(),
                        level = profile.level.toInt(),
                        achievementsUnlocked = profile.achievements_unlocked.toInt(),
                    )
                } else {
                    initializeStats()
                    UserPoints()
                }
            } catch (e: Exception) {
                initializeStats()
                UserPoints()
            }
        }
    }

    fun calculateLevel(points: Int): Int {
        return UserProfile.getLevelFromXP(points)
    }
}
