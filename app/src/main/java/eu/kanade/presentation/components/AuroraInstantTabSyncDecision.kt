package eu.kanade.presentation.components

internal data class AuroraInstantTabSyncDecision(
    val selectedPage: Int? = null,
    val pagerPage: Int? = null,
    val nextPreviousInstantTabSwitching: Boolean,
)

internal fun resolveAuroraInstantTabSync(
    instantTabSwitching: Boolean,
    previousInstantTabSwitching: Boolean,
    stateCurrentPage: Int,
    instantSelectedPage: Int,
    lastIndex: Int,
): AuroraInstantTabSyncDecision {
    if (lastIndex < 0) {
        return AuroraInstantTabSyncDecision(
            nextPreviousInstantTabSwitching = instantTabSwitching,
        )
    }

    val clampedStatePage = stateCurrentPage.coerceIn(0, lastIndex)
    val clampedInstantPage = instantSelectedPage.coerceIn(0, lastIndex)

    return if (instantTabSwitching) {
        if (!previousInstantTabSwitching) {
            AuroraInstantTabSyncDecision(
                selectedPage = clampedStatePage,
                nextPreviousInstantTabSwitching = true,
            )
        } else {
            AuroraInstantTabSyncDecision(
                pagerPage = clampedInstantPage.takeIf { it != clampedStatePage },
                nextPreviousInstantTabSwitching = true,
            )
        }
    } else {
        if (previousInstantTabSwitching) {
            AuroraInstantTabSyncDecision(
                pagerPage = clampedInstantPage.takeIf { it != clampedStatePage },
                nextPreviousInstantTabSwitching = false,
            )
        } else {
            AuroraInstantTabSyncDecision(
                pagerPage = if (stateCurrentPage > lastIndex) lastIndex else null,
                nextPreviousInstantTabSwitching = false,
            )
        }
    }
}
