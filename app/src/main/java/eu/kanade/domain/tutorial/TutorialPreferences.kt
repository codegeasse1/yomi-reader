package eu.kanade.domain.tutorial

import eu.kanade.domain.tutorial.model.TutorialMode
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * Persisted state for the beginner tutorial / coach-mark system.
 *
 * Mirrors the [eu.kanade.domain.ui.UiPreferences] pattern (PreferenceStore-backed).
 * Designed to be extension-safe: new tips only add ids to [shownTips], no migrations.
 */
class TutorialPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun tutorialMode() = preferenceStore.getEnum("tutorial_mode", TutorialMode.GUIDED)

    fun tourCompleted() = preferenceStore.getBoolean("tutorial_tour_completed", false)

    /** Set of CoachTip ids that have already been displayed (shown once). */
    fun shownTips() = preferenceStore.getStringSet("tutorial_shown_tips", emptySet())
}
