package eu.kanade.tachiyomi.ui.reader.novel

import tachiyomi.domain.items.novelchapter.model.NovelChapter

/** Default section weight of a test spine, mirroring a compiled block's character length. */
internal const val TEST_SECTION_CHAR_COUNT = 4_000

/**
 * Builds a spine shaped like the one a compiled book artifact produces: every section carries an
 * exact, already measured text length.
 *
 * Book mode only ever runs over an artifact, so tests must not build spines out of estimates.
 */
internal fun testSpineOf(
    chapters: List<NovelChapter>,
    charCounts: Map<Long, Int> = emptyMap(),
    defaultCharCount: Int = TEST_SECTION_CHAR_COUNT,
): NovelBookSpine = NovelBookSpine(
    chapters.mapIndexed { index, chapter ->
        NovelBookSection(
            chapterId = chapter.id,
            index = index,
            name = chapter.name,
            charCount = (charCounts[chapter.id] ?: defaultCharCount).coerceAtLeast(1),
            isMeasured = true,
        )
    },
)
