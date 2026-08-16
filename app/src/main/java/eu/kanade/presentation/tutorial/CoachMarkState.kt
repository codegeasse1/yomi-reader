package eu.kanade.presentation.tutorial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Holds the on-screen bounds of each registered [TipAnchor] plus the currently
 * displayed tip. Lives at the app root and is exposed via [LocalCoachMarkState].
 */
class CoachMarkState {

    private val anchorBounds = mutableStateMapOf<TipAnchor, Rect>()

    var activeTip by mutableStateOf<CoachTip?>(null)
        internal set

    var isBottomBarVisible by mutableStateOf(false)

    fun updateAnchor(anchor: TipAnchor, bounds: Rect) {
        anchorBounds[anchor] = bounds
    }

    fun removeAnchor(anchor: TipAnchor) {
        anchorBounds.remove(anchor)
    }

    fun boundsOf(anchor: TipAnchor): Rect? = anchorBounds[anchor]
}

val LocalCoachMarkState = staticCompositionLocalOf { CoachMarkState() }

/**
 * Marks a composable as the visual target for [anchor]. The element reports its
 * position in root coordinates so the overlay can spotlight it. No-op cost when
 * no tutorial is active.
 */
fun Modifier.coachAnchor(anchor: TipAnchor): Modifier = composed {
    val state = LocalCoachMarkState.current
    onGloballyPositioned { coordinates ->
        state.updateAnchor(anchor, coordinates.boundsInRoot())
    }
}

/**
 * Maps a navigation [tab] to its [TipAnchor] (by class simple name) and registers
 * it. Returns the original modifier unchanged for tabs that have no tip. This lets
 * the bottom navigation participate in the tour without hard-coding tab types.
 */
fun Modifier.coachAnchorForTab(tab: eu.kanade.presentation.util.Tab): Modifier {
    val name = tab::class.simpleName.orEmpty()
    val anchor = when {
        name.contains("Library", ignoreCase = true) -> TipAnchor.LIBRARY_TAB
        name.contains("Browse", ignoreCase = true) -> TipAnchor.BROWSE_TAB
        name.contains("Source", ignoreCase = true) -> TipAnchor.BROWSE_TAB
        name.contains("Updates", ignoreCase = true) -> TipAnchor.UPDATES_TAB
        name.contains("More", ignoreCase = true) -> TipAnchor.MORE_TAB
        else -> null
    } ?: return this
    return this.coachAnchor(anchor)
}
