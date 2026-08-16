package eu.kanade.presentation.tutorial

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.tutorial.TutorialPreferences
import eu.kanade.domain.tutorial.model.TutorialMode
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Drives the welcome tour: decides which [CoachTip] is active, advances through
 * the ordered sequence, and persists progress (shown ids / tour completion).
 *
 * MVP scope: ordered ONBOARDING_TOUR only. Contextual tips can reuse [markShown]
 * + [CoachMarkState.activeTip] later.
 */
class TutorialController(
    private val prefs: TutorialPreferences,
    private val state: CoachMarkState,
    private val basePrefs: BasePreferences = Injekt.get(),
) {
    private var tourIndex by androidx.compose.runtime.mutableIntStateOf(-1)

    private val tour = CoachTipRegistry.tour

    val isGuided: Boolean
        get() = prefs.tutorialMode().get() == TutorialMode.GUIDED && basePrefs.shownOnboardingFlow().get()

    fun startTourIfNeeded() {
        if (!isGuided) return
        if (prefs.tourCompleted().get()) return
        if (tourIndex >= 0) return
        advanceTo(0)
    }

    fun next() {
        val active = state.activeTip
        if (active != null && active.group == TipGroup.CONTEXTUAL) {
            dismissContextual()
            return
        }
        markShown(active?.id)
        val nextIndex = tourIndex + 1
        if (nextIndex >= tour.size) {
            finishTour()
        } else {
            advanceTo(nextIndex)
        }
    }

    fun skipTour() {
        if (state.activeTip?.group == TipGroup.CONTEXTUAL) {
            dismissContextual()
            return
        }
        finishTour()
    }

    /**
     * Called when a screen is entered. Shows the first eligible CONTEXTUAL tip
     * for that screen exactly once, and never while the welcome tour is active.
     */
    fun onScreenEntered(anchor: TipAnchor) {
        if (!isGuided) return
        if (state.activeTip != null) return
        if (tourIndex in 0 until tour.size) return // tour in progress

        val tip = CoachTipRegistry.contextual.firstOrNull { candidate ->
            candidate.anchor == anchor &&
                candidate.id !in prefs.shownTips().get() &&
                state.boundsOf(candidate.anchor) != null
        } ?: return
        state.activeTip = tip
    }

    /** Dismiss the current contextual tip (mark shown). */
    fun dismissContextual() {
        markShown(state.activeTip?.id)
        state.activeTip = null
    }

    private fun advanceTo(index: Int) {
        tourIndex = index
        state.activeTip = tour.getOrNull(index)
    }

    private fun finishTour() {
        tourIndex = tour.size
        state.activeTip = null
        prefs.tourCompleted().set(true)
    }

    private fun markShown(id: String?) {
        id ?: return
        val current = prefs.shownTips().get()
        if (id !in current) {
            prefs.shownTips().set(current + id)
        }
    }
}

/**
 * Convenience host: provides [LocalCoachMarkState], wires a [TutorialController],
 * auto-starts the tour, and renders the overlay above [content].
 */
@Composable
fun TutorialHost(
    content: @Composable () -> Unit,
) {
    val prefs = remember { Injekt.get<TutorialPreferences>() }
    val basePrefs = remember { Injekt.get<BasePreferences>() }
    val shownOnboardingFlow by basePrefs.shownOnboardingFlow().collectAsState()
    val state = remember { CoachMarkState() }
    val controller = remember { TutorialController(prefs, state, basePrefs) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.CompositionLocalProvider(
        LocalCoachMarkState provides state,
        LocalTutorialController provides controller,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        ) {
            content()
            CoachMarkOverlay(
                state = state,
                onNext = { scope.launch { controller.next() } },
                onSkip = { scope.launch { controller.skipTour() } },
            )
        }
    }

    LaunchedEffect(shownOnboardingFlow) {
        if (shownOnboardingFlow) {
            controller.startTourIfNeeded()
        }
    }
}

/**
 * Exposes the active [TutorialController] to descendant screens so they can
 * report screen entry for contextual coach marks.
 */
val LocalTutorialController = androidx.compose.runtime.staticCompositionLocalOf<TutorialController?> { null }

/**
 * Drop this into a screen to trigger its contextual coach mark the first time the
 * screen is shown. No-op if there is no controller (e.g. outside [TutorialHost]).
 */
@Composable
fun TutorialScreenEntry(anchor: TipAnchor) {
    val controller = LocalTutorialController.current ?: return
    LaunchedEffect(anchor) {
        // Small delay so the anchor has been laid out & measured.
        kotlinx.coroutines.delay(500)
        controller.onScreenEntered(anchor)
    }
}
