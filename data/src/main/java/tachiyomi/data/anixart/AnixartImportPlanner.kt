package tachiyomi.data.anixart

/**
 * Pure planning layer for the Anixart importer.
 *
 * Important domain fact: in Aniyomi a "watch status" and a personal score are
 * owned by the TRACKER subsystem, not by the local library entry. A purely local
 * import therefore cannot write a status/score directly. The pragmatic, lossless
 * local representation is to map each Anixart status onto a library CATEGORY
 * (e.g. "Смотрю" -> a "Watching" category) and optionally drop starred entries
 * into a favourites category.
 *
 * This object turns the user's confirmed review selections into a concrete,
 * de-duplicated set of import actions WITHOUT performing any IO, so it can be
 * unit-tested in isolation. The coordinator ([ImportAnixartEntries]) executes the
 * resulting plan against the real interactors.
 */
object AnixartImportPlanner {

    /** A single reviewed row: the chosen candidate (or null to skip) + enabled flag. */
    data class Selection(
        val row: AnixartRow,
        val chosen: AnixartMatcher.SearchCandidate?,
        val enabled: Boolean,
    )

    /** How statuses/favourites should be projected onto categories. */
    data class Config(
        /** status -> target category id; missing entries mean "no category for this status". */
        val statusCategoryIds: Map<AnixartStatus, Long> = emptyMap(),
        /** Optional category id that starred ("Добавлено") entries are added to. */
        val favoriteCategoryId: Long? = null,
    )

    /** One concrete action to apply to the library. */
    data class Action(
        val candidate: AnixartMatcher.SearchCandidate,
        /** Categories to ADD (coordinator merges, never replaces existing). */
        val categoryIds: Set<Long>,
        /** Carried for UI/reporting; status that produced this action. */
        val status: AnixartStatus?,
        val rating: Int?,
        val isFavorite: Boolean,
    )

    data class Plan(
        val actions: List<Action>,
        val skippedDisabled: Int,
        val skippedNoMatch: Int,
        val mergedDuplicates: Int,
    )

    fun plan(selections: List<Selection>, config: Config): Plan {
        var skippedDisabled = 0
        var skippedNoMatch = 0
        var mergedDuplicates = 0
        // Preserve first-seen order while merging duplicates that resolve to the
        // same library entry (same source + url).
        val byKey = LinkedHashMap<String, Action>()

        for (sel in selections) {
            if (!sel.enabled) {
                skippedDisabled++
                continue
            }
            val candidate = sel.chosen
            if (candidate == null) {
                skippedNoMatch++
                continue
            }

            val status = sel.row.status
            val cats = buildSet {
                status?.let { s -> config.statusCategoryIds[s]?.let(::add) }
                if (sel.row.isFavorite) config.favoriteCategoryId?.let(::add)
            }

            val key = candidate.sourceId.toString() + "|" + candidate.url.ifEmpty { candidate.id.toString() }
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = Action(
                    candidate = candidate,
                    categoryIds = cats,
                    status = status,
                    rating = sel.row.ratingOutOfTen,
                    isFavorite = sel.row.isFavorite,
                )
            } else {
                // Same target anime appeared twice (e.g. duplicate Anixart rows):
                // union their categories instead of importing twice.
                mergedDuplicates++
                byKey[key] = existing.copy(
                    categoryIds = existing.categoryIds + cats,
                    isFavorite = existing.isFavorite || sel.row.isFavorite,
                    rating = existing.rating ?: sel.row.ratingOutOfTen,
                )
            }
        }

        return Plan(
            actions = byKey.values.toList(),
            skippedDisabled = skippedDisabled,
            skippedNoMatch = skippedNoMatch,
            mergedDuplicates = mergedDuplicates,
        )
    }
}
