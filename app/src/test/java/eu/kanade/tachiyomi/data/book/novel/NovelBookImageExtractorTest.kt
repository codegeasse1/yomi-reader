package eu.kanade.tachiyomi.data.book.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Base64

class NovelBookImageExtractorTest {

    private val pngBytes = byteArrayOf(1, 2, 3, 4, 5)
    private val png = Base64.getEncoder().encodeToString(pngBytes)

    private fun imagesIn(root: File): List<File> =
        File(root, NovelBookImageExtractor.IMAGES_DIRECTORY_NAME).listFiles()?.sortedBy { it.name }.orEmpty()

    @Test
    fun `writes inline images to disk and rewires the tag`(@TempDir root: File) {
        val html = """<div><p>Text</p><img src="data:image/png;base64,$png"/></div>"""

        val result = NovelBookImageExtractor.externalize(html, root)

        val files = imagesIn(root)
        files.size shouldBe 1
        files[0].extension shouldBe "png"
        files[0].readBytes().toList() shouldBe pngBytes.toList()
        result.contains("data:image/png") shouldBe false
        result.contains(files[0].toURI().toString()) shouldBe true
        result.contains("<p>Text</p>") shouldBe true
    }

    @Test
    fun `identical images are stored once`(@TempDir root: File) {
        val jpg = Base64.getEncoder().encodeToString(byteArrayOf(9, 9, 9))
        val html = """
            <div>
              <img src="data:image/png;base64,$png"/>
              <img src="data:image/png;base64,$png"/>
              <img src="data:image/jpeg;base64,$jpg"/>
            </div>
        """.trimIndent()

        val result = NovelBookImageExtractor.externalize(html, root)

        val files = imagesIn(root)
        files.size shouldBe 2
        files.count { it.extension == "png" } shouldBe 1
        files.count { it.extension == "jpg" } shouldBe 1
        result.contains("base64") shouldBe false
    }

    @Test
    fun `html without inline images is returned untouched`(@TempDir root: File) {
        val html = """<div><p>Just text</p><img src="images/pic.png"/></div>"""

        NovelBookImageExtractor.externalize(html, root) shouldBe html
        File(root, NovelBookImageExtractor.IMAGES_DIRECTORY_NAME).exists() shouldBe false
    }

    @Test
    fun `broken data uris and empty tags are left in place`(@TempDir root: File) {
        val html = """<div><img src="data:image/png;base64,%%not-base64%%"/><img/></div>"""

        val result = NovelBookImageExtractor.externalize(html, root)

        result.contains("data:image/png;base64,%%not-base64%%") shouldBe true
        imagesIn(root).size shouldBe 0
    }
}
