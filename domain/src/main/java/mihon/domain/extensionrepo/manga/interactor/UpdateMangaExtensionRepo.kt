package mihon.domain.extensionrepo.manga.interactor

import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository

class UpdateMangaExtensionRepo(
    private val repository: MangaExtensionStoreRepository,
) {
    suspend fun awaitAll() {
        repository.refreshAll()
    }
}
