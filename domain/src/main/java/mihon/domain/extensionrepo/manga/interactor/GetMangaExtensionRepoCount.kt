package mihon.domain.extensionrepo.manga.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository

class GetMangaExtensionRepoCount(
    private val repository: MangaExtensionStoreRepository,
) {
    fun subscribe(): Flow<Int> = repository.getCountAsFlow().map { it.toInt() }
}
