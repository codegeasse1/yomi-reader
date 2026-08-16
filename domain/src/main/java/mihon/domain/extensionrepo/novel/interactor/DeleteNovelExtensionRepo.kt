package mihon.domain.extensionrepo.novel.interactor

import mihon.domain.extensionstore.model.legacyBaseUrl
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository

class DeleteNovelExtensionRepo(
    private val repository: NovelExtensionStoreRepository,
) {
    suspend fun await(baseUrl: String) {
        val normalized = baseUrl.trimEnd('/')
        val store = repository.getAll().find { it.legacyBaseUrl().trimEnd('/') == normalized }
        if (store != null) {
            repository.remove(store.indexUrl)
        }
    }
}
