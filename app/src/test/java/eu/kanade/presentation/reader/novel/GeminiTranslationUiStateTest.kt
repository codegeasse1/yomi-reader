package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GeminiTranslationUiStateTest {

    @Test
    fun `resolves translating state with active progress`() {
        resolveGeminiTranslationUiState(
            isTranslating = true,
            hasCache = false,
            isVisible = false,
            translationProgress = 48,
        ) shouldBe GeminiTranslationUiState.Translating
    }

    @Test
    fun `resolves cached visible state when translated text is shown`() {
        resolveGeminiTranslationUiState(
            isTranslating = false,
            hasCache = true,
            isVisible = true,
            translationProgress = 100,
        ) shouldBe GeminiTranslationUiState.CachedVisible
    }

    @Test
    fun `resolves cached hidden state when translation exists but original is shown`() {
        resolveGeminiTranslationUiState(
            isTranslating = false,
            hasCache = true,
            isVisible = false,
            translationProgress = 100,
        ) shouldBe GeminiTranslationUiState.CachedHidden
    }

    @Test
    fun `treats progress completed as translation result even without cache flag`() {
        resolveGeminiTranslationUiState(
            isTranslating = false,
            hasCache = false,
            isVisible = false,
            translationProgress = 100,
        ) shouldBe GeminiTranslationUiState.CachedHidden
    }

    @Test
    fun `resolves ready state when no translation is running or available`() {
        resolveGeminiTranslationUiState(
            isTranslating = false,
            hasCache = false,
            isVisible = false,
            translationProgress = 0,
        ) shouldBe GeminiTranslationUiState.Ready
    }
}
