package tachiyomi.data.anixart

/**
 * Pure matching orchestration for the Anixart importer.
 *
 * Fixes the ScreenModel anti-pattern of accumulating candidates across queries
 * and keeping the last iteration instead of the best score.
 */
object AnixartMatchingCoordinator {

    data class RowMatch(
        val result: AnixartMatcher.MatchResult,
        /** The search query that produced [result], or null when nothing matched. */
        val matchedQuery: String?,
    )

    data class MatchingReport(
        val total: Int,
        val auto: Int,
        val needsReview: Int,
        val noMatch: Int,
    )

    /**
     * Matches one [row] by trying [AnixartRow.searchQueries] in order.
     * Each query is searched and scored in isolation; the globally best result wins.
     */
    suspend fun matchRow(
        row: AnixartRow,
        search: suspend (String) -> List<AnixartMatcher.SearchCandidate>,
    ): RowMatch = matchTitles(row.candidateTitles(), row.searchQueries(), search)

    suspend fun matchTitles(
        candidateTitles: List<String>,
        searchQueries: List<String>,
        search: suspend (String) -> List<AnixartMatcher.SearchCandidate>,
    ): RowMatch {
        if (candidateTitles.isEmpty()) {
            return RowMatch(
                result = AnixartMatcher.match(emptyList(), emptyList()),
                matchedQuery = null,
            )
        }

        var bestResult: AnixartMatcher.MatchResult? = null
        var bestQuery: String? = null

        for (query in searchQueries) {
            val candidates = dedupCandidates(search(query))
            if (candidates.isEmpty()) continue

            val result = AnixartMatcher.match(candidateTitles, candidates)
            if (isBetter(result, bestResult)) {
                bestResult = result
                bestQuery = query
            }
            if (result.confidence == AnixartMatcher.Confidence.AUTO) break
        }

        val final = finalizeMatch(bestResult ?: AnixartMatcher.match(candidateTitles, emptyList()))
        return RowMatch(result = final, matchedQuery = bestQuery)
    }

    fun summarize(matches: List<RowMatch>): MatchingReport {
        var auto = 0
        var needsReview = 0
        var noMatch = 0
        for (m in matches) {
            when (m.result.confidence) {
                AnixartMatcher.Confidence.AUTO -> auto++
                AnixartMatcher.Confidence.NEEDS_REVIEW -> needsReview++
                AnixartMatcher.Confidence.NO_MATCH -> noMatch++
            }
        }
        return MatchingReport(
            total = matches.size,
            auto = auto,
            needsReview = needsReview,
            noMatch = noMatch,
        )
    }

    fun dedupCandidates(candidates: List<AnixartMatcher.SearchCandidate>): List<AnixartMatcher.SearchCandidate> {
        val seen = LinkedHashSet<String>()
        return candidates.filter { candidate ->
            val key = candidate.sourceId.toString() + "|" + candidate.url.ifEmpty { candidate.id.toString() }
            seen.add(key)
        }
    }

    private fun isBetter(
        new: AnixartMatcher.MatchResult,
        old: AnixartMatcher.MatchResult?,
    ): Boolean {
        if (old == null) return (new.ranked.firstOrNull()?.score ?: 0) > 0
        val newScore = new.best?.score ?: new.ranked.firstOrNull()?.score ?: 0
        val oldScore = old.best?.score ?: old.ranked.firstOrNull()?.score ?: 0
        if (newScore != oldScore) return newScore > oldScore
        return confidenceRank(new.confidence) < confidenceRank(old.confidence)
    }

    /**
     * Source search can return plausible catalogue hits whose titles score below the
     * matcher floor. Keep them for manual review instead of discarding the row.
     */
    private fun finalizeMatch(result: AnixartMatcher.MatchResult): AnixartMatcher.MatchResult {
        val top = result.ranked.firstOrNull() ?: return result
        if (result.confidence == AnixartMatcher.Confidence.NO_MATCH && top.score > 0) {
            return result.copy(
                confidence = AnixartMatcher.Confidence.NEEDS_REVIEW,
                best = top,
            )
        }
        return result
    }

    private fun confidenceRank(confidence: AnixartMatcher.Confidence): Int = when (confidence) {
        AnixartMatcher.Confidence.AUTO -> 0
        AnixartMatcher.Confidence.NEEDS_REVIEW -> 1
        AnixartMatcher.Confidence.NO_MATCH -> 2
    }
}
