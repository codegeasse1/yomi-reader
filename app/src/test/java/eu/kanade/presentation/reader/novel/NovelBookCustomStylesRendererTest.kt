package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookCustomStylesRendererTest {

    @Test
    fun `custom css or js keeps the webview renderer instead of dropping the setting`() {
        resolveNovelBookRenderer(
            pageReaderEnabled = false,
            richNativeRendererExperimentalEnabled = true,
            bionicReadingEnabled = false,
            customStylesPresent = true,
            richContentUnsupportedFeaturesDetected = false,
            nativeBookRendererAvailable = true,
        ) shouldBe NovelBookRenderer.WEBVIEW_SCROLLED
    }

    @Test
    fun `without custom styles the native renderer is still used`() {
        resolveNovelBookRenderer(
            pageReaderEnabled = false,
            richNativeRendererExperimentalEnabled = true,
            bionicReadingEnabled = false,
            customStylesPresent = false,
            richContentUnsupportedFeaturesDetected = false,
            nativeBookRendererAvailable = true,
        ) shouldBe NovelBookRenderer.RICH_NATIVE_SCROLLED
    }
}
