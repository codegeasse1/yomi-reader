package mihon.core.migration.migrations

import eu.kanade.domain.source.service.SourcePreferences
import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class TrustExtensionRepositoryMigration : Migration {
    override val version: Float = 7f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val sourcePreferences = migrationContext.get<SourcePreferences>() ?: return@withIOContext false

        val animeExtensionStoreRepository =
            migrationContext.get<AnimeExtensionStoreRepository>() ?: return@withIOContext false
        for ((index, source) in sourcePreferences.animeExtensionRepos().get().withIndex()) {
            try {
                animeExtensionStoreRepository.insertFromPreference(
                    source,
                    "Repo #${index + 1}",
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Error Migrating Extension Repo with baseUrl: $source" }
            }
        }
        sourcePreferences.animeExtensionRepos().delete()

        val mangaExtensionStoreRepository =
            migrationContext.get<MangaExtensionStoreRepository>() ?: return@withIOContext false
        for ((index, source) in sourcePreferences.mangaExtensionRepos().get().withIndex()) {
            try {
                mangaExtensionStoreRepository.insertFromPreference(
                    source,
                    "Repo #${index + 1}",
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) {
                    "Error Migrating Manga Extension Repo with baseUrl: $source"
                }
            }
        }
        sourcePreferences.mangaExtensionRepos().delete()

        return@withIOContext true
    }
}
