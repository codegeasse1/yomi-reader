package eu.kanade.tachiyomi.ui.reader.novel

/*
 * Reader progress is persisted in a single `Long` column (`chapter.lastPageRead`), so several
 * unrelated progress formats have to share one number line. Each format owns a half-open range and
 * is recognised purely by where its value falls.
 *
 * | Format                     | Range (inclusive)                                | Payload layout                                        |
 * | -------------------------- | ------------------------------------------------ | ----------------------------------------------------- |
 * | native scroll, no total    | 5_000_000_000 .. 5_999_999_999                    | marker + index * 1e6 + offsetPx                       |
 * | web scroll percent         | 6_000_000_000 .. 6_000_000_100                    | marker + percent                                       |
 * | page reader                | 7_000_000_000 .. 7_999_999_999_999_999            | marker + index * 1e6 + totalItems                      |
 * | native scroll, with total  | 8_000_000_000_000_000 .. ~1.008e18                | marker + index * 1e12 + totalItems * 1e6 + offsetPx    |
 *
 * Two things about this table are load-bearing.
 *
 * First, the ranges are disjoint, and the page reader range ends exactly where the with-total
 * native range begins. That adjacency used to be written as two independent constants that merely
 * happened to be equal, which left the decoder relying on the order of its own `if` checks. The
 * boundary is now declared once and the page reader end is derived from it, so the two cannot drift
 * apart in a later edit.
 *
 * Second, every encoder must stay inside its own range. The no-total native encoder did not clamp
 * its index, so an index of 1 000 produced exactly `WEB_SCROLL_MARKER` and came back out of the
 * decoder as "web scroll, 0%", and an index of 2 000 landed in the page reader range and came back
 * as a page position. Both call sites in the reader pass `totalItems` today, so this was latent
 * rather than actively corrupting saved progress, but the entry point is reachable and the failure
 * was silent. The index is now clamped to the range's own capacity.
 *
 * The packed representation is legacy. The intended replacement is separate `progress_kind`,
 * `progress_a` and `progress_b` columns; until that migration lands, the decoders below must keep
 * reading every value this file has ever written.
 */

private const val NATIVE_SCROLL_MARKER = 5_000_000_000L
private const val NATIVE_SCROLL_OFFSET_BASE = 1_000_000L
private const val WEB_SCROLL_MARKER = 6_000_000_000L
private const val WEB_SCROLL_MAX_PERCENT = 100L
private const val PAGE_READER_MARKER = 7_000_000_000L
private const val PAGE_READER_TOTAL_BASE = 1_000_000L

/**
 * Where the page reader range stops and the with-total native range starts. Declared once so the
 * two formats cannot be moved independently.
 */
private const val NATIVE_SCROLL_WITH_TOTAL_MARKER = 8_000_000_000_000_000L
private const val PAGE_READER_END = NATIVE_SCROLL_WITH_TOTAL_MARKER

private const val NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE = 1_000_000_000_000L
private const val NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE = 1_000_000L

/** Largest offset the no-total native format can carry before it overflows into the index digits. */
private const val NATIVE_SCROLL_MAX_OFFSET = NATIVE_SCROLL_OFFSET_BASE - 1

/**
 * Largest index the no-total native format can carry before it leaves its own range. The range is
 * one billion wide and each index step costs [NATIVE_SCROLL_OFFSET_BASE], which leaves room for
 * indices 0..999.
 */
private const val NATIVE_SCROLL_MAX_INDEX =
    ((WEB_SCROLL_MARKER - NATIVE_SCROLL_MARKER) / NATIVE_SCROLL_OFFSET_BASE) - 1

/** Largest item count the page reader format can carry in its low digits. */
private const val PAGE_READER_MAX_TOTAL = PAGE_READER_TOTAL_BASE - 1

/** Largest item count the with-total native format can carry in its middle digits. */
private const val NATIVE_SCROLL_WITH_TOTAL_MAX_TOTAL = NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE - 1

internal data class NativeScrollProgress(
    val index: Int,
    val offsetPx: Int,
    val totalItems: Int? = null,
)

data class PageReaderProgress(
    val index: Int,
    val totalItems: Int,
)

