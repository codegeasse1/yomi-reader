package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult
import kotlin.math.min

class BalancedRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch =
            event is AchievementEvent.ChapterRead ||
                event is AchievementEvent.EpisodeWatched ||
                event is AchievementEvent.NovelChapterRead
        if (!isMatch) return RuleResult.NoChange

        val manga = context.getChaptersRead(AchievementCategory.MANGA)
        val anime = context.getChaptersRead(AchievementCategory.ANIME)
        val valMin = min(manga, anime)
        return RuleResult.Update(valMin)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        val manga = context.getChaptersRead(AchievementCategory.MANGA)
        val anime = context.getChaptersRead(AchievementCategory.ANIME)
        return min(manga, anime)
    }
}
