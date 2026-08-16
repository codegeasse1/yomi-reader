package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Immutable

/**
 * What the reader chrome draws around the content: the progress readout, the labels at the ends of
 * the vertical seekbar, and the tick marks along it.
 *
 * The chrome used to derive each of these inline with its own `if (bookMode.isEnabled)` branch, so
 * the same question was answered in several places and the answers could disagree. Here the mode is
 * an input to one pure function and the chrome only renders the result.
 *
 * The rail labels are nullable on purpose: both the page reader and the scroll percentage hide them
 * when there is nothing meaningful to count, and book mode always has something to count.
 */
@Immutable
internal data class ReaderChromeState(
    val progressPercent: Int,
    val railTopLabel: String?,
    val railBottomLabel: String?,
    val tickFractions: List<Float>,
)

/**
 * Builds the [ReaderChromeState] for the current reading mode.
 *
 * In book mode the reader never leaves the book, so the rail counts towards the end of the novel and
 * carries no per-chapter ticks: page ticks would mark positions the paged flow no longer stops at.
 */
internal fun resolveReaderChromeState(
    bookModeEnabled: Boolean,
    readingProgressPercent: Int,
    usePageReader: Boolean,
    pageIndex: Int,
    pageCount: Int,
    showScrollPercentage: Boolean,
): ReaderChromeState {
    val progressPercent = readingProgressPercent.coerceIn(0, 100)
    val (topLabel, bottomLabel) = when {
        bookModeEnabled -> "$progressPercent%" to "100%"
        usePageReader -> resolveReaderPageRailLabels(
            pageIndex = pageIndex,
            pageCount = pageCount,
        )
        else -> verticalSeekbarLabels(
            readingProgressPercent = readingProgressPercent,
            showScrollPercentage = showScrollPercentage,
        )
    }
    val tickFractions = if (!bookModeEnabled && usePageReader) {
        resolveReaderVerticalSeekbarTickFractions(pageCount)
    } else {
        emptyList()
    }
    return ReaderChromeState(
        progressPercent = progressPercent,
        railTopLabel = topLabel,
        railBottomLabel = bottomLabel,
        tickFractions = tickFractions,
    )
}
