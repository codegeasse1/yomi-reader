package tachiyomi.data.book.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.book.novel.model.NovelBookState
import tachiyomi.domain.book.novel.repository.NovelBookStateRepository

class NovelBookStateRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelBookStateRepository {

    override suspend fun getBookState(novelId: Long): NovelBookState? {
        return handler.awaitOneOrNull { db -> db.novel_booksQueries.getBook(novelId, novelBookStateMapper) }
    }

    override fun subscribeBookState(novelId: Long): Flow<NovelBookState?> {
        return handler.subscribeToOneOrNull { db -> db.novel_booksQueries.getBook(novelId, novelBookStateMapper) }
    }

    override fun subscribeEnabledBookStates(): Flow<List<NovelBookState>> {
        return handler.subscribeToList { db -> db.novel_booksQueries.getEnabledBooks(novelBookStateMapper) }
    }

    override suspend fun getAllBookStates(): List<NovelBookState> {
        return handler.awaitList { db -> db.novel_booksQueries.getAllBooks(novelBookStateMapper) }
    }

    override suspend fun upsertBookState(state: NovelBookState) {
        handler.await { db ->
            db.novel_booksQueries.upsert(
                novelId = state.novelId,
                enabled = state.enabled,
                bookVersion = state.bookVersion,
                sourceId = state.sourceId,
                chapterSetHash = state.chapterSetHash,
                totalChars = state.totalChars,
                chapterCount = state.chapterCount.toLong(),
                charOffset = state.charOffset,
                lastChapterId = state.lastChapterId,
                blockIndex = state.blockIndex.toLong(),
                chapterCharOffset = state.chapterCharOffset.toLong(),
                progressMigrated = state.progressMigrated,
                complete = state.complete,
                builtAt = state.builtAt,
                updatedAt = state.updatedAt,
            )
        }
    }

    override suspend fun setBookEnabled(novelId: Long, enabled: Boolean, updatedAt: Long) {
        handler.await { db ->
            db.novel_booksQueries.setEnabled(
                enabled = enabled,
                updatedAt = updatedAt,
                novelId = novelId,
            )
        }
    }

    override suspend fun setBookProgress(
        novelId: Long,
        charOffset: Long,
        lastChapterId: Long?,
        blockIndex: Int,
        chapterCharOffset: Int,
        updatedAt: Long,
    ) {
        handler.await { db ->
            db.novel_booksQueries.setProgress(
                charOffset = charOffset,
                lastChapterId = lastChapterId,
                blockIndex = blockIndex.toLong(),
                chapterCharOffset = chapterCharOffset.toLong(),
                updatedAt = updatedAt,
                novelId = novelId,
            )
        }
    }

    override suspend fun deleteBookState(novelId: Long) {
        handler.await { db -> db.novel_booksQueries.delete(novelId) }
    }

    override suspend fun deleteAllBookStates() {
        handler.await { db -> db.novel_booksQueries.deleteAll() }
    }
}
