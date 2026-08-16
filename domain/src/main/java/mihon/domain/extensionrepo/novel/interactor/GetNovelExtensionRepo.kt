package mihon.domain.extensionrepo.novel.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import mihon.domain.extensionstore.toExtensionRepo

class GetNovelExtensionRepo(
    private val repository: NovelExtensionStoreRepository,
) {
    fun subscribeAll(): Flow<List<ExtensionRepo>> {
        return repository.getAllAsFlow().map { stores -> stores.map { it.toExtensionRepo() } }
    }

    suspend fun getAll(): List<ExtensionRepo> {
        repository.ensureLegacyMigrated()
        return repository.getAll().map { it.toExtensionRepo() }
    }
}
