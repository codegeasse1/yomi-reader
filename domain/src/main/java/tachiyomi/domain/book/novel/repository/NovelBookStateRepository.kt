package tachiyomi.domain.book.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.book.novel.model.NovelBookState

interface NovelBookStateRepository {

    suspend fun getBookState(novelId: Long): NovelBookState?

    fun subscribeBookState(novelId: Long): Flow<NovelBookState?>

    fun subscribeEnabledBookStates(): Flow<List<NovelBookState>>

    suspend fun getAllBookStates(): List<NovelBookState>

    suspend fun upsertBookState(state: NovelBookState)

    suspend fun setBookEnabled(novelId: Long, enabled: Boolean, updatedAt: Long)

    suspend fun setBookProgress(
        novelId: Long,
        charOffset: Long,
        lastChapterId: Long?,
        blockIndex: Int,
        chapterCharOffset: Int,
        updatedAt: Long,
    )

    suspend fun deleteBookState(novelId: Long)

    suspend fun deleteAllBookStates()
}
