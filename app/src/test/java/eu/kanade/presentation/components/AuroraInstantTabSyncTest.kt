package eu.kanade.presentation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuroraInstantTabSyncTest {

    @Test
    fun `entering instant mode seeds selection from pager page`() {
        val decision = resolveAuroraInstantTabSync(
            instantTabSwitching = true,
            previousInstantTabSwitching = false,
            stateCurrentPage = 2,
            instantSelectedPage = 0,
            lastIndex = 4,
        )

        assertEquals(2, decision.selectedPage)
        assertNull(decision.pagerPage)
        assertEquals(true, decision.nextPreviousInstantTabSwitching)
    }

    @Test
    fun `instant mode keeps pager aligned with the selected page`() {
        val decision = resolveAuroraInstantTabSync(
            instantTabSwitching = true,
            previousInstantTabSwitching = true,
            stateCurrentPage = 2,
            instantSelectedPage = 3,
            lastIndex = 4,
        )

        assertNull(decision.selectedPage)
        assertEquals(3, decision.pagerPage)
        assertEquals(true, decision.nextPreviousInstantTabSwitching)
    }

    @Test
    fun `leaving instant mode syncs pager to the last instant selection`() {
        val decision = resolveAuroraInstantTabSync(
            instantTabSwitching = false,
            previousInstantTabSwitching = true,
            stateCurrentPage = 1,
            instantSelectedPage = 3,
            lastIndex = 4,
        )

        assertNull(decision.selectedPage)
        assertEquals(3, decision.pagerPage)
        assertEquals(false, decision.nextPreviousInstantTabSwitching)
    }

    @Test
    fun `pager mode clamps out of range current page`() {
        val decision = resolveAuroraInstantTabSync(
            instantTabSwitching = false,
            previousInstantTabSwitching = false,
            stateCurrentPage = 8,
            instantSelectedPage = 0,
            lastIndex = 4,
        )

        assertNull(decision.selectedPage)
        assertEquals(4, decision.pagerPage)
        assertEquals(false, decision.nextPreviousInstantTabSwitching)
    }
}
