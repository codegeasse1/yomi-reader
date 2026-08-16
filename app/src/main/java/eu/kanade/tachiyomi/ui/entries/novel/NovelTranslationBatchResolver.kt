package eu.kanade.tachiyomi.ui.entries.novel

import tachiyomi.domain.items.novelchapter.model.NovelChapter

enum class TranslationBatchScope {
    SELECTED,
    DOWNLOADED,
    UNREAD,
    FIRST_N_VISIBLE,
    RANGE,
}

data class TranslationBatchSelection(
    val chapterIdsToEnqueue: List<Long>,
    val skippedAlreadyTranslatedCount: Int,
)

fun resolveTranslationBatchChapterIds(
    scope: TranslationBatchScope,
    limit: Int,
    chapters: List<NovelChapter>,
    selectedChapterIds: Set<Long>,
    downloadedChapterIds: Set<Long>,
    rangeStart: Int = 1,
    rangeEnd: Int = limit,
): List<Long> {
    val orderedChapters = chapters.asSequence()
    return when (scope) {
        TranslationBatchScope.SELECTED ->
            orderedChapters
                .filter { it.id in selectedChapterIds }
                .map { it.id }
                .toList()

        TranslationBatchScope.DOWNLOADED ->
            orderedChapters
                .filter { it.id in downloadedChapterIds }
                .map { it.id }
                .toList()

        TranslationBatchScope.UNREAD ->
            orderedChapters
                .filter { !it.read }
                .map { it.id }
                .toList()

        TranslationBatchScope.FIRST_N_VISIBLE ->
            orderedChapters
                .let { sequence ->
                    if (limit > 0) sequence.take(limit) else sequence
                }
                .map { it.id }
                .toList()

        TranslationBatchScope.RANGE -> {
            if (chapters.isEmpty()) {
                return emptyList()
            }

            val normalizedStart = minOf(rangeStart, rangeEnd).coerceAtLeast(1)
            val normalizedEnd = maxOf(rangeStart, rangeEnd).coerceAtLeast(normalizedStart)
            val startIndex = (normalizedStart - 1).coerceAtMost(chapters.lastIndex)
            val endIndex = (normalizedEnd - 1).coerceAtMost(chapters.lastIndex)
            if (startIndex > endIndex) {
                emptyList()
            } else {
                orderedChapters
                    .drop(startIndex)
                    .take(endIndex - startIndex + 1)
                    .map { it.id }
                    .toList()
            }
        }
    }
}

fun filterTranslationBatchChapterIds(
    chapterIds: List<Long>,
    alreadyTranslatedChapterIds: Set<Long>,
    forceRetranslate: Boolean,
): TranslationBatchSelection {
    if (forceRetranslate) {
        return TranslationBatchSelection(
            chapterIdsToEnqueue = chapterIds,
            skippedAlreadyTranslatedCount = 0,
        )
    }

    val filteredChapterIds = chapterIds.filterNot { it in alreadyTranslatedChapterIds }
    return TranslationBatchSelection(
        chapterIdsToEnqueue = filteredChapterIds,
        skippedAlreadyTranslatedCount = chapterIds.size - filteredChapterIds.size,
    )
}
