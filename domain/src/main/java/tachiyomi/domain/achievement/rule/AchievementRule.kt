package tachiyomi.domain.achievement.rule

import tachiyomi.domain.achievement.model.AchievementEvent

sealed interface RuleResult {
    data class Update(val newProgress: Int) : RuleResult
    object NoChange : RuleResult
}

interface AchievementRule {
    val achievementId: String

    suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult

    suspend fun evaluateFull(
        context: RuleContext,
    ): Int
}
