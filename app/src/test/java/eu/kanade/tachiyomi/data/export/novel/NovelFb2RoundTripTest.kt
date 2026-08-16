package eu.kanade.tachiyomi.data.export.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.source.local.entries.novel.Fb2Book

/**
 * Round-trip coverage for the FB2 export: everything [NovelFb2Writer] emits must be readable again
 * by [Fb2Book], the parser used for imported local books. This is what guarantees that a book
 * exported from the app can be re-imported into it without losing chapters, titles or formatting.
 *
 * Only Jsoup-backed parser paths are exercised, so no Robolectric runtime is needed; cover images
 * are the single `android.util.Base64` dependency of the parser and are covered elsewhere.
 */
class NovelFb2RoundTripTest {

    private val metadata = NovelFb2Metadata(
        title = "Тайтл & Co",
        bookId = "novel-42",
        author = "Автор",
        description = "Описание",
        language = "ru",
        exportedOn = "2026-07-29",
    )

    private fun roundTrip(chapters: List<NovelFb2Chapter>): Fb2Book {
        val document = NovelFb2Writer.build(metadata = metadata, chapters = chapters)
        return Fb2Book.parse(document.xml.byteInputStream())
    }

    @Test
    fun `exported metadata and chapter order survive parsing`() {
        val book = roundTrip(
            listOf(
                NovelFb2Chapter(id = "ch-1", title = "Глава 1", html = "<p>Первая</p>"),
                NovelFb2Chapter(id = "ch-2", title = "Глава 2", html = "<p>Вторая</p>"),
                NovelFb2Chapter(id = "ch-3", title = "Глава 3", html = "<p>Третья</p>"),
            ),
        )

        assertEquals("Тайтл & Co", book.bookTitle)
        assertEquals(listOf("Автор"), book.authors)
        assertEquals("Описание", book.annotation)
        assertEquals(listOf("Глава 1", "Глава 2", "Глава 3"), book.chapters.map { it.title })
        assertEquals(listOf(0, 1, 2), book.chapters.map { it.index })

        val second = book.chapterHtml(1)
        assertTrue(second.contains("<p>Вторая</p>"), second)
        assertFalse(second.contains("Первая"), second)
        assertFalse(second.contains("Третья"), second)
    }

    @Test
    fun `exported block and inline formatting survive parsing`() {
        val html = buildString {
            append("<h2>Заголовок</h2>")
            append("<p>Обычный <em>курсив</em> и <b>жирный</b> текст</p>")
            append("<hr>")
            append("<blockquote><p>Цитата</p></blockquote>")
            append("<ul><li>Пункт</li></ul>")
            append("<p><a href=\"https://example.org\">Ссылка</a></p>")
        }
        val book = roundTrip(listOf(NovelFb2Chapter(id = "ch-1", title = "Глава", html = html)))

        val restored = book.chapterHtml(0)
        assertTrue(restored.contains("<h4>Заголовок</h4>"), restored)
        assertTrue(restored.contains("<em>курсив</em>"), restored)
        assertTrue(restored.contains("<strong>жирный</strong>"), restored)
        assertTrue(restored.contains("<br/>"), restored)
        assertTrue(restored.contains("<blockquote><p>Цитата</p></blockquote>"), restored)
        assertTrue(restored.contains("<p>Пункт</p>"), restored)
        assertTrue(restored.contains("<a href=\"https://example.org\">Ссылка</a>"), restored)
    }

    @Test
    fun `xml escaping is reversible`() {
        val book = roundTrip(
            listOf(
                NovelFb2Chapter(id = "ch-1", title = "A < B", html = "<p>Tom & Jerry</p>"),
            ),
        )

        assertEquals(listOf("A < B"), book.chapters.map { it.title })
        val restored = book.chapterHtml(0)
        assertTrue(restored.contains("Tom &amp; Jerry"), restored)
        assertFalse(restored.contains("&amp;amp;"), restored)
    }

    @Test
    fun `a book exported without chapters is still parseable`() {
        val book = roundTrip(emptyList())

        assertEquals(1, book.chapters.size)
        assertTrue(book.chapterHtml(0).contains("<br/>"), book.chapterHtml(0))
    }
}
