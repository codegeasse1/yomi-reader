package eu.kanade.tachiyomi.ui.reader.novel

/**
 * The one and only reading position of book mode.
 *
 * Book mode used to carry the position in four shapes at once (offset inside a section, section
 * fraction, whole-book character offset and a packed `lastPageRead` value), converting between them
 * on every hop and losing precision each time. A locator is anchored to real content instead:
 *
 * - [chapterId] is the chapter the reader is inside. It survives a rebuild of the book artifact,
 *   which is why the position is anchored to it and not to a block index.
 * - [charOffset] is the sanitized character offset **inside that chapter**, so it stays valid even
 *   when the block layout changes.
 * - [blockIndex] is the artifact block that holds the position. It is a lookup hint only: it makes
 *   resolving a locator O(1) and is recomputed whenever the book is opened.
 *
 * Nothing in this type depends on font size, theme or pagination, so it is comparable across
 * renderers and across sessions.
 */
data class BookLocator(
    val chapterId: Long,
    val blockIndex: Int,
    val charOffset: Int,
) {

    companion object {
        /** Locator of a book that has no stored position yet. */
        val UNKNOWN = BookLocator(chapterId = NO_CHAPTER_ID, blockIndex = 0, charOffset = 0)

        /** Chapter id of a position that is not anchored to a chapter (empty or unopened book). */
        const val NO_CHAPTER_ID = 0L
    }
}
