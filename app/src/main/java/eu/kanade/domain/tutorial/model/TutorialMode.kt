package eu.kanade.domain.tutorial.model

/**
 * Controls how much in-app guidance (coach marks / guided tour) the user sees.
 *
 * - [GUIDED]: beginner mode. Welcome tour + contextual coach marks are shown.
 * - [OFF]: experienced mode. No tips are shown.
 */
enum class TutorialMode {
    GUIDED,
    OFF,
}
