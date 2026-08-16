package eu.kanade.presentation.tutorial

import tachiyomi.i18n.MR

/**
 * Central, extension-safe registry of all coach marks.
 *
 * MVP scope: a 3-step welcome tour (Library -> Browse -> Add repository) plus a
 * contextual player tip. New tips are added here only.
 *
 * Legal note: the add-repository tip teaches *mechanics* with a placeholder URL
 * and explicitly tells users to use legal sources only. It never references real
 * repositories. See LEGAL_RISK_REPORT.md.
 */
object CoachTipRegistry {

    val tips: List<CoachTip> = listOf(
        CoachTip(
            id = "tour_library",
            anchor = TipAnchor.LIBRARY_TAB,
            titleRes = MR.strings.tip_library_title,
            bodyRes = MR.strings.tip_library_body,
            group = TipGroup.ONBOARDING_TOUR,
            trigger = TipTrigger.InTourSequence,
            order = 0,
        ),
        CoachTip(
            id = "tour_browse",
            anchor = TipAnchor.BROWSE_TAB,
            titleRes = MR.strings.tip_browse_title,
            bodyRes = MR.strings.tip_browse_body,
            group = TipGroup.ONBOARDING_TOUR,
            trigger = TipTrigger.InTourSequence,
            order = 1,
            prerequisite = "tour_library",
        ),
        CoachTip(
            id = "tour_add_repo",
            anchor = TipAnchor.ADD_REPO_BUTTON,
            titleRes = MR.strings.tip_add_repo_title,
            bodyRes = MR.strings.tip_add_repo_body,
            group = TipGroup.ONBOARDING_TOUR,
            trigger = TipTrigger.InTourSequence,
            order = 2,
            prerequisite = "tour_browse",
        ),
        CoachTip(
            id = "ctx_more_settings",
            anchor = TipAnchor.MORE_TAB,
            titleRes = MR.strings.tip_more_title,
            bodyRes = MR.strings.tip_more_body,
            group = TipGroup.CONTEXTUAL,
            trigger = TipTrigger.OnScreenEnter,
        ),
    )

    /** Contextual tips shown on first screen entry (outside the tour). */
    val contextual: List<CoachTip> = tips.filter { it.group == TipGroup.CONTEXTUAL }

    /** Ordered tips that make up the welcome tour. */
    val tour: List<CoachTip> = tips
        .filter { it.group == TipGroup.ONBOARDING_TOUR }
        .sortedBy { it.order }
}
