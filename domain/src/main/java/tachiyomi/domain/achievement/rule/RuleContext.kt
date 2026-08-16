package tachiyomi.domain.achievement.rule

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent

interface RuleContext {
    suspend fun getChaptersRead(category: AchievementCategory): Int
    suspend fun getLibraryCount(category: AchievementCategory): Int
    suspend fun getCompletedCount(category: AchievementCategory): Int
    suspend fun getOngoingCount(category: AchievementCategory): Int
    suspend fun getGenreDiversity(category: AchievementCategory): Int
    suspend fun getSourceDiversity(category: AchievementCategory): Int
    suspend fun hasCompletedWithMinChapters(category: AchievementCategory, minChapters: Int): Boolean
    suspend fun hasLibraryGenre(genre: String): Int
    suspend fun hasLibraryTitleLike(pattern: String): Boolean
    suspend fun getCurrentStreak(): Int
    suspend fun hasSessionInTimeRange(startHour: Int, endHour: Int): Boolean
    suspend fun getFeatureCount(feature: AchievementEvent.Feature): Int
    suspend fun getMaxSessionDuration(): Long
    suspend fun getUnlockedAchievementsCountExcluding(metaIds: Set<String>): Int
    suspend fun getCurrentPoints(): Int
}
