package eu.kanade.tachiyomi.data.suggestions

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SuggestionState {
    data object Idle : SuggestionState // Before loading starts
    data object Loading : SuggestionState // Loading in progress
    data object Disabled : SuggestionState // Feature toggled off
    data class Empty(val message: String? = null) : SuggestionState // Successful fetch but empty result

    @Immutable
    data class Success(val items: List<SuggestionItem>, val hasMore: Boolean = false) : SuggestionState
    data class Error(val message: String) : SuggestionState
}
