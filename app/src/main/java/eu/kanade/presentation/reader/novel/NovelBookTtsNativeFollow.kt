package eu.kanade.presentation.reader.novel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor

/**
 * Where the native book renderer currently paints each block.
 *
 * The native renderer keeps one lazy list item per book section, and a section is tens of thousands
 * of characters long. Scrolling to the item therefore lands on the section's first paragraph and
 * stays there for the whole section: the voice moved, the screen did not. The blocks report the
 * position they were laid out at, so follow-along can scroll to the paragraph instead of to the
 * section, and a manual scroll can be undone by returning to the block the voice is on.
 */
@Stable
internal class NovelBookTtsBlockPositions {

    private val topsInWindow = mutableStateMapOf<String, Float>()

    private var viewportTop by mutableFloatStateOf(0f)
    private var viewportHeight by mutableFloatStateOf(0f)

    /** Records the list viewport, in the same window coordinates the blocks report. */
    fun recordViewport(topInWindow: Float, heightPx: Float) {
        viewportTop = topInWindow
        viewportHeight = heightPx
    }

    fun recordBlock(anchor: NovelBlockAnchor?, topInWindow: Float) {
        if (anchor == null) return
        topsInWindow[anchor.domId] = topInWindow
    }

    fun forgetBlock(anchor: NovelBlockAnchor?) {
        if (anchor == null) return
        topsInWindow.remove(anchor.domId)
    }

    /** True while [anchor] is composed and has reported a position. */
    fun isLaidOut(anchor: NovelBlockAnchor): Boolean = topsInWindow.containsKey(anchor.domId)

    /**
     * Pixels the list has to scroll to bring [anchor] into the reading band, or `null` while that
     * block is not laid out - the caller then waits for the section scroll to compose it.
     */
    fun scrollDeltaFor(anchor: NovelBlockAnchor): Float? {
        val top = topsInWindow[anchor.domId] ?: return null
        return novelBookTtsScrollDelta(
            blockTopInWindow = top,
            viewportTopInWindow = viewportTop,
            viewportHeightPx = viewportHeight,
        )
    }
}

/**
 * Keeps the spoken block a third of the way down the viewport, which is where the WebView anchor
 * script puts it too, so both renderers follow the voice the same way.
 */
internal fun novelBookTtsScrollDelta(
    blockTopInWindow: Float,
    viewportTopInWindow: Float,
    viewportHeightPx: Float,
): Float {
    val readingBandOffset = if (viewportHeightPx > 0f) viewportHeightPx / 3f else 0f
    return blockTopInWindow - viewportTopInWindow - readingBandOffset
}

/** Below this the block is already where the reader is looking, and scrolling would only jitter. */
internal const val NOVEL_BOOK_TTS_SCROLL_EPSILON_PX = 8f

/**
 * Frames to wait for the section scroll to compose the spoken block before giving up.
 *
 * The wait is restarted whenever the resident sections change, so a block that is still being
 * loaded is not lost: the budget only bounds one attempt, it is not the total number of tries.
 */
internal const val NOVEL_BOOK_TTS_SCROLL_ATTEMPTS = 12
