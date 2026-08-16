package eu.kanade.presentation.reader.novel

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class NovelBookSectionDomTest {

    @Test
    fun `section html carries index and chapter id`() {
        val html = buildBookSectionHtml(
            sectionIndex = 3,
            chapterId = 42L,
            title = "Chapter 4",
            bodyHtml = "<p>Body</p>",
        )

        html shouldContain "id=\"__an_book_section_3\""
        html shouldContain "data-an-section=\"3\""
        html shouldContain "data-an-chapter=\"42\""
        html shouldContain "<p>Body</p>"
    }

    @Test
    fun `first section has no divider but later sections do`() {
        val first = buildBookSectionHtml(0, 1L, "One", "<p>1</p>")
        val second = buildBookSectionHtml(1, 2L, "Two", "<p>2</p>")

        first shouldNotContain BOOK_SECTION_DIVIDER_CLASS
        second shouldContain BOOK_SECTION_DIVIDER_CLASS
    }

    @Test
    fun `heading can be hidden and titles are escaped`() {
        val hidden = buildBookSectionHtml(1, 1L, "Title", "<p>1</p>", showHeading = false)
        val escaped = buildBookSectionHtml(1, 1L, "A <b>&</b>", "<p>1</p>")

        hidden shouldNotContain BOOK_SECTION_TITLE_CLASS
        escaped shouldContain "A &lt;b&gt;&amp;&lt;/b&gt;"
    }
}
