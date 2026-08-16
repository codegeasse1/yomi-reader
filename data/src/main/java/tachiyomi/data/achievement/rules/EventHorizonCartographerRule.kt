package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class EventHorizonCartographerRule(
    override val achievementId: String = ACHIEVEMENT_ID,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.LibraryAdded && event !is AchievementEvent.LibraryRemoved) {
            return RuleResult.NoChange
        }
        return RuleResult.Update(evaluateFull(context))
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return eventHorizonCartographerProgress(
            mangaCount = context.getLibraryCount(AchievementCategory.MANGA),
            animeCount = context.getLibraryCount(AchievementCategory.ANIME),
            novelCount = context.getLibraryCount(AchievementCategory.NOVEL),
            sourceDiversity = context.getSourceDiversity(AchievementCategory.BOTH),
        )
    }

    companion object {
        const val ACHIEVEMENT_ID = "event_horizon_cartographer"
        const val REQUIRED_PER_REALM = 12
        const val REQUIRED_SOURCES = 12
        const val MAX_PROGRESS = 100
    }
}

internal fun eventHorizonCartographerProgress(
    mangaCount: Int,
    animeCount: Int,
    novelCount: Int,
    sourceDiversity: Int,
): Int {
    fun segment(value: Int, required: Int): Int = ((value.coerceIn(0, required) * 25f) / required).toInt()

    return segment(mangaCount, EventHorizonCartographerRule.REQUIRED_PER_REALM) +
        segment(animeCount, EventHorizonCartographerRule.REQUIRED_PER_REALM) +
        segment(novelCount, EventHorizonCartographerRule.REQUIRED_PER_REALM) +
        segment(sourceDiversity, EventHorizonCartographerRule.REQUIRED_SOURCES)
}