/**
 * Packs a native scroll position.
 *
 * Passing [totalItems] selects the wider format, which is what every live call site does. The
 * no-total format is kept because saved progress still contains values written by older builds, and
 * it can only address indices up to [NATIVE_SCROLL_MAX_INDEX]; positions beyond that are clamped
 * rather than allowed to escape into another format's range.
 */
internal fun encodeNativeScrollProgress(
    index: Int,
    offsetPx: Int,
    totalItems: Int? = null,
): Long {
    val safeOffset = offsetPx.coerceIn(0, NATIVE_SCROLL_MAX_OFFSET.toInt()).toLong()
    if (totalItems != null) {
        val maxIndex = ((Long.MAX_VALUE - NATIVE_SCROLL_WITH_TOTAL_MARKER) / NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val safeTotalItems = totalItems.coerceAtLeast(1)
            .coerceAtMost(NATIVE_SCROLL_WITH_TOTAL_MAX_TOTAL.toInt())
            .coerceAtMost(maxIndex + 1)
        val safeIndex = index.coerceIn(0, safeTotalItems - 1).toLong()
        return NATIVE_SCROLL_WITH_TOTAL_MARKER +
            (safeIndex * NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE) +
            (safeTotalItems.toLong() * NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE) +
            safeOffset
    }

    val safeIndex = index.coerceIn(0, NATIVE_SCROLL_MAX_INDEX.toInt()).toLong()
    return NATIVE_SCROLL_MARKER + (safeIndex * NATIVE_SCROLL_OFFSET_BASE) + safeOffset
}

internal fun decodeNativeScrollProgress(value: Long): NativeScrollProgress? {
    if (value >= NATIVE_SCROLL_WITH_TOTAL_MARKER) {
        val payload = value - NATIVE_SCROLL_WITH_TOTAL_MARKER
        val index = (payload / NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
        val totalItems = ((payload % NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE) / NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
        val offset = (payload % NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE)
            .coerceIn(0L, NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE - 1)
            .toInt()
        return NativeScrollProgress(
            index = index.coerceIn(0, totalItems - 1),
            offsetPx = offset,
            totalItems = totalItems,
        )
    }

    if (value < NATIVE_SCROLL_MARKER || value >= WEB_SCROLL_MARKER) return null
    val payload = value - NATIVE_SCROLL_MARKER
    val index = (payload / NATIVE_SCROLL_OFFSET_BASE).toInt().coerceAtLeast(0)
    val offset = (payload % NATIVE_SCROLL_OFFSET_BASE).toInt().coerceAtLeast(0)
    return NativeScrollProgress(index = index, offsetPx = offset)
}

internal fun encodeWebScrollProgressPercent(percent: Int): Long {
    val safePercent = percent.coerceIn(0, WEB_SCROLL_MAX_PERCENT.toInt()).toLong()
    return WEB_SCROLL_MARKER + safePercent
}

internal fun decodeWebScrollProgressPercent(value: Long): Int? {
    if (value < WEB_SCROLL_MARKER || value > WEB_SCROLL_MARKER + WEB_SCROLL_MAX_PERCENT) {
        return null
    }
    return (value - WEB_SCROLL_MARKER).toInt().coerceIn(0, WEB_SCROLL_MAX_PERCENT.toInt())
}

internal fun encodePageReaderProgress(
    index: Int,
    totalItems: Int,
): Long {
    val safeTotalItems = totalItems.coerceAtLeast(1).coerceAtMost(PAGE_READER_MAX_TOTAL.toInt())
    val safeIndex = index.coerceIn(0, safeTotalItems - 1)
    return PAGE_READER_MARKER + (safeIndex.toLong() * PAGE_READER_TOTAL_BASE) + safeTotalItems.toLong()
}

internal fun decodePageReaderProgress(value: Long): PageReaderProgress? {
    if (value < PAGE_READER_MARKER || value >= PAGE_READER_END) return null
    val payload = value - PAGE_READER_MARKER
    val index = (payload / PAGE_READER_TOTAL_BASE).toInt().coerceAtLeast(0)
    val totalItems = (payload % PAGE_READER_TOTAL_BASE).toInt().coerceAtLeast(1)
    val safeIndex = index.coerceIn(0, totalItems - 1)
    return PageReaderProgress(index = safeIndex, totalItems = totalItems)
}
