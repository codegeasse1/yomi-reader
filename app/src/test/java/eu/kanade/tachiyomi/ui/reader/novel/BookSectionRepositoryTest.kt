package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class BookSectionRepositoryTest {

    private fun chapterHtml(chapterId: Long, text: String): String =
        "<section class=\"nb-chapter\" id=\"nb-ch-$chapterId\" data-cid=\"$chapterId\">" +
            "<p>$text</p></section>"

    private fun repository(
        translate: suspend (Long, String) -> String = { _, html -> html },
        showChapterHeadings: Boolean = true,
        translationVariant: String = "raw",
    ) = DefaultBookSectionRepository(
        loadRawSection = { chapterId ->
            NovelBookRawSection(chapterId = chapterId, chapterName = "Chapter $chapterId", rawHtml = "<p>raw</p>")
        },
        translateChapterHtml = translate,
        showChapterHeadings = { showChapterHeadings },
        translationVariant = { translationVariant },
    )

    @Test
    fun `a section straddling a chapter boundary is translated chapter by chapter`() = runTest {
        val html = chapterHtml(7L, "first") + chapterHtml(8L, "second")
        val translatedChapters = mutableListOf<Long>()

        val result = repository(
            translate = { chapterId, body ->
                translatedChapters += chapterId
                body.replace("<p>", "<p>[$chapterId] ")
            },
        ).applyArtifactTranslations(html = html, chapterIds = listOf(7L, 8L))

        translatedChapters shouldBe listOf(7L, 8L)
        result shouldContain "[7] first"
        result shouldContain "[8] second"
    }

    @Test
    fun `a section with a single chapter is handed over whole`() = runTest {
        val html = chapterHtml(7L, "only")
        var received: String? = null

        repository(
            translate = { _, body ->
                received = body
                body
            },
        ).applyArtifactTranslations(html = html, chapterIds = listOf(7L))

        received shouldBe html
    }

    @Test
    fun `markup without chapter markers falls back to the first chapter of the section`() = runTest {
        val translatedChapters = mutableListOf<Long>()

        repository(
            translate = { chapterId, body ->
                translatedChapters += chapterId
                body
            },
        ).applyArtifactTranslations(html = "<p>legacy artifact</p>", chapterIds = listOf(12L))

        translatedChapters shouldBe listOf(12L)
    }

    @Test
    fun `untranslated content is returned unchanged`() = runTest {
        val html = chapterHtml(7L, "first") + chapterHtml(8L, "second")

        repository().applyArtifactTranslations(html = html, chapterIds = listOf(7L, 8L)) shouldBe html
    }

    @Test
    fun `the transform signature changes with the headings setting and the translation`() {
        val base = repository().transformSignature()

        base shouldNotBe repository(showChapterHeadings = false).transformSignature()
        base shouldNotBe repository(translationVariant = "gemini").transformSignature()
        repository(translationVariant = "gemini").transformSignature() shouldBe
            repository(translationVariant = "gemini").transformSignature()
    }

    @Test
    fun `splitting keeps every chapter of the section in reading order`() {
        val fragments = splitArtifactSectionByChapter(
            html = chapterHtml(1L, "a") + chapterHtml(2L, "b") + chapterHtml(3L, "c"),
            fallbackChapterIds = listOf(1L, 2L, 3L),
        )

        fragments.map { it.chapterId } shouldBe listOf(1L, 2L, 3L)
        fragments.joinToString(separator = "") { it.html } shouldContain "nb-ch-3"
    }
}
