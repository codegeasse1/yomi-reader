package tachiyomi.source.local.io.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LocalNovelFormatsTest {

    @Test
    fun `supports novel formats and rejects pdf`() {
        LocalNovelFormats.isSupportedExtension("epub") shouldBe true
        LocalNovelFormats.isSupportedExtension("txt") shouldBe true
        LocalNovelFormats.isSupportedExtension("fb2") shouldBe true
        LocalNovelFormats.isSupportedExtension("PDF") shouldBe false
        LocalNovelFormats.isSupportedExtension("pdf") shouldBe false
        LocalNovelFormats.isSupportedExtension(null) shouldBe false
    }

    @Test
    fun `file name checks use last extension`() {
        LocalNovelFormats.isSupportedFileName("book.epub") shouldBe true
        LocalNovelFormats.isSupportedFileName("book.PDF") shouldBe false
        LocalNovelFormats.isSupportedFileName("archive.tar.gz") shouldBe false
        LocalNovelFormats.isSupportedFileName("noext") shouldBe false
    }
}
