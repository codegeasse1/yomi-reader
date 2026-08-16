package mihon.domain.extensionrepo.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.toExtensionRepo

class GetMangaExtensionRepo(
    private val repository: MangaExtensionStoreRepository,
) {
    fun subscribeAll(): Flow<List<ExtensionRepo>> {
        return repository.getAllAsFlow().map { stores -> stores.map { it.toExtensionRepo() } }
    }

    suspend fun getAll(): List<ExtensionRepo> {
        repository.ensureLegacyMigrated()
        return repository.getAll().map { it.toExtensionRepo() }
    }
}
