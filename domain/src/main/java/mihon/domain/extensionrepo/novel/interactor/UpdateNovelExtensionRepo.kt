package mihon.domain.extensionrepo.novel.interactor

import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository

class UpdateNovelExtensionRepo(
    private val repository: NovelExtensionStoreRepository,
) {
    suspend fun awaitAll() {
        repository.refreshAll()
    }
}
