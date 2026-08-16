package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class MetaRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val count = context.getUnlockedAchievementsCountExcluding(META_IDS)
        return RuleResult.Update(count)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return context.getUnlockedAchievementsCountExcluding(META_IDS)
    }

    companion object {
        val META_IDS = setOf(
            "master_achiever",
            "achievement_hunter",
            "achievement_collector",
            "achievement_completionist",
        )
    }
}
