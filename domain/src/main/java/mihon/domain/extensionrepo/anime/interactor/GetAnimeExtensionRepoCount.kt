package mihon.domain.extensionrepo.anime.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository

class GetAnimeExtensionRepoCount(
    private val repository: AnimeExtensionStoreRepository,
) {
    fun subscribe(): Flow<Int> = repository.getCountAsFlow().map { it.toInt() }
}
