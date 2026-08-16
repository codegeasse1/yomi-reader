package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class ChadRule : AchievementRule {
    override val achievementId: String = "secret_chad"

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = event is AchievementEvent.MangaCompleted ||
            event is AchievementEvent.AnimeCompleted ||
            event is AchievementEvent.NovelCompleted
        if (!isMatch) return RuleResult.NoChange

        val isChad = checkChad(context)
        return if (isChad) RuleResult.Update(1) else RuleResult.NoChange
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return if (checkChad(context)) 1 else 0
    }

    private suspend fun checkChad(context: RuleContext): Boolean {
        val completed = context.getCompletedCount(AchievementCategory.BOTH)
        val ongoing = context.getOngoingCount(AchievementCategory.BOTH)
        return completed >= 10 && ongoing == 0
    }
}
