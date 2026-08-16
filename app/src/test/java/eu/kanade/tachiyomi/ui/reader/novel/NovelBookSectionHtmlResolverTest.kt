package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookSectionHtmlResolverTest {

    private val spine = testSpineOf(
        chapters = (0 until 3).map { index ->
            NovelChapter.create().copy(id = index + 1L, name = "Chapter ${index + 1}")
        },
    )

    private fun resolver(
        rawHtml: (Long) -> String = { chapterId -> "<p>chapter $chapterId</p>" },
        showChapterHeadings: Boolean = true,
    ) = NovelBookSectionHtmlResolver(
        currentSpine = { spine },
        repository = DefaultBookSectionRepository(
            loadRawSection = { chapterId ->
                NovelBookRawSection(
                    chapterId = chapterId,
                    chapterName = "Chapter $chapterId",
                    rawHtml = rawHtml(chapterId),
                )
            },
            translateChapterHtml = { _, html -> html },
            showChapterHeadings = { showChapterHeadings },
        ),
    )

    @Test
    fun `a section carries its index chapter id and body`() = runTest {
        val html = resolver().resolve(1L)

        html shouldContain "data-an-section=\"1\""
        html shouldContain "data-an-chapter=\"2\""
        html shouldContain "chapter 2"
        html shouldContain "Chapter 2"
    }

    @Test
    fun `the first section has no divider and later sections do`() = runTest {
        resolver().resolve(0L) shouldNotContain "an-book-divider"
        resolver().resolve(2L) shouldContain "an-book-divider"
    }

    @Test
    fun `chapter headings can be turned off`() = runTest {
        val html = resolver(showChapterHeadings = false).resolve(1L)

        html shouldNotContain "an-book-section-title"
        html shouldContain "chapter 2"
    }

    @Test
    fun `blank chapter content still renders its heading`() = runTest {
        // The shared pipeline prepends the chapter heading before checking for blank content, the
        // same way the per-chapter reader does, so an empty chapter is not mistaken for a load
        // failure: it renders its title and an empty body.
        val html = resolver(rawHtml = { "   " }).resolve(2L)

        html.isNotBlank() shouldBe true
        html shouldContain "an-reader-chapter-title"
        html shouldNotContain "<p>"
    }

    @Test
    fun `a chapter outside the spine resolves to nothing`() = runTest {
        resolver().resolve(99L) shouldBe ""
    }
}
