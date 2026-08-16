package eu.kanade.presentation.reader.novel

/**
 * DOM helpers for the continuous ("book mode") novel reader.
 *
 * Book mode used to keep a single WebView document alive and stream chapter sections into it via
 * raw JavaScript. The book engine now owns that document (see NovelBookEngineDocument), so the
 * helpers that remain here only build the section markup itself: no Android types, no WebView
 * access and nothing that can affect the existing chapter-by-chapter reader.
 */

internal const val BOOK_SECTION_CLASS = "an-book-section"
internal const val BOOK_SECTION_BODY_CLASS = "an-book-section-body"
internal const val BOOK_SECTION_TITLE_CLASS = "an-book-section-title"
internal const val BOOK_SECTION_DIVIDER_CLASS = "an-book-divider"
internal const val BOOK_SECTION_ID_PREFIX = "__an_book_section_"

internal fun bookSectionElementId(sectionIndex: Int): String = "$BOOK_SECTION_ID_PREFIX$sectionIndex"

/**
 * Wraps one chapter's reader-ready HTML into a book section element.
 *
 * The section carries its index and chapter id as data attributes so the reader can map a scroll
 * position back to a chapter without reloading anything.
 */
internal fun buildBookSectionHtml(
    sectionIndex: Int,
    chapterId: Long,
    title: String?,
    bodyHtml: String,
    showDivider: Boolean = sectionIndex > 0,
    showHeading: Boolean = true,
): String {
    return buildString {
        append("<section id=\"")
        append(bookSectionElementId(sectionIndex))
        append("\" class=\"")
        append(BOOK_SECTION_CLASS)
        append("\" data-an-section=\"")
        append(sectionIndex)
        append("\" data-an-chapter=\"")
        append(chapterId)
        append("\">")
        if (showDivider) {
            append("<div class=\"")
            append(BOOK_SECTION_DIVIDER_CLASS)
            append("\" aria-hidden=\"true\"></div>")
        }
        if (showHeading && !title.isNullOrBlank()) {
            append("<h2 class=\"")
            append(BOOK_SECTION_TITLE_CLASS)
            append("\">")
            append(escapeBookHtmlText(title))
            append("</h2>")
        }
        append("<div class=\"")
        append(BOOK_SECTION_BODY_CLASS)
        append("\">")
        append(bodyHtml)
        append("</div></section>")
    }
}

private fun escapeBookHtmlText(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
