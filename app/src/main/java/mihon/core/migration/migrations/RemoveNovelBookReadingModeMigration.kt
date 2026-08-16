package mihon.core.migration.migrations

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReadingMode
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

/**
 * Drops the global "Book" novel reading mode.
 *
 * Continuous reading is now a per-title compiled book artifact instead of a reader-wide setting, so
 * the option is gone from the settings UI. Anyone still storing the old value would otherwise be
 * stuck in the legacy stitched-chapter mode with no way to leave it.
 */
class RemoveNovelBookReadingModeMigration : Migration {
    override val version = 188f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val preferences = migrationContext.get<NovelReaderPreferences>() ?: return@withIOContext false
        val readingMode = preferences.readingMode()
        if (readingMode.get() == NovelReadingMode.BOOK) {
            readingMode.set(NovelReadingMode.CHAPTERS)
        }
        return@withIOContext true
    }
}
