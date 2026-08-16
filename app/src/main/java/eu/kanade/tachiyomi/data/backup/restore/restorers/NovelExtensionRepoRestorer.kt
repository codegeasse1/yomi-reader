package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionstore.model.ExtensionStore
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionRepoRestorer(
    private val novelHandler: NovelDatabaseHandler = Injekt.get(),
    private val getExtensionRepos: GetNovelExtensionRepo = Injekt.get(),
) {

    suspend operator fun invoke(
        backupRepo: BackupExtensionRepos,
    ) {
        val dbRepos = getExtensionRepos.getAll()
        // Placeholder fingerprints are shared by every repo added without one, so they are not an
        // identity: only real fingerprints may block a restore.
        val existingReposBySHA = dbRepos
            .filterNot { isPlaceholderSigningKey(it.signingKeyFingerprint) }
            .associateBy { it.signingKeyFingerprint }
        val existingReposByUrl = dbRepos.associateBy { it.baseUrl }
        val urlExists = existingReposByUrl[backupRepo.baseUrl]
        val shaExists = if (isPlaceholderSigningKey(backupRepo.signingKeyFingerprint)) {
            null
        } else {
            existingReposBySHA[backupRepo.signingKeyFingerprint]
        }
        if (urlExists != null && urlExists.signingKeyFingerprint == backupRepo.signingKeyFingerprint) {
            // Already present, e.g. the same backup restored twice: nothing to do.
            return
        }
        if (urlExists != null) {
            error("Already Exists with different signing key fingerprint")
        } else if (shaExists != null) {
            error("${shaExists.name} has the same signing key fingerprint")
        } else {
            val store = ExtensionStore(
                indexUrl = backupRepo.baseUrl,
                name = backupRepo.name,
                badgeLabel = backupRepo.shortName ?: backupRepo.name,
                signingKey = backupRepo.signingKeyFingerprint,
                contact = ExtensionStore.Contact(
                    website = backupRepo.website,
                    discord = null,
                ),
                isLegacy = true,
                extensionListUrl = null,
            )
            novelHandler.await { db ->
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
        }
    }
}
