package tachiyomi.domain.book.novel.interactor

import tachiyomi.domain.book.novel.repository.NovelBookStateRepository

class DeleteNovelBookState(
    private val repository: NovelBookStateRepository,
) {
    suspend fun await(novelId: Long) {
        repository.deleteBookState(novelId)
    }

    suspend fun awaitAll() {
        repository.deleteAllBookStates()
    }
}
