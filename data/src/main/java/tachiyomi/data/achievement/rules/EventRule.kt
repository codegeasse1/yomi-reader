package tachiyomi.data.achievement.rules

import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.rule.AchievementRule
import tachiyomi.domain.achievement.rule.RuleContext
import tachiyomi.domain.achievement.rule.RuleResult

class EventRule(
    override val achievementId: String,
) : AchievementRule {

    override suspend fun evaluateDelta(
        event: AchievementEvent,
        currentProgress: Int,
        context: RuleContext,
    ): RuleResult {
        val isMatch = when (achievementId) {
            "first_chapter" -> event is AchievementEvent.ChapterRead
            "first_episode" -> event is AchievementEvent.EpisodeWatched
            "first_novel_chapter" -> event is AchievementEvent.NovelChapterRead
            "complete_1_manga" -> event is AchievementEvent.MangaCompleted
            "complete_1_anime" -> event is AchievementEvent.AnimeCompleted
            "complete_1_novel" -> event is AchievementEvent.NovelCompleted
            "read_long_manga" -> event is AchievementEvent.MangaCompleted
            "read_long_novel" -> event is AchievementEvent.NovelCompleted
            else -> false
        }
        if (!isMatch) return RuleResult.NoChange

        return when (achievementId) {
            "read_long_manga", "read_long_novel" -> RuleResult.Update(checkProgress(context))
            else -> RuleResult.Update(1)
        }
    }

    override suspend fun evaluateFull(context: RuleContext): Int {
        return checkProgress(context)
    }

    private suspend fun checkProgress(context: RuleContext): Int {
        return when (achievementId) {
            "first_chapter" -> if (context.getChaptersRead(AchievementCategory.MANGA) > 0) 1 else 0
            "first_episode" -> if (context.getChaptersRead(AchievementCategory.ANIME) > 0) 1 else 0
            "first_novel_chapter" -> if (context.getChaptersRead(AchievementCategory.NOVEL) > 0) 1 else 0
            "complete_1_manga" -> if (context.getCompletedCount(AchievementCategory.MANGA) > 0) 1 else 0
            "complete_1_anime" -> if (context.getCompletedCount(AchievementCategory.ANIME) > 0) 1 else 0
            "complete_1_novel" -> if (context.getCompletedCount(AchievementCategory.NOVEL) > 0) 1 else 0
            "read_long_manga" -> if (context.hasCompletedWithMinChapters(AchievementCategory.MANGA, 200)) 1 else 0
            "read_long_novel" -> if (context.hasCompletedWithMinChapters(AchievementCategory.NOVEL, 200)) 1 else 0
            else -> 0
        }
    }
}
