package mihon.domain.extensionrepo.anime.interactor

import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository

class UpdateAnimeExtensionRepo(
    private val repository: AnimeExtensionStoreRepository,
) {
    suspend fun awaitAll() {
        repository.refreshAll()
    }
}
