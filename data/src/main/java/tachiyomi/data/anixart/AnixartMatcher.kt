package tachiyomi.data.anixart

import java.text.Normalizer

/**
 * Pure, side-effect-free title matcher for the Anixart importer.
 *
 * Anixart exports carry no source/url, only titles, so entries must be matched
 * against installed anime sources by NAME. This object owns the scoring and
 * confidence classification; the actual network search lives behind
 * [AnixartTitleSearcher] in the app layer so this core stays unit-testable
 * without Android.
 *
 * Scoring (0..100), taking the best over every candidate title of the row vs.
 * every title of each search result:
 *  - 100: normalized titles are identical
 *  - 70..95: one normalized title contains the other, scaled by length ratio so
 *    a very short query ("Bleach") inside a long title doesn't auto-win
 *  - otherwise: token-set overlap (Jaccard), scaled to 0..65
 *
 * The length-ratio penalty fixes a known weakness of the reference Go matcher,
 * whose containment score ignored how different the lengths were.
 */
object AnixartMatcher {

    /** A title-bearing search result coming from an installed source. */
    data class SearchCandidate(
        val id: Long,
        val sourceId: Long,
        val displayTitle: String,
        /** All titles to score against (primary + alternatives/synonyms). */
        val titles: List<String>,
        /** Source entry url, needed to persist the match into the library. */
        val url: String = "",
        val thumbnailUrl: String? = null,
    )

    enum class Confidence {
        /** High score, single clear winner — safe to import without review. */
        AUTO,

        /** Plausible but uncertain, or several close candidates — needs user review. */
        NEEDS_REVIEW,

        /** Nothing crossed the floor — leave unmatched. */
        NO_MATCH,
    }

    data class ScoredCandidate(
        val candidate: SearchCandidate,
        val score: Int,
    )

    data class MatchResult(
        val confidence: Confidence,
        /** Best candidate, or null when [confidence] is [Confidence.NO_MATCH]. */
        val best: ScoredCandidate?,
        /** Top candidates sorted by score desc (for the review dropdown). */
        val ranked: List<ScoredCandidate>,
    )

    private const val AUTO_THRESHOLD = 90
    private const val REVIEW_FLOOR = 55

    /** If the runner-up is this close to the winner, force manual review. */
    private const val AMBIGUITY_GAP = 6

    /**
     * Matches one set of [queryTitles] (from [AnixartRow.candidateTitles]) against
     * the [candidates] returned by a source search.
     */
    fun match(queryTitles: List<String>, candidates: List<SearchCandidate>): MatchResult {
        if (queryTitles.isEmpty() || candidates.isEmpty()) {
            return MatchResult(Confidence.NO_MATCH, null, emptyList())
        }

        val ranked = candidates
            .map { candidate ->
                val score = bestScore(queryTitles, candidate.titles)
                ScoredCandidate(candidate, score)
            }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenBy { it.candidate.displayTitle })

        val best = ranked.firstOrNull()
        if (best == null || best.score < REVIEW_FLOOR) {
            return MatchResult(Confidence.NO_MATCH, null, ranked.take(MAX_RANKED))
        }

        val runnerUp = ranked.getOrNull(1)
        val ambiguous = runnerUp != null && (best.score - runnerUp.score) < AMBIGUITY_GAP
        val confidence = when {
            best.score >= AUTO_THRESHOLD && !ambiguous -> Confidence.AUTO
            else -> Confidence.NEEDS_REVIEW
        }
        return MatchResult(confidence, best, ranked.take(MAX_RANKED))
    }

    private fun bestScore(queryTitles: List<String>, candidateTitles: List<String>): Int {
        var best = 0
        for (q in queryTitles) {
            for (c in candidateTitles) {
                val s = pairScore(q, c)
                if (s > best) best = s
                if (best == 100) return 100
            }
        }
        return best
    }

    /** Score a single query/candidate pair on a 0..100 scale. */
    fun pairScore(query: String, candidate: String): Int {
        val nq = normalize(query)
        val nc = normalize(candidate)
        if (nq.isEmpty() || nc.isEmpty()) return 0
        if (nq == nc) return 100

        if (nc.contains(nq) || nq.contains(nc)) {
            val shorter = minOf(nq.length, nc.length).toDouble()
            val longer = maxOf(nq.length, nc.length).toDouble()
            // 70..95 depending on how much of the longer string is covered.
            return (70 + 25 * (shorter / longer)).toInt()
        }

        val qt = nq.split(' ').filter { it.isNotEmpty() }.toSet()
        val ct = nc.split(' ').filter { it.isNotEmpty() }.toSet()
        if (qt.isEmpty() || ct.isEmpty()) return 0
        val intersection = qt.count { it in ct }
        val union = (qt + ct).size
        // Jaccard scaled to 0..65 so token overlap can never beat a real containment.
        return (65.0 * intersection / union).toInt()
    }

    /**
     * Unicode-aware normalization: NFKD fold, drop diacritics, lowercase (locale
     * independent), keep letters/digits, collapse runs of other chars to a single
     * space. Crucially this lowercases Cyrillic correctly (unlike SQLite's
     * ASCII-only LOWER), the same class of bug fixed earlier in genre matching.
     */
    fun normalize(value: String): String {
        val folded = Normalizer.normalize(value, Normalizer.Form.NFKD)
        val sb = StringBuilder(folded.length)
        var pendingSpace = false
        for (ch in folded) {
            when {
                ch.isLetterOrDigit() -> {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    pendingSpace = false
                    sb.append(ch.lowercaseChar())
                }
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> {
                    // Drop combining diacritics produced by NFKD.
                }
                else -> pendingSpace = true
            }
        }
        return sb.toString()
    }

    private const val MAX_RANKED = 5
}

/**
 * Abstraction over "search installed anime sources for a title". Implemented in
 * the app layer (it needs the source manager + network); kept out of this module
 * so the scoring core remains pure and testable.
 */
interface AnixartTitleSearcher {
    suspend fun search(query: String): List<AnixartMatcher.SearchCandidate>
}
