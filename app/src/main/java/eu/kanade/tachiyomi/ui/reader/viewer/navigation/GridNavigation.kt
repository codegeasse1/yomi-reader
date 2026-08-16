package eu.kanade.tachiyomi.ui.reader.viewer.navigation

import android.graphics.RectF
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation

/**
 * Customizable 3x3 grid navigation. Every cell resolves to a user selected
 * action, so the reader behaves exactly like the layout configured in the
 * tap zones editor.
 */
class GridNavigation(serializedActions: String) : ViewerNavigation() {

    override var regionList: List<Region> = parseCustomTapZoneTokens(serializedActions)
        .mapIndexed { index, token ->
            val row = index / 3
            val column = index % 3
            Region(
                rectF = RectF(
                    column / 3f,
                    row / 3f,
                    (column + 1) / 3f,
                    (row + 1) / 3f,
                ),
                type = when (token) {
                    "MENU" -> NavigationRegion.MENU
                    "PREV" -> NavigationRegion.PREV
                    "NEXT" -> NavigationRegion.NEXT
                    "LEFT" -> NavigationRegion.LEFT
                    "RIGHT" -> NavigationRegion.RIGHT
                    else -> NavigationRegion.NONE
                },
            )
        }
}
