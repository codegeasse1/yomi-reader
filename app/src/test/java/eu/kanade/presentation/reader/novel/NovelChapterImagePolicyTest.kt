package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelChapterImagePolicyTest {

    @Test
    fun `extractChapterImageModels extracts plugin images and referer images`() {
        val pluginUrl = "novelimg://4587089711799726242/img1.jpg"
        val httpUrl = "https://example.org/img2.jpg"
        val blocks = listOf(
            NovelRichContentBlock.HorizontalRule(),
            NovelRichContentBlock.Image(url = pluginUrl),
            NovelRichContentBlock.Image(url = httpUrl),
            NovelRichContentBlock.Image(url = "   "),
        )

        val result = extractChapterImageModels(
            blocks = blocks,
            referer = "https://example.org/ch1",
        )

        result.size shouldBe 2
        result[0] shouldBe NovelPluginImage(pluginUrl)
        result[1] shouldBe NovelReaderRefererImage(url = httpUrl, referer = "https://example.org/ch1")
    }

    @Test
    fun `extractChapterImageModels eliminates duplicates`() {
        val pluginUrl = "novelimg://4587089711799726242/img1.jpg"
        val blocks = listOf(
            NovelRichContentBlock.Image(url = pluginUrl),
            NovelRichContentBlock.Image(url = pluginUrl),
        )

        val result = extractChapterImageModels(
            blocks = blocks,
            referer = null,
        )

        result.size shouldBe 1
        result[0] shouldBe NovelPluginImage(pluginUrl)
    }

    @Test
    fun `prioritizeChapterImageModels places active image index first`() {
        val img0 = NovelPluginImage("plugin://123/img0.jpg")
        val img1 = NovelPluginImage("plugin://123/img1.jpg")
        val img2 = NovelPluginImage("plugin://123/img2.jpg")
        val models = listOf(img0, img1, img2)

        val prioritized = prioritizeChapterImageModels(
            imageModels = models,
            activeImageIndex = 1,
        )

        prioritized shouldContainExactly listOf(img1, img2, img0)
    }
}
