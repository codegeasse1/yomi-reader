package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.backupExtensionStoreMapper
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaExtensionStoreBackupCreator(
    private val mangaStoreRepository: MangaExtensionStoreRepository = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupExtensionStore> {
        return mangaStoreRepository.getAll()
            .map(backupExtensionStoreMapper)
    }
}
