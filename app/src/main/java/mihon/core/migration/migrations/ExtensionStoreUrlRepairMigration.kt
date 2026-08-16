package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.model.collapseDuplicateExtensionStoreSuffix
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import tachiyomi.core.common.util.lang.withIOContext

/**
 * Repairs extension store index urls that were persisted with a duplicated index file suffix,
 * such as `https://.../extensions/repo/repo.json/repo.json`.
 *
 * Those rows were written by the legacy repo -> store port, which appended `/repo.json` to a
 * base url that already contained it. Every store refresh and extension list fetch for such a
 * row fails with HTTP 404, so the affected repos show no extensions at all.
 *
 * Runs on every launch because the malformed rows can also arrive through backup restore, and
 * is a no-op (three small selects) once all stored urls are canonical.
 */
class ExtensionStoreUrlRepairMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val mangaRepo = migrationContext.get<MangaExtensionStoreRepository>() ?: return@withIOContext false
        val animeRepo = migrationContext.get<AnimeExtensionStoreRepository>() ?: return@withIOContext false
        val novelRepo = migrationContext.get<NovelExtensionStoreRepository>() ?: return@withIOContext false

        repair(mangaRepo.getAll(), { mangaRepo.upsertStore(it) }, { mangaRepo.remove(it) })
        repair(animeRepo.getAll(), { animeRepo.upsertStore(it) }, { animeRepo.remove(it) })
        repair(novelRepo.getAll(), { novelRepo.upsertStore(it) }, { novelRepo.remove(it) })

        true
    }

    private suspend fun repair(
        stores: List<ExtensionStore>,
        upsert: suspend (ExtensionStore) -> Unit,
        remove: suspend (String) -> Unit,
    ) {
        stores.forEach { store ->
            val canonical = store.indexUrl.collapseDuplicateExtensionStoreSuffix()
            if (canonical == store.indexUrl) return@forEach

            val conflicts = stores.any { it !== store && it.indexUrl == canonical }
            if (!conflicts) {
                upsert(store.copy(indexUrl = canonical))
            }
            remove(store.indexUrl)
        }
    }
}
