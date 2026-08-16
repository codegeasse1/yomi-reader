package eu.kanade.tachiyomi.data.export.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NovelFb2WriterTest {

    private val metadata = NovelFb2Metadata(
        title = "Тайтл & Co",
        bookId = "novel-42",
        author = "Автор",
        description = "Описание",
        language = "ru",
        exportedOn = "2026-07-29",
    )

    @Test
    fun `writes one section per chapter in the given order`() {
        val document = NovelFb2Writer.build(
            metadata = metadata,
            chapters = listOf(
                NovelFb2Chapter(id = "ch-1", title = "Глава 1", html = "<p>Первая</p>"),
                NovelFb2Chapter(id = "ch-2", title = "Глава 2", html = "<p>Вторая</p>"),
                NovelFb2Chapter(id = "ch-3", title = "Глава 3", html = "<p>Третья</p>"),
            ),
        )

        val xml = document.xml
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertEquals(3, document.report.chapters)
        assertEquals(3, Regex("<section id=\"").findAll(xml).count())
        assertTrue(xml.contains("<title><p>Глава 1</p></title><p>Первая</p>"))
        assertTrue(xml.indexOf("ch-1") < xml.indexOf("ch-2"))
        assertTrue(xml.indexOf("ch-2") < xml.indexOf("ch-3"))
    }

    @Test
    fun `writes fb2 metadata and escapes xml special characters`() {
        val document = NovelFb2Writer.build(
            metadata = metadata,
            chapters = listOf(
                NovelFb2Chapter(id = "ch-1", title = "A < B", html = "<p>Tom & Jerry <tag></p>"),
            ),
        )

        val xml = document.xml
        assertTrue(xml.contains("<book-title>Тайтл &amp; Co</book-title>"))
        assertTrue(xml.contains("<nickname>Автор</nickname>"))
        assertTrue(xml.contains("<annotation><p>Описание</p></annotation>"))
        assertTrue(xml.contains("<lang>ru</lang>"))
        assertTrue(xml.contains("<id>novel-42</id>"))
        assertTrue(xml.contains("<date>2026-07-29</date>"))
        assertTrue(xml.contains("<title><p>A &lt; B</p></title>"))
        assertTrue(xml.contains("Tom &amp; Jerry"))
        assertFalse(xml.contains("<tag>"))
    }

    @Test
    fun `maps html blocks to fb2 blocks and reports skipped images`() {
        val html = buildString {
            append("<h2>Заголовок</h2>")
            append("<p>Обычный <em>курсив</em> и <b>жирный</b> текст</p>")
            append("<hr>")
            append("<blockquote><p>Цитата</p></blockquote>")
            append("<ul><li>Пункт</li></ul>")
            append("<p><img src=\"https://example.org/a.png\"/></p>")
            append("<a href=\"https://example.org\">Ссылка</a>")
        }
        val document = NovelFb2Writer.build(
            metadata = metadata,
            chapters = listOf(NovelFb2Chapter(id = "ch-1", title = "Глава", html = html)),
        )

        val xml = document.xml
        assertTrue(xml.contains("<subtitle>Заголовок</subtitle>"))
        assertTrue(xml.contains("<emphasis>курсив</emphasis>"))
        assertTrue(xml.contains("<strong>жирный</strong>"))
        assertTrue(xml.contains("<empty-line/>"))
        assertTrue(xml.contains("<cite><p>Цитата</p></cite>"))
        assertTrue(xml.contains("<p>Пункт</p>"))
        assertTrue(xml.contains("<a l:href=\"https://example.org\">Ссылка</a>"))
        assertFalse(xml.contains("<img"))
        assertEquals(1, document.report.skippedImages)
    }

    @Test
    fun `never emits an empty section`() {
        val document = NovelFb2Writer.build(
            metadata = metadata,
            chapters = listOf(
                NovelFb2Chapter(id = "ch-1", title = "Пустая", html = "   "),
            ),
        )

        val xml = document.xml
        assertTrue(xml.contains("<section id=\"ch-1\"><title><p>Пустая</p></title><empty-line/></section>"))
    }

    @Test
    fun `builds a valid skeleton even without chapters`() {
        val document = NovelFb2Writer.build(metadata = metadata, chapters = emptyList())

        val xml = document.xml
        assertEquals(0, document.report.chapters)
        assertTrue(xml.contains("<body>"))
        assertTrue(xml.contains("<section><empty-line/></section>"))
        assertTrue(xml.trimEnd().endsWith("</FictionBook>"))
    }
}
