package eu.kanade.tachiyomi.data.suggestions

/**
 * Per-source weights used to compute the final score of a [SuggestionItem].
 *
 * Final score = sourceWeight × bestMatchScore, where bestMatchScore is the
 * best [SuggestionTitleResolver.scoreMatch] result across all candidate
 * titles for the current novel (0..100).
 *
 * The weights are tuned so that:
 *  - High-trust external databases (AniList, MangaUpdates, NovelUpdates)
 *    dominate the list.
 *  - Native "related" plugin results are slightly less trusted but still
 *    in front of generic search results.
 *  - The source's own search engine is treated as a soft signal: exact
 *    title matches are preferred over relaxed ones, author matches beat
 *    genre matches, and the "popular" catalogue is a last-resort filler.
 */
object SuggestionSourceWeight {

    const val EXTERNAL_ANILIST: Double = 1.0
    const val EXTERNAL_MAL: Double = 0.9
    const val EXTERNAL_MU: Double = 0.9
    const val EXTERNAL_NU: Double = 0.9
    const val RELATED: Double = 0.8
    const val SEARCH_TITLE: Double = 0.6
    const val SEARCH_AUTHOR: Double = 0.4
    const val SEARCH_GENRE: Double = 0.3
    const val POPULAR_BACKFILL: Double = 0.1

    fun of(reason: SuggestionReason): Double = when (reason) {
        SuggestionReason.RELATED -> RELATED
        SuggestionReason.EXTERNAL_ANILIST -> EXTERNAL_ANILIST
        SuggestionReason.EXTERNAL_MAL -> EXTERNAL_MAL
        SuggestionReason.EXTERNAL_MU -> EXTERNAL_MU
        SuggestionReason.EXTERNAL_NU -> EXTERNAL_NU
        SuggestionReason.SEARCH_TITLE -> SEARCH_TITLE
        SuggestionReason.SEARCH_AUTHOR -> SEARCH_AUTHOR
        SuggestionReason.SEARCH_GENRE -> SEARCH_GENRE
        SuggestionReason.POPULAR_BACKFILL -> POPULAR_BACKFILL
    }

    /**
     * Computes the final score for a single suggestion. Returns a
     * non-negative value where higher is better. Suggestions with
     * no observable match ([bestMatchScore] == 0) will still get a
     * non-zero floor coming from the source weight alone, which lets
     * the ranker keep backfill items visible without drowning real
     * matches.
     */
    fun finalScore(reason: SuggestionReason, bestMatchScore: Int): Double {
        val weight = of(reason)
        val normalized = (bestMatchScore.coerceIn(0, 100)).toDouble()
        return weight * normalized
    }
}
