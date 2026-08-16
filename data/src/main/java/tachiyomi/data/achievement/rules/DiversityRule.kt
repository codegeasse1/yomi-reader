package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class DiversityRule(
    override val achievementId: String,
    private val category: AchievementCategory,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = event is AchievementEvent.LibraryAdded
        if (!isMatch) return RuleResult.NoChange

        val total = if (achievementId.contains("genre", ignoreCase = true)) {
            context.getGenreDiversity(category)
        } else {
            context.getSourceDiversity(category)
        }
        return RuleResult.Update(total)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return if (achievementId.contains("genre", ignoreCase = true)) {
            context.getGenreDiversity(category)
        } else {
            context.getSourceDiversity(category)
        }
    }
}
