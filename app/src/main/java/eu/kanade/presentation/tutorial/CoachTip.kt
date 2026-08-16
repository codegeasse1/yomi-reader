package eu.kanade.presentation.tutorial

import dev.icerock.moko.resources.StringResource

/**
 * Logical UI targets a coach mark can point at. A screen registers its element
 * position for an anchor via [Modifier.coachAnchor]; the controller looks the
 * position up when deciding where to draw the spotlight.
 */
enum class TipAnchor {
    LIBRARY_TAB,
    BROWSE_TAB,
    UPDATES_TAB,
    MORE_TAB,
    ADD_REPO_BUTTON,
    PLAYER_CONTROLS,
    READER_MENU,
}

/** Which logical flow a tip belongs to. */
enum class TipGroup {
    ONBOARDING_TOUR,
    CONTEXTUAL,
}

/** When a tip becomes eligible to show. */
sealed interface TipTrigger {
    /** Shown as part of the ordered welcome tour. */
    data object InTourSequence : TipTrigger

    /** Shown the first time its screen is entered. */
    data object OnScreenEnter : TipTrigger

    /** Shown after a delay once its anchor is on screen. */
    data class AfterDelay(val ms: Long) : TipTrigger
}

/**
 * A single declarative coach mark. Tips are pure data so adding guidance never
 * requires touching the overlay/controller code.
 */
data class CoachTip(
    val id: String,
    val anchor: TipAnchor,
    val titleRes: StringResource,
    val bodyRes: StringResource,
    val group: TipGroup,
    val trigger: TipTrigger,
    val order: Int = 0,
    val prerequisite: String? = null,
)
