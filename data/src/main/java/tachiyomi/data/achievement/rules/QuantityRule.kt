package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class QuantityRule(
    override val achievementId: String,
    private val category: AchievementCategory,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = when (event) {
            is AchievementEvent.ChapterRead ->
                category == AchievementCategory.MANGA ||
                    category == AchievementCategory.BOTH
            is AchievementEvent.EpisodeWatched ->
                category == AchievementCategory.ANIME ||
                    category == AchievementCategory.BOTH
            is AchievementEvent.NovelChapterRead ->
                category == AchievementCategory.NOVEL ||
                    category == AchievementCategory.BOTH
            else -> false
        }
        if (!isMatch) return RuleResult.NoChange

        val total = context.getChaptersRead(category)
        return RuleResult.Update(total)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return context.getChaptersRead(category)
    }
}
