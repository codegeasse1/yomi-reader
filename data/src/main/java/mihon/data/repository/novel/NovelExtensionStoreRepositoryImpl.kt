package mihon.data.repository.novel

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.data.extension.repository.extensionStoreMapper
import mihon.data.extension.service.ExtensionStoreService
import mihon.data.repository.LegacyExtensionStorePortGuard
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.novel.data.NovelDatabase

class NovelExtensionStoreRepositoryImpl(
    private val handler: NovelDatabaseHandler,
    private val service: ExtensionStoreService,
    preferenceStore: PreferenceStore,
) : NovelExtensionStoreRepository {

    private val legacyPortGuard = LegacyExtensionStorePortGuard(preferenceStore, LEGACY_PORT_KEY)

    override suspend fun insert(indexUrl: String): Result<Unit> {
        return service.fetch(indexUrl).mapCatching { upsert(it) }
    }

    override suspend fun insertFromPreference(indexUrl: String, name: String) {
        handler.await { db ->
            db.extension_storeQueries.upsert(
                indexUrl = indexUrl,
                name = name,
                badgeLabel = name,
                signingKey = "NO_SIGNING_KEY",
                contactWebsite = indexUrl,
                contactDiscord = null,
                isLegacy = true,
                extensionListUrl = null,
            )
        }
    }

    override suspend fun refreshAll() {
        try {
            val stores = handler.awaitList { db -> db.extension_storeQueries.getAll(::extensionStoreMapper) }
                .filterNot { it.isLegacy }
            supervisorScope {
                stores.map { store ->
                    async {
                        service.fetch(store.indexUrl)
                            .mapCatching { fetched ->
                                handler.await(inTransaction = true) { db ->
                                    upsert(db, fetched)
                                    if (store.indexUrl != fetched.indexUrl) {
                                        db.extension_storeQueries.delete(store.indexUrl)
                                    }
                                }
                            }
                            .onFailure {
                                logcat(LogPriority.ERROR, it) {
                                    "Failed to refresh extension store '${store.name} (${store.indexUrl})'"
                                }
                            }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    private suspend fun upsert(store: ExtensionStore) {
        handler.await { db -> upsert(db, store) }
    }

    private fun upsert(db: NovelDatabase, store: ExtensionStore) {
        db.extension_storeQueries.upsert(
            indexUrl = store.indexUrl,
            name = store.name,
            badgeLabel = store.badgeLabel,
            signingKey = store.signingKey,
            contactWebsite = store.contact.website,
            contactDiscord = store.contact.discord,
            isLegacy = store.isLegacy,
            extensionListUrl = store.extensionListUrl,
        )
    }

    override suspend fun upsertStore(store: ExtensionStore) {
        upsert(store)
    }

    override suspend fun setCustomName(indexUrl: String, customName: String?) {
        handler.await { db -> db.extension_storeQueries.setCustomName(customName, indexUrl) }
    }

    override suspend fun getAll(): List<ExtensionStore> {
        migrateLegacyIfNeeded()
        return handler.awaitList { db -> db.extension_storeQueries.getAll(::extensionStoreMapper) }
    }

    /**
     * One-time port from legacy novel_extension_repos. Reachable from [getAll] because extension
     * loading resolves trusted fingerprints through it, and that can happen before the app migrator
     * runs.
     */
    override suspend fun ensureLegacyMigrated() {
        migrateLegacyIfNeeded()
    }

    private suspend fun migrateLegacyIfNeeded() {
        legacyPortGuard.runOnce(
            storeCount = { handler.awaitOneOrNull { db -> db.extension_storeQueries.getCount() } ?: 0L },
            port = {
                handler.awaitList { db ->
                    db.novel_extension_reposQueries.findAll { baseUrl, name, shortName, website, fingerprint ->
                        ExtensionStore(
                            indexUrl = baseUrl,
                            name = name,
                            badgeLabel = shortName ?: name,
                            signingKey = fingerprint,
                            contact = ExtensionStore.Contact(
                                website = website,
                                discord = null,
                            ),
                            isLegacy = true,
                            extensionListUrl = null,
                        )
                    }
                }.forEach { store ->
                    upsertStore(store)
                }
            },
        )
    }

    override fun getAllAsFlow(): Flow<List<ExtensionStore>> {
        return handler.subscribeToList { db ->
            db.extension_storeQueries.getAll(::extensionStoreMapper)
        }
    }

    override fun getCountAsFlow(): Flow<Long> {
        return handler.subscribeToOne { db -> db.extension_storeQueries.getCount() }
    }

    override suspend fun remove(indexUrl: String) {
        handler.await { db -> db.extension_storeQueries.delete(indexUrl) }
    }

    private companion object {
        const val LEGACY_PORT_KEY = "novel_extension_repo_ported_to_store"
    }
}
