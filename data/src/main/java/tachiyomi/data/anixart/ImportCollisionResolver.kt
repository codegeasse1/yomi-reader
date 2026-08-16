package tachiyomi.data.anixart

/**
 * Resolves "two different import rows claim the SAME catalogue entry" collisions.
 *
 * Why this exists: [AnixartImportPlanner] (and its Shikimori twin) key actions by
 * `sourceId|url`, so two review rows whose fuzzy match landed on the same catalogue
 * entry silently collapse into a single action. That is correct for a genuine
 * duplicate inside the export, but wrong when two DIFFERENT titles both grabbed the
 * same hit — typically seasons of one franchise that [AnixartRow.cleanAnimeTitle]
 * reduced to the same search query. The user then sees "found 100" on the review
 * screen and "added 86" on the done screen, with nothing telling them which 14 rows
 * disappeared.
 *
 * This pass runs after matching and before review:
 *  - rows that genuinely duplicate each other (same normalized identity AND the same
 *    pick) keep the shared candidate and are counted as [Report.duplicates];
 *  - otherwise the highest scoring row keeps the contested candidate and every loser
 *    falls back to its next free candidate ([Report.reassigned]) or is released to
 *    "not found" ([Report.released]) so it stays visible in the review list and can be
 *    searched manually, instead of silently vanishing during import.
 *
 * Pure and side-effect free so it can be unit tested without Android.
 */
object ImportCollisionResolver {

    /** One review row as seen by the resolver. [index] is its position in the list. */
    data class Row(
        val index: Int,
        /** Normalized primary title; rows sharing it are treated as the same work. */
        val identity: String,
        val ranked: List<AnixartMatcher.ScoredCandidate>,
        val selectedId: Long?,
        val enabled: Boolean,
    )

    data class Resolution(
        val index: Int,
        val selectedId: Long?,
        val enabled: Boolean,
        /** The row lost its first choice and fell back to another candidate. */
        val reassigned: Boolean,
        /** No free candidate was left; the row is now unmatched. */
        val released: Boolean,
        /** Index of the row this one genuinely duplicates, or null. */
        val duplicateOf: Int?,
    )

    data class Report(
        val reassigned: Int,
        val released: Int,
        val duplicates: Int,
    )

    /** Same key the planners use to collapse actions. */
    fun candidateKey(candidate: AnixartMatcher.SearchCandidate): String =
        candidate.sourceId.toString() + "|" + candidate.url.ifEmpty { candidate.id.toString() }

    /** Identity used to tell a real duplicate row apart from a mis-match collision. */
    fun identityOf(titles: List<String>): String =
        AnixartMatcher.normalize(titles.firstOrNull().orEmpty())

    fun resolve(rows: List<Row>): Pair<List<Resolution>, Report> {
        if (rows.isEmpty()) return emptyList<Resolution>() to Report(0, 0, 0)

        val resolutions = HashMap<Int, Resolution>(rows.size)
        val active = ArrayList<Row>(rows.size)

        // Rows the user already skipped, or that never matched, pass through untouched.
        for (row in rows) {
            if (!row.enabled || row.selectedId == null || row.ranked.isEmpty()) {
                resolutions[row.index] = passthrough(row)
            } else {
                active += row
            }
        }

        // Group genuine duplicates behind a single competitor so the export listing
        // one title twice still results in one merged library entry, not a bogus
        // "second best" match for the second occurrence.
        val ownerByDuplicateKey = HashMap<String, Int>()
        val followers = HashMap<Int, MutableList<Int>>()
        val competitors = ArrayList<Row>(active.size)
        for (row in active) {
            val duplicateKey = row.identity + "@@" + row.selectedId
            if (row.identity.isNotEmpty()) {
                val owner = ownerByDuplicateKey[duplicateKey]
                if (owner != null) {
                    followers.getOrPut(owner) { ArrayList() } += row.index
                    continue
                }
                ownerByDuplicateKey[duplicateKey] = row.index
            }
            competitors += row
        }

        // Best score wins the contested entry; ties fall back to file order so the
        // outcome is deterministic across runs.
        val ordered = competitors.sortedWith(
            compareByDescending<Row> { scoreOf(it) }.thenBy { it.index },
        )

        val claimed = HashSet<String>()
        var reassigned = 0
        var released = 0

        for (row in ordered) {
            val current = row.ranked.firstOrNull { it.candidate.id == row.selectedId }
            if (current != null && claimed.add(candidateKey(current.candidate))) {
                resolutions[row.index] = Resolution(
                    index = row.index,
                    selectedId = row.selectedId,
                    enabled = true,
                    reassigned = false,
                    released = false,
                    duplicateOf = null,
                )
                continue
            }
            val fallback = row.ranked.firstOrNull { scored ->
                scored.score > 0 && candidateKey(scored.candidate) !in claimed
            }
            if (fallback != null) {
                claimed += candidateKey(fallback.candidate)
                reassigned++
                resolutions[row.index] = Resolution(
                    index = row.index,
                    selectedId = fallback.candidate.id,
                    enabled = true,
                    reassigned = true,
                    released = false,
                    duplicateOf = null,
                )
            } else {
                released++
                resolutions[row.index] = Resolution(
                    index = row.index,
                    selectedId = null,
                    enabled = false,
                    reassigned = false,
                    released = true,
                    duplicateOf = null,
                )
            }
        }

        // Followers mirror whatever their owner ended up with, so a duplicate never
        // drags a wrong entry into the library on its own.
        var duplicates = 0
        for ((ownerIndex, followerIndexes) in followers) {
            val owner = resolutions.getValue(ownerIndex)
            for (followerIndex in followerIndexes) {
                duplicates++
                resolutions[followerIndex] = Resolution(
                    index = followerIndex,
                    selectedId = owner.selectedId,
                    enabled = owner.enabled,
                    reassigned = false,
                    released = owner.released,
                    duplicateOf = ownerIndex,
                )
            }
        }

        return rows.map { resolutions.getValue(it.index) } to
            Report(reassigned = reassigned, released = released, duplicates = duplicates)
    }

    private fun passthrough(row: Row) = Resolution(
        index = row.index,
        selectedId = row.selectedId,
        enabled = row.enabled,
        reassigned = false,
        released = false,
        duplicateOf = null,
    )

    private fun scoreOf(row: Row): Int =
        row.ranked.firstOrNull { it.candidate.id == row.selectedId }?.score ?: 0
}
