package eu.kanade.presentation.entries.novel

internal enum class NovelBookReadingMode {
    BOOK,
    CHAPTERS,
}

internal data class NovelBookModeMenu(
    val current: NovelBookReadingMode,
    val target: NovelBookReadingMode,
)

internal fun resolveNovelBookModeMenu(readAsBook: Boolean): NovelBookModeMenu {
    return if (readAsBook) {
        NovelBookModeMenu(
            current = NovelBookReadingMode.BOOK,
            target = NovelBookReadingMode.CHAPTERS,
        )
    } else {
        NovelBookModeMenu(
            current = NovelBookReadingMode.CHAPTERS,
            target = NovelBookReadingMode.BOOK,
        )
    }
}
