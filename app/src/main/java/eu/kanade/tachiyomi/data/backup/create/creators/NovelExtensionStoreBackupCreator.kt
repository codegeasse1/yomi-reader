package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.backupExtensionStoreMapper
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelExtensionStoreBackupCreator(
    private val novelStoreRepository: NovelExtensionStoreRepository = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupExtensionStore> {
        return novelStoreRepository.getAll()
            .map(backupExtensionStoreMapper)
    }
}
