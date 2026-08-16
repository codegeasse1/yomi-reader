package tachiyomi.domain.book.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.book.novel.model.NovelBookState
import tachiyomi.domain.book.novel.repository.NovelBookStateRepository

class GetNovelBookState(
    private val repository: NovelBookStateRepository,
) {
    suspend fun await(novelId: Long): NovelBookState? {
        return repository.getBookState(novelId)
    }

    fun subscribe(novelId: Long): Flow<NovelBookState?> {
        return repository.subscribeBookState(novelId)
    }

    fun subscribeEnabled(): Flow<List<NovelBookState>> {
        return repository.subscribeEnabledBookStates()
    }

    suspend fun awaitAll(): List<NovelBookState> {
        return repository.getAllBookStates()
    }
}
