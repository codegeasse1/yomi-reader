package eu.kanade.tachiyomi.ui.reader.novel

/**
 * A single section of the book spine: one block of the compiled book artifact. A block is a window
 * over the continuous body, so it usually covers one chapter and sometimes straddles a boundary and
 * covers two; see [chapterIds].
 *
 * [charCount] is the sanitized text length taken from the artifact index and is the ONLY size domain
 * progress is computed in. Because a spine is only ever built from a compiled artifact, the value is
 * always exact and [isMeasured] is always true.
 *
 * Renderer pixel heights deliberately have no place here: they change with font size, margins and
 * theme, so letting them reach the progress domain is what made book-mode progress drift.
 */
data class NovelBookSection(
    /**
     * First real chapter this section contains.
     *
     * Sections used to carry a synthetic key (`-(blockIndex + 1)`) so that block indices could not
     * collide with chapter ids. That key is what made [NovelBookSpine.indexOf] fail for TTS and
     * translations, because no real chapter id was ever found in the spine. Sections are addressed
     * by [index] everywhere now, so this can be the real chapter id again.
     */
    val chapterId: Long,
    val index: Int,
    val name: String,
    val charCount: Int,
    val isMeasured: Boolean,
    /**
     * Every chapter whose text overlaps this section, in reading order.
     *
     * A compiled block is a window over the continuous body, so it usually covers one chapter and
     * sometimes straddles a boundary and covers two. Empty means "only [chapterId]".
     */
    val chapterIds: List<Long> = emptyList(),
) {
    /**
     * Key this section is cached and loaded under.
     *
     * It is the spine index, because that is the only unique address of a section: a block can hold
     * two chapters and a chapter can span several blocks.
     */
    val loaderKey: Long get() = index.toLong()

    /** Chapters of this section, always non-empty for a section built from an artifact. */
    val coveredChapterIds: List<Long> get() = chapterIds.ifEmpty { listOf(chapterId) }

    fun covers(chapterId: Long): Boolean = coveredChapterIds.contains(chapterId)
}

/**
 * A stable position inside the book: section plus character offset inside that section.
 *
 * This is the book-mode equivalent of an EPUB CFI and is independent of font size, theme and
 * pagination, unlike the per-chapter page index used by the classic chapter-by-chapter reader.
 */
data class NovelBookLocation(
    val sectionIndex: Int,
    val charOffset: Int,
) {
    companion object {
        val START = NovelBookLocation(sectionIndex = 0, charOffset = 0)
    }
}

/**
 * Ordered spine of a novel plus the location map used by the continuous ("book mode") reader.
 *
 * Pure data and math only: no Android, no IO, no reader state. The classic chapter reader keeps
 * working untouched; this model is additive and only consumed by book mode.
 */
