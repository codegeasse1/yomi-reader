package eu.kanade.tachiyomi.data.suggestions.sources

import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed

abstract class RecommendationPagingSource {
    abstract val name: String
    abstract val mediaType: SuggestionMediaType
    var matchedBase: Boolean = false

    /**
     * Fetch recommendations for [seed]. Return empty list on error — callers handle silently.
     */
    abstract suspend fun fetchSuggestions(seed: SuggestionSeed): List<SuggestionItem>
}
