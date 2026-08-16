package eu.kanade.tachiyomi.ui.reader.novel.setting

/**
 * How the novel reader presents a series.
 *
 * [CHAPTERS] is the long standing behaviour: one chapter is loaded and rendered at a time.
 * [BOOK] keeps a single continuous document and streams neighbouring chapters in and out around the
 * reading position, so a novel can be read end to end without waiting on chapter loads.
 */
enum class NovelReadingMode {
    CHAPTERS,
    BOOK,
}
