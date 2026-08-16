package mihon.domain.extensionrepo.manga.interactor

import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.model.legacyBaseUrl

class DeleteMangaExtensionRepo(
    private val repository: MangaExtensionStoreRepository,
) {
    suspend fun await(baseUrl: String) {
        val normalized = baseUrl.trimEnd('/')
        val store = repository.getAll().find { it.legacyBaseUrl().trimEnd('/') == normalized }
        if (store != null) {
            repository.remove(store.indexUrl)
        }
    }
}
