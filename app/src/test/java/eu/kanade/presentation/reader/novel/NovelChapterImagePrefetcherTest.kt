package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class NovelChapterImagePrefetcherTest {

    @Test
    fun `prioritization sorts active image index first`() {
        val models = listOf(
            NovelPluginImage("plugin://1/img0.jpg"),
            NovelPluginImage("plugin://1/img1.jpg"),
            NovelPluginImage("plugin://1/img2.jpg"),
        )

        val prioritized = prioritizeChapterImageModels(models, activeImageIndex = 2)
        prioritized[0] shouldBe NovelPluginImage("plugin://1/img2.jpg")
    }
}
