package eu.kanade.tachiyomi.data.suggestions.anime

import eu.kanade.tachiyomi.data.suggestions.SuggestionItem

sealed interface AnimeFallbackOutcome {
    data class Success(val items: List<SuggestionItem>) : AnimeFallbackOutcome
    data class Empty(val reason: AnimeFallbackReason) : AnimeFallbackOutcome
}
