package mihon.core.migration.migrations

import eu.kanade.domain.ui.model.AppTheme
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * Migration to set default values for Aurora anime/manga section preferences
 */
class AuroraSectionMigration : Migration {
    override val version = 131f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val appTheme = preferenceStore.getEnum("pref_app_theme", AppTheme.AURORA).get()

        if (!appTheme.isAuroraStyle) {
            return true
        }

        val animeSectionPref = preferenceStore.getBoolean("aurora_show_anime_section", true)
        if (!animeSectionPref.isSet()) {
            animeSectionPref.set(true)
        }

        val mangaSectionPref = preferenceStore.getBoolean("aurora_show_manga_section", true)
        if (!mangaSectionPref.isSet()) {
            mangaSectionPref.set(true)
        }

        return true
    }
}
