package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderGeminiAutomationTest {

    @Test
    fun `english source detector accepts common english labels`() {
        isGeminiSourceLanguageEnglish("English") shouldBe true
        isGeminiSourceLanguageEnglish(" english ") shouldBe true
        isGeminiSourceLanguageEnglish("EN") shouldBe true
        isGeminiSourceLanguageEnglish("Английский") shouldBe true
    }

    @Test
    fun `english source detector rejects non english labels`() {
        isGeminiSourceLanguageEnglish("Japanese") shouldBe false
        isGeminiSourceLanguageEnglish("Русский") shouldBe false
        isGeminiSourceLanguageEnglish("French") shouldBe false
    }

    @Test
    fun `gemini next chapter prefetch threshold uses 30 percent for standard reader mode`() {
        hasReachedGeminiNextChapterTranslationPrefetchThreshold(currentIndex = 2, totalItems = 10) shouldBe true
        hasReachedGeminiNextChapterTranslationPrefetchThreshold(currentIndex = 1, totalItems = 10) shouldBe false
    }

    @Test
    fun `gemini next chapter prefetch threshold uses integer percent when total is 100`() {
        hasReachedGeminiNextChapterTranslationPrefetchThreshold(currentIndex = 30, totalItems = 100) shouldBe true
        hasReachedGeminiNextChapterTranslationPrefetchThreshold(currentIndex = 29, totalItems = 100) shouldBe false
    }
}
