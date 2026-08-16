package mihon.domain.extensionrepo.anime.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.toExtensionRepo

class GetAnimeExtensionRepo(
    private val repository: AnimeExtensionStoreRepository,
) {
    fun subscribeAll(): Flow<List<ExtensionRepo>> {
        return repository.getAllAsFlow().map { stores -> stores.map { it.toExtensionRepo() } }
    }

    suspend fun getAll(): List<ExtensionRepo> {
        repository.ensureLegacyMigrated()
        return repository.getAll().map { it.toExtensionRepo() }
    }
}
