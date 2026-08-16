package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class CompletionCountRule(
    override val achievementId: String,
    private val category: AchievementCategory,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = when (event) {
            is AchievementEvent.MangaCompleted ->
                category == AchievementCategory.MANGA ||
                    category == AchievementCategory.BOTH
            is AchievementEvent.AnimeCompleted ->
                category == AchievementCategory.ANIME ||
                    category == AchievementCategory.BOTH
            is AchievementEvent.NovelCompleted ->
                category == AchievementCategory.NOVEL ||
                    category == AchievementCategory.BOTH
            else -> false
        }
        if (!isMatch) return RuleResult.NoChange
        return RuleResult.Update(context.getCompletedCount(category))
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return context.getCompletedCount(category)
    }
}
