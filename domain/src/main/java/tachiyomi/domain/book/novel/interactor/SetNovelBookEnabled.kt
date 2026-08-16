package tachiyomi.domain.book.novel.interactor

import tachiyomi.domain.book.novel.repository.NovelBookStateRepository

/** Switches a single title between book reading and per-chapter reading. */
class SetNovelBookEnabled(
    private val repository: NovelBookStateRepository,
) {
    suspend fun await(novelId: Long, enabled: Boolean, now: Long = System.currentTimeMillis()) {
        repository.setBookEnabled(novelId = novelId, enabled = enabled, updatedAt = now)
    }
}