data class NovelBookSpine(
    val sections: List<NovelBookSection>,
) {

    /** Total sanitized text length of the whole book. */
    val totalCharCount: Int = sections.sumOf { it.charCount }

    /** Global start offset of every section, used for O(log n) location lookups. */
    private val startOffsets: List<Int> = buildList(sections.size) {
        var running = 0
        sections.forEach { section ->
            add(running)
            running += section.charCount
        }
    }

    val isEmpty: Boolean get() = sections.isEmpty()

    val measuredSectionCount: Int get() = sections.count { it.isMeasured }

    /**
     * Cumulative section boundaries in `0f..1f` with `sections.size + 1` entries: section `i` spans
     * `sectionFractions[i]` to `sectionFractions[i + 1]`.
     *
     * This is the book's size domain (the equivalent of foliate's `getSectionFractions()`), so every
     * renderer, the progress bar and the chapter ticks all read positions from one coordinate system
     * instead of deriving them from whatever the current renderer happens to have measured.
     */
    val sectionFractions: List<Float> = buildList(sections.size + 1) {
        val total = totalCharCount.coerceAtLeast(1).toFloat()
        var running = 0
        add(0f)
        sections.forEach { section ->
            running += section.charCount
            add((running / total).coerceIn(0f, 1f))
        }
    }

    /**
     * First section that contains [chapterId], or -1.
     *
     * A chapter can span several blocks and a block can span two chapters, so the lookup goes
     * through the chapters a section covers instead of comparing one id per section.
     */
    fun indexOf(chapterId: Long): Int = sections.indexOfFirst { it.covers(chapterId) }

    fun sectionAt(sectionIndex: Int): NovelBookSection? = sections.getOrNull(sectionIndex)

    fun sectionOf(chapterId: Long): NovelBookSection? = sections.firstOrNull { it.covers(chapterId) }

    fun startOffsetOf(sectionIndex: Int): Int = startOffsets.getOrNull(sectionIndex) ?: 0

    /** Start fraction of a section in the book size domain. */
    fun sectionStartFraction(sectionIndex: Int): Float =
        sectionFractions.getOrNull(sectionIndex.coerceAtLeast(0)) ?: 0f

    /** Clamps a location into the current spine bounds, tolerating stale or out-of-range input. */
    fun clampLocation(location: NovelBookLocation): NovelBookLocation {
        if (sections.isEmpty()) return NovelBookLocation.START
        val sectionIndex = location.sectionIndex.coerceIn(0, sections.lastIndex)
        val maxOffset = (sections[sectionIndex].charCount - 1).coerceAtLeast(0)
        return NovelBookLocation(
            sectionIndex = sectionIndex,
            charOffset = location.charOffset.coerceIn(0, maxOffset),
        )
    }

    fun globalOffsetOf(location: NovelBookLocation): Int {
        if (sections.isEmpty()) return 0
        val clamped = clampLocation(location)
        return startOffsetOf(clamped.sectionIndex) + clamped.charOffset
    }

    fun locationOf(globalOffset: Int): NovelBookLocation {
        if (sections.isEmpty()) return NovelBookLocation.START
        val target = globalOffset.coerceIn(0, (totalCharCount - 1).coerceAtLeast(0))
        val searchResult = startOffsets.binarySearch(target)
        val sectionIndex = if (searchResult >= 0) {
            searchResult
        } else {
            (-searchResult - 2).coerceIn(0, sections.lastIndex)
        }
        return NovelBookLocation(
            sectionIndex = sectionIndex,
            charOffset = (target - startOffsetOf(sectionIndex)).coerceAtLeast(0),
        )
    }

    /** Whole-book progress in `0f..1f`, used for the book progress bar and time-left estimates. */
    fun progressOf(location: NovelBookLocation): Float {
        if (sections.isEmpty() || totalCharCount <= 0) return 0f
        return (globalOffsetOf(location).toFloat() / totalCharCount.toFloat()).coerceIn(0f, 1f)
    }

    /** Progress inside a single section in `0f..1f`, used to decide when a chapter counts as read. */
    fun sectionProgressOf(location: NovelBookLocation): Float {
        val clamped = clampLocation(location)
        val section = sectionAt(clamped.sectionIndex) ?: return 0f
        if (section.charCount <= 0) return 0f
        return (clamped.charOffset.toFloat() / section.charCount.toFloat()).coerceIn(0f, 1f)
    }

    fun locationFor(chapterId: Long, charOffset: Int = 0): NovelBookLocation? {
        val sectionIndex = indexOf(chapterId).takeIf { it >= 0 } ?: return null
        return clampLocation(NovelBookLocation(sectionIndex, charOffset))
    }

    /**
     * Maps legacy per-chapter progress (a fraction of the chapter) onto a book location so existing
     * saved progress keeps working when book mode is enabled for the first time.
     */
    fun locationForChapterFraction(chapterId: Long, fraction: Float): NovelBookLocation? {
        val section = sectionOf(chapterId) ?: return null
        val safeFraction = fraction.coerceIn(0f, 1f)
        val charOffset = (section.charCount * safeFraction).toInt()
        return clampLocation(NovelBookLocation(section.index, charOffset))
    }

    /** Section indices that should be kept resident in the reader, centered on [sectionIndex]. */
    fun windowAround(sectionIndex: Int, radius: Int): List<Int> {
        if (sections.isEmpty()) return emptyList()
        val center = sectionIndex.coerceIn(0, sections.lastIndex)
        val safeRadius = radius.coerceAtLeast(0)
        val start = (center - safeRadius).coerceAtLeast(0)
        val end = (center + safeRadius).coerceAtMost(sections.lastIndex)
        return (start..end).toList()
    }

    companion object {
        val EMPTY = NovelBookSpine(emptyList())
    }
}
