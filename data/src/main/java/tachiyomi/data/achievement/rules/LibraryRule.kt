package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class LibraryRule(
    override val achievementId: String,
    private val category: AchievementCategory,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = event is AchievementEvent.LibraryAdded || event is AchievementEvent.LibraryRemoved
        if (!isMatch) return RuleResult.NoChange

        val total = context.getLibraryCount(category)
        return RuleResult.Update(total)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return context.getLibraryCount(category)
    }
}
