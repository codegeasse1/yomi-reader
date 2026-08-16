package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionStoreRestorer(
    private val animeHandler: AnimeDatabaseHandler = Injekt.get(),
    private val animeStoreRepository: AnimeExtensionStoreRepository = Injekt.get(),
) {

    suspend operator fun invoke(
        backupStore: BackupExtensionStore,
    ) {
        val dbStores = animeStoreRepository.getAll()
        val existingByIndex = dbStores.associateBy { it.indexUrl }
        // Placeholder keys are shared by every store without a reachable repo.json, so they are
        // not an identity: only real fingerprints may block a restore.
        val existingByKey = dbStores
            .filterNot { isPlaceholderSigningKey(it.signingKey) }
            .associateBy { it.signingKey }
        val indexExists = existingByIndex[backupStore.indexUrl]
        val keyExists = if (isPlaceholderSigningKey(backupStore.signingKey)) {
            null
        } else {
            existingByKey[backupStore.signingKey]
        }
        if (indexExists != null && indexExists.signingKey == backupStore.signingKey) {
            // Already present, e.g. the same backup restored twice: nothing to do.
            return
        }
        if (indexExists != null) {
            error("Already Exists with different signing key")
        } else if (keyExists != null) {
            error("${keyExists.name} has the same signing key")
        } else {
            animeHandler.await { db ->
                db.extension_storeQueries.upsert(
                    indexUrl = backupStore.indexUrl,
                    name = backupStore.name,
                    badgeLabel = backupStore.badgeLabel,
                    signingKey = backupStore.signingKey,
                    contactWebsite = backupStore.contactWebsite,
                    contactDiscord = backupStore.contactDiscord,
                    isLegacy = backupStore.isLegacy,
                    extensionListUrl = backupStore.extensionListUrl,
                )
            }
        }
    }
}
