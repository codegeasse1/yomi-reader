package mihon.domain.extensionrepo.novel.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository

class GetNovelExtensionRepoCount(
    private val repository: NovelExtensionStoreRepository,
) {
    fun subscribe(): Flow<Int> = repository.getCountAsFlow().map { it.toInt() }
}
