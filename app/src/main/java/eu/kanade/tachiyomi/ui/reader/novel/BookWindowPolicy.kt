package eu.kanade.tachiyomi.ui.reader.novel

import kotlin.math.abs

/**
 * The one window policy of book mode.
 *
 * Book mode used to size its window in three places at once: the prefetch planner, the render
 * coordinator and a hardcoded `> 5` inside the book engine. Two of them planned against the spine
 * while the third pruned against the live document, so the same reading position could be resident
 * for one subsystem and prunable for the other - which is what made a chapter boundary load the
 * same section two or three times.
 *
 * Every radius now comes from here, and the only tuning knob is [TARGET_RESIDENT_CHARS]: the amount
 * of text that stays mounted around the reader, independent of how the book happens to be sliced.
 */
data class BookWindowPolicy(
    val residentRadius: Int = DEFAULT_RESIDENT_RADIUS,
    val prefetchAhead: Int = DEFAULT_PREFETCH_AHEAD,
    val prefetchBehind: Int = DEFAULT_PREFETCH_BEHIND,
    val maxConcurrentPrefetch: Int = DEFAULT_MAX_CONCURRENT_PREFETCH,
) {

    /** How many sections the renderer keeps mounted at once. */
    val residentSectionCount: Int get() = residentRadius * 2 + 1

    /** Sections that must stay mounted for a reader sitting in [center]. */
    fun residentSections(center: Int, sectionCount: Int): List<Int> {
        if (sectionCount <= 0) return emptyList()
        val last = sectionCount - 1
        val middle = center.coerceIn(0, last)
        val first = (middle - residentRadius).coerceAtLeast(0)
        val end = (middle + residentRadius).coerceAtMost(last)
        return (first..end).toList()
    }

    /** Sections worth holding prepared: the resident window plus the look-ahead around it. */
    fun cacheRange(center: Int, sectionCount: Int): IntRange {
        if (sectionCount <= 0) return IntRange.EMPTY
        val last = sectionCount - 1
        val middle = center.coerceIn(0, last)
        val first = (middle - residentRadius - prefetchBehind.coerceAtLeast(0)).coerceAtLeast(0)
        val end = (middle + residentRadius + prefetchAhead.coerceAtLeast(0)).coerceAtMost(last)
        return first..end
    }

    /**
     * Sections to prepare next, in priority order: what has to be on screen right now, then the
     * look-ahead, then the sections behind for a reader scrolling backwards.
     */
    fun prefetchOrder(
        center: Int,
        sectionCount: Int,
        prepared: Set<Int> = emptySet(),
        inFlight: Set<Int> = emptySet(),
    ): List<Int> {
        val range = cacheRange(center, sectionCount)
        if (range.isEmpty()) return emptyList()
        val middle = center.coerceIn(range.first, range.last)
        val ordered = LinkedHashSet<Int>()
        residentSections(middle, sectionCount).sortedBy { abs(it - middle) }.forEach(ordered::add)
        for (index in middle + 1..range.last) ordered.add(index)
        for (index in middle - 1 downTo range.first) ordered.add(index)
        return ordered.filter { it in range && it !in prepared && it !in inFlight }
    }

    /** Sections that may be handed to the loader right now, respecting [maxConcurrentPrefetch]. */
    fun nextPrefetchBatch(order: List<Int>, inFlightCount: Int): List<Int> {
        val slots = (maxConcurrentPrefetch - inFlightCount.coerceAtLeast(0)).coerceAtLeast(0)
        if (slots == 0) return emptyList()
        return order.take(slots)
    }

    /** Prepared sections that left the cache window and may be dropped, furthest away first. */
    fun releasableSections(center: Int, sectionCount: Int, prepared: Set<Int>): List<Int> {
        if (prepared.isEmpty()) return emptyList()
        val range = cacheRange(center, sectionCount)
        val middle = center.coerceIn(0, (sectionCount - 1).coerceAtLeast(0))
        return prepared
            .filter { it !in range && it in 0 until sectionCount }
            .sortedByDescending { abs(it - middle) }
    }

    companion object {
        /**
         * Two sections on each side, i.e. the five resident sections the engine used to keep
         * through a hardcoded `> 5`. Keeping the number identical means moving the knob here is a
         * refactor, not a behaviour change.
         */
        const val DEFAULT_RESIDENT_RADIUS = 2
        const val DEFAULT_PREFETCH_AHEAD = 3
        const val DEFAULT_PREFETCH_BEHIND = 1
        const val DEFAULT_MAX_CONCURRENT_PREFETCH = 2

        /**
         * Text that stays mounted around the reading position, in characters.
         *
         * Counting the window in sections was fine while a section was a whole chapter, but a
         * compiled book is sliced into fixed-size blocks: shrinking the block size silently shrank
         * the mounted window with it, so one fling left the window, pruned the section under the
         * reader and threw the reader back to the anchored position.
         */
        const val TARGET_RESIDENT_CHARS = 240_000

        val DEFAULT = BookWindowPolicy()

        /** Policy for a book whose sections are [blockChars] characters long. */
        fun forBlockChars(blockChars: Int, base: BookWindowPolicy = DEFAULT): BookWindowPolicy {
            if (blockChars <= 0) return base
            val radius = ((TARGET_RESIDENT_CHARS / blockChars) / 2).coerceIn(1, 8)
            return base.copy(
                residentRadius = radius.coerceAtLeast(base.residentRadius),
                prefetchAhead = (radius + 2).coerceAtLeast(base.prefetchAhead),
                prefetchBehind = radius.coerceAtLeast(base.prefetchBehind),
            )
        }
    }
}

/**
 * What the mounted renderer has to hold right now.
 *
 * This is the whole channel between the core and a renderer: the core publishes the window, the
 * renderer pulls the sections it is missing through `loadSection(index)` and drops the rest. There
 * is no command queue and no acknowledgement anymore, so a renderer that was rebuilt underneath the
 * core simply re-reads the state instead of having to be re-seeded.
 */
data class NovelBookWindowState(
    val sectionCount: Int = 0,
    val residentSections: List<Int> = emptyList(),
    /**
     * Content revision per section, bumped when a mounted section's markup changed (a finished
     * translation, a visibility switch). A renderer holding an older revision re-pulls that one
     * section in place instead of the document being rebuilt around it.
     */
    val sectionRevisions: Map<Int, Long> = emptyMap(),
) {
    fun revisionOf(sectionIndex: Int): Long = sectionRevisions[sectionIndex] ?: 0L

    companion object {
        val EMPTY = NovelBookWindowState()
    }
}
