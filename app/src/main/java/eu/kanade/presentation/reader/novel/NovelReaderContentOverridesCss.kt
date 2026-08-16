package eu.kanade.presentation.reader.novel

/**
 * Appends the book renderer's own layout overrides to [baseCss].
 *
 * The stylesheet used to be assembled with an inline `buildString` inside `NovelReaderScreen`, so
 * every line of it counted towards that composable's own bytecode. The method grew past the size ART
 * is willing to JIT-compile, which left the reader running interpreted for its first frames - exactly
 * when the book is being restored and the user is looking at it. The literal lives here instead and
 * is only re-joined when the base stylesheet changes.
 */
internal fun withNovelBookReaderContentOverrides(baseCss: String): String =
    baseCss + NOVEL_BOOK_READER_CONTENT_OVERRIDES_CSS

internal val NOVEL_BOOK_READER_CONTENT_OVERRIDES_CSS = """

#an-book-content {
  padding-top: var(--an-reader-padding-top) !important;
  padding-bottom: var(--an-reader-padding-bottom) !important;
  padding-left: var(--an-reader-padding-left) !important;
  padding-right: var(--an-reader-padding-right) !important;
  background: var(--an-reader-bg) !important;
  color: var(--an-reader-fg) !important;
}
""".trimIndent()
