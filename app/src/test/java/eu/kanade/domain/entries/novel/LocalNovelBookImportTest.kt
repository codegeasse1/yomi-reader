package eu.kanade.domain.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LocalNovelBookImportTest {

    @Test
    fun `accepts epub and fb2 only`() {
        LocalNovelBookImport.isSupportedImportFileName("book.epub") shouldBe true
        LocalNovelBookImport.isSupportedImportFileName("book.FB2") shouldBe true
        LocalNovelBookImport.isSupportedImportFileName("book.pdf") shouldBe false
        LocalNovelBookImport.isSupportedImportFileName("book.txt") shouldBe false
        LocalNovelBookImport.isSupportedImportFileName("book") shouldBe false
    }

    @Test
    fun `title fallback strips supported extensions`() {
        LocalNovelBookImport.titleFallbackFromFileName("My Novel.epub") shouldBe "My Novel"
        LocalNovelBookImport.titleFallbackFromFileName("My Novel.fb2") shouldBe "My Novel"
        LocalNovelBookImport.titleFallbackFromFileName("untitled") shouldBe "untitled"
    }

    @Test
    fun `sanitizes illegal path characters`() {
        LocalNovelBookImport.sanitizeFileName("a/b:c*.epub") shouldBe "a_b_c_.epub"
    }
}
