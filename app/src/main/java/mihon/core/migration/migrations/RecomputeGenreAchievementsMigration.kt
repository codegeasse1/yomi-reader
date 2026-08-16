package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.achievement.handler.AchievementHandler

/**
 * One-time recompute of achievement progress after fixing localized (Cyrillic)
 * genre matching.
 *
 * Existing libraries were under-counted because SQLite's built-in LOWER() only
 * folds ASCII case, so genre comparisons like "Гарем" vs "гарем" never matched
 * and genre achievements stayed at 0. After switching genre matching to a
 * Unicode-aware Kotlin path, this migration re-evaluates the affected
 * achievements once so users don't have to re-add anything by hand.
 *
 * The recompute is monotonic and never lowers progress or touches already
 * unlocked achievements, so it is safe to run unconditionally on upgrade.
 */
class RecomputeGenreAchievementsMigration : Migration {
    override val version = Migration.ALWAYS

    override suspend operator fun invoke(migrationContext: MigrationContext): Boolean {
        if (migrationContext.dryrun) return true
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val preference = preferenceStore.getBoolean("recompute_genre_achievements_v181_done", false)
        if (preference.get()) return true

        val handler = migrationContext.get<AchievementHandler>() ?: return false
        handler.recomputeGenreAchievements()

        preference.set(true)
        return true
    }
}
