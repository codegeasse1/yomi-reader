package tachiyomi.domain.book.novel.interactor

import tachiyomi.domain.book.novel.model.NovelBookState
import tachiyomi.domain.book.novel.repository.NovelBookStateRepository

class UpsertNovelBookState(
    private val repository: NovelBookStateRepository,
) {
    suspend fun await(state: NovelBookState) {
        repository.upsertBookState(state)
    }
}
