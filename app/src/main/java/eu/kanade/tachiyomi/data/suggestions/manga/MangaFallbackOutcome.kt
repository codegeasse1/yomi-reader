package eu.kanade.tachiyomi.data.suggestions.manga

import eu.kanade.tachiyomi.data.suggestions.SuggestionItem

sealed interface MangaFallbackOutcome {
    data class Success(val items: List<SuggestionItem>) : MangaFallbackOutcome
    data class Empty(val reason: MangaFallbackReason) : MangaFallbackOutcome
}
