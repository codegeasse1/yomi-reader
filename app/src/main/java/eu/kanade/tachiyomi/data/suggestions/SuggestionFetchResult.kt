package eu.kanade.tachiyomi.data.suggestions

data class SuggestionFetchResult(
    val items: List<SuggestionItem>,
    val attemptedSources: Int,
    val failedSources: Int,
    val matchedBase: Boolean = false,
)
