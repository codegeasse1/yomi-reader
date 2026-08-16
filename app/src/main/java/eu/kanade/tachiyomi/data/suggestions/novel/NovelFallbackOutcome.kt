package eu.kanade.tachiyomi.data.suggestions.novel

import eu.kanade.tachiyomi.data.suggestions.SuggestionItem

sealed interface NovelFallbackOutcome {
    data class Success(val items: List<SuggestionItem>) : NovelFallbackOutcome
    data class Empty(val reason: NovelFallbackReason) : NovelFallbackOutcome
}
