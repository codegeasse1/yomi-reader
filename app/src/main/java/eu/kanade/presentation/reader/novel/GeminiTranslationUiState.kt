package eu.kanade.presentation.reader.novel

internal enum class GeminiTranslationUiState {
    Translating,
    CachedVisible,
    CachedHidden,
    Ready,
}

internal fun resolveGeminiTranslationUiState(
    isTranslating: Boolean,
    hasCache: Boolean,
    isVisible: Boolean,
    translationProgress: Int,
): GeminiTranslationUiState {
    if (isTranslating) return GeminiTranslationUiState.Translating

    val hasTranslationResult = hasCache || translationProgress >= 100
    if (!hasTranslationResult) return GeminiTranslationUiState.Ready

    return if (isVisible) {
        GeminiTranslationUiState.CachedVisible
    } else {
        GeminiTranslationUiState.CachedHidden
    }
}
