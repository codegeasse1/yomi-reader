package eu.kanade.tachiyomi.ui.reader.novel

/** Start of the value range that used to mean "a whole-book position". */
private const val LEGACY_BOOK_LOCATION_MARKER = 6_100_000_000L

/** End of that range: the first value that meant "a page reader position" instead. */
private const val LEGACY_BOOK_LOCATION_END = 7_000_000_000L

private const val LEGACY_BOOK_LOCATION_FRACTION_BASE = 1_000L

private const val LEGACY_BOOK_LOCATION_MAX_PERMILLE = (LEGACY_BOOK_LOCATION_FRACTION_BASE - 1L).toInt()

private const val LEGACY_BOOK_LOCATION_MAX_SECTION_INDEX =
    (((LEGACY_BOOK_LOCATION_END - LEGACY_BOOK_LOCATION_MARKER) / LEGACY_BOOK_LOCATION_FRACTION_BASE) - 1L).toInt()

/**
 * A book-mode position written by versions before the locator.
 *
 * Older builds packed the position into the per-chapter `lastPageRead` column as
 * `marker + sectionIndex * 1000 + permille`, where the section index was an index into the chapter
 * list, not into the artifact blocks the reader uses now. Nothing writes this format any more; it
 * exists only so the one-off migration in [planBookProgressMigration] can recover a position that
 * would otherwise be lost, and it is expected to be deleted two releases after that migration ships.
 *
 * [chapterIndex] is a position in the reading-ordered chapter list, which is what the old spine used
 * as its section index.
 */
data class LegacyBookLocation(
    val chapterIndex: Int,
    val chapterPermille: Int,
) {
    val chapterFraction: Float get() = chapterPermille / LEGACY_BOOK_LOCATION_FRACTION_BASE.toFloat()
}

/**
 * Decodes a legacy book position, or returns null when [value] is any other kind of stored progress
 * (native scroll, web scroll, page reader, or a plain page number).
 */
@Deprecated("Migration only: nothing writes this format any more. Remove two releases after phase 4.")
internal fun decodeLegacyBookLocation(value: Long): LegacyBookLocation? {
    if (value < LEGACY_BOOK_LOCATION_MARKER || value >= LEGACY_BOOK_LOCATION_END) return null
    val payload = value - LEGACY_BOOK_LOCATION_MARKER
    val chapterIndex = (payload / LEGACY_BOOK_LOCATION_FRACTION_BASE)
        .coerceIn(0L, LEGACY_BOOK_LOCATION_MAX_SECTION_INDEX.toLong())
        .toInt()
    val chapterPermille = (payload % LEGACY_BOOK_LOCATION_FRACTION_BASE)
        .coerceIn(0L, LEGACY_BOOK_LOCATION_MAX_PERMILLE.toLong())
        .toInt()
    return LegacyBookLocation(chapterIndex = chapterIndex, chapterPermille = chapterPermille)
}
