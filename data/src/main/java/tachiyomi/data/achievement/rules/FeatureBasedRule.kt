package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class FeatureBasedRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        if (event !is AchievementEvent.FeatureUsed) return RuleResult.NoChange

        val isFeatureMatch = when (achievementId) {
            "download_starter", "chapter_collector", "trophy_hunter" -> {
                event.feature == AchievementEvent.Feature.DOWNLOAD
            }
            "search_user" -> {
                event.feature == AchievementEvent.Feature.SEARCH ||
                    event.feature == AchievementEvent.Feature.ADVANCED_SEARCH
            }
            "advanced_explorer" -> {
                event.feature == AchievementEvent.Feature.ADVANCED_SEARCH
            }
            "filter_master" -> {
                event.feature == AchievementEvent.Feature.FILTER
            }
            "backup_master" -> {
                event.feature == AchievementEvent.Feature.BACKUP
            }
            "settings_explorer" -> {
                event.feature == AchievementEvent.Feature.SETTINGS
            }
            "stats_viewer" -> {
                event.feature == AchievementEvent.Feature.STATS
            }
            "theme_changer" -> {
                event.feature == AchievementEvent.Feature.THEME_CHANGE
            }
            "persistent_clicker" -> {
                event.feature == AchievementEvent.Feature.LOGO_CLICK
            }
            "secret_hall_unlocked" -> {
                event.feature == AchievementEvent.Feature.SECRET_HALL_UNLOCKED
            }
            else -> false
        }
        if (!isFeatureMatch) return RuleResult.NoChange

        val count = getCount(context)
        return RuleResult.Update(count)
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return getCount(context)
    }

    private suspend fun getCount(context: RuleContext): Int {
        return when (achievementId) {
            "download_starter", "chapter_collector", "trophy_hunter" -> {
                context.getFeatureCount(AchievementEvent.Feature.DOWNLOAD)
            }
            "search_user" -> {
                val search = context.getFeatureCount(AchievementEvent.Feature.SEARCH)
                val advanced = context.getFeatureCount(AchievementEvent.Feature.ADVANCED_SEARCH)
                search + advanced
            }
            "advanced_explorer" -> {
                context.getFeatureCount(AchievementEvent.Feature.ADVANCED_SEARCH)
            }
            "filter_master" -> {
                context.getFeatureCount(AchievementEvent.Feature.FILTER)
            }
            "backup_master" -> {
                context.getFeatureCount(AchievementEvent.Feature.BACKUP)
            }
            "settings_explorer" -> {
                context.getFeatureCount(AchievementEvent.Feature.SETTINGS)
            }
            "stats_viewer" -> {
                context.getFeatureCount(AchievementEvent.Feature.STATS)
            }
            "theme_changer" -> {
                context.getFeatureCount(AchievementEvent.Feature.THEME_CHANGE)
            }
            "persistent_clicker" -> {
                context.getFeatureCount(AchievementEvent.Feature.LOGO_CLICK)
            }
            "secret_hall_unlocked" -> {
                context.getFeatureCount(AchievementEvent.Feature.SECRET_HALL_UNLOCKED)
            }
            else -> 0
        }
    }
}
