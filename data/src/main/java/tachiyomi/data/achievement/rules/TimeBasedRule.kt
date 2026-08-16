package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class TimeBasedRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = when (achievementId) {
            "night_owl" -> event is AchievementEvent.AppStart || event is AchievementEvent.SessionEnd
            "early_bird" -> event is AchievementEvent.AppStart || event is AchievementEvent.SessionEnd
            "marathon_reader" -> event is AchievementEvent.SessionEnd
            else -> false
        }
        if (!isMatch) return RuleResult.NoChange

        val progress = checkProgress(context)
        return RuleResult.Update(progress)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return checkProgress(context)
    }

    private suspend fun checkProgress(context: RuleContext): Int {
        return when (achievementId) {
            "night_owl" -> if (context.hasSessionInTimeRange(2, 5)) 1 else 0
            "early_bird" -> if (context.hasSessionInTimeRange(6, 9)) 1 else 0
            "marathon_reader" -> {
                val maxDuration = context.getMaxSessionDuration()
                val targetDuration = 2 * 60 * 60 * 1000L // 2 hours
                if (maxDuration >= targetDuration) 1 else 0
            }
            else -> 0
        }
    }
}
