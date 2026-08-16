package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.backupExtensionStoreMapper
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionStoreBackupCreator(
    private val animeStoreRepository: AnimeExtensionStoreRepository = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupExtensionStore> {
        return animeStoreRepository.getAll()
            .map(backupExtensionStoreMapper)
    }
}
