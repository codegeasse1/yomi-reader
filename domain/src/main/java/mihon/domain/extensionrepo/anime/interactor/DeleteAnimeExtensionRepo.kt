package mihon.domain.extensionrepo.anime.interactor

import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.model.legacyBaseUrl

class DeleteAnimeExtensionRepo(
    private val repository: AnimeExtensionStoreRepository,
) {
    suspend fun await(baseUrl: String) {
        val normalized = baseUrl.trimEnd('/')
        val store = repository.getAll().find { it.legacyBaseUrl().trimEnd('/') == normalized }
        if (store != null) {
            repository.remove(store.indexUrl)
        }
    }
}
