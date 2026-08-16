package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.toLegacyExtensionStore
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaExtensionRepoRestorer(
    private val mangaHandler: MangaDatabaseHandler = Injekt.get(),
    private val getExtensionRepos: GetMangaExtensionRepo = Injekt.get(),
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
            val store = ExtensionRepo(
                baseUrl = backupRepo.baseUrl,
                name = backupRepo.name,
                shortName = backupRepo.shortName,
                website = backupRepo.website,
                signingKeyFingerprint = backupRepo.signingKeyFingerprint,
            ).toLegacyExtensionStore()
            mangaHandler.await { db ->
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
