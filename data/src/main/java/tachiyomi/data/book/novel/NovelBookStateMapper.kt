package tachiyomi.data.book.novel

import tachiyomi.domain.book.novel.model.NovelBookState

val novelBookStateMapper: (
    Long,
    Boolean,
    Long,
    Long,
    String,
    Long,
    Long,
    Long,
    Long?,
    Long,
    Long,
    Boolean,
    Boolean,
    Long,
    Long,
) -> NovelBookState =
    {
            novelId,
            enabled,
            bookVersion,
            sourceId,
            chapterSetHash,
            totalChars,
            chapterCount,
            charOffset,
            lastChapterId,
            blockIndex,
            chapterCharOffset,
            progressMigrated,
            complete,
            builtAt,
            updatedAt,
        ->
        NovelBookState(
            novelId = novelId,
            enabled = enabled,
            bookVersion = bookVersion,
            sourceId = sourceId,
            chapterSetHash = chapterSetHash,
            totalChars = totalChars,
            chapterCount = chapterCount.toInt(),
            charOffset = charOffset,
            lastChapterId = lastChapterId,
            complete = complete,
            builtAt = builtAt,
            updatedAt = updatedAt,
            blockIndex = blockIndex.toInt(),
            chapterCharOffset = chapterCharOffset.toInt(),
            progressMigrated = progressMigrated,
        )
    }
