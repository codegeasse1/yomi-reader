package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import kotlin.math.roundToInt

internal const val AUTO_SCROLL_PREFETCH_THRESHOLD_PERCENT = 85
internal const val AUTO_SCROLL_HANDOFF_TTL_MS = 30_000L
internal const val AUTO_SCROLL_END_DWELL_MS = 1_500L

internal enum class NovelAutoScrollMode {
    Off,
    Running,
    Cooldown,
    EndDwell,
    Handoff,
    Paused,
}

internal data class NovelAutoScrollConfig(
    val enabled: Boolean,
    val speed: Int,
    val chapterEndBehavior: NovelAutoScrollChapterEndBehavior,
    val endPauseMs: Long,
    val endOffsetPx: Int,
)

internal data class NovelAutoScrollEndState(
    val isAtEnd: Boolean,
    val stableEndFrameCount: Int,
    val shouldEnterDwell: Boolean,
    val shouldAdvanceNow: Boolean,
)

data class NovelAutoScrollHandoffState(
    val fromChapterId: Long,
    val targetChapterId: Long,
    val speed: Int,
    val requestedAtMs: Long,
)

internal fun resolveNovelAutoScrollEndState(
    canScrollForward: Boolean,
    scrollConsumedPx: Float,
    isContentReady: Boolean,
    hasCompletedInitialLayout: Boolean,
    hasRenderableItems: Boolean,
    previousStableEndFrameCount: Int,
    requiredStableFrames: Int = 2,
): NovelAutoScrollEndState {
    val canEvaluateEnd = isContentReady && hasCompletedInitialLayout && hasRenderableItems
    if (!canEvaluateEnd) {
        return NovelAutoScrollEndState(
            isAtEnd = false,
            stableEndFrameCount = 0,
            shouldEnterDwell = false,
            shouldAdvanceNow = false,
        )
    }

    val atEndThisFrame = !canScrollForward || scrollConsumedPx == 0f
    val stableFrames = if (atEndThisFrame) {
        (previousStableEndFrameCount + 1).coerceAtLeast(1)
    } else {
        0
    }
    val stableEnough = stableFrames >= requiredStableFrames.coerceAtLeast(1)
    return NovelAutoScrollEndState(
        isAtEnd = atEndThisFrame,
        stableEndFrameCount = stableFrames,
        shouldEnterDwell = stableEnough,
        shouldAdvanceNow = stableEnough,
    )
}

internal fun shouldAutoScrollAdvanceToNextChapter(
    behavior: NovelAutoScrollChapterEndBehavior,
    hasNextChapter: Boolean,
): Boolean {
    return hasNextChapter && behavior != NovelAutoScrollChapterEndBehavior.StopAtEnd
}

internal fun shouldAutoScrollContinueAcrossChapters(
    behavior: NovelAutoScrollChapterEndBehavior,
): Boolean {
    return behavior == NovelAutoScrollChapterEndBehavior.ContinuousReading
}

internal fun resolveAutoScrollPrefetchNeeded(
    currentIndex: Int,
    totalItems: Int,
    behavior: NovelAutoScrollChapterEndBehavior,
    thresholdPercent: Int = AUTO_SCROLL_PREFETCH_THRESHOLD_PERCENT,
): Boolean {
    if (totalItems <= 0 || currentIndex < 0) return false
    val progressPercent = (((currentIndex + 1).toFloat() / totalItems.toFloat()) * 100f)
        .roundToInt()
        .coerceIn(0, 100)
    return resolveAutoScrollPrefetchNeededByPercent(
        progressPercent = progressPercent,
        behavior = behavior,
        thresholdPercent = thresholdPercent,
    )
}

/**
 * Percent based prefetch gate. Reading progress is already tracked as a percentage, so callers no
 * longer have to fake `currentIndex` / `totalItems = 100` just to ask this question.
 */
internal fun resolveAutoScrollPrefetchNeededByPercent(
    progressPercent: Int,
    behavior: NovelAutoScrollChapterEndBehavior,
    thresholdPercent: Int = AUTO_SCROLL_PREFETCH_THRESHOLD_PERCENT,
): Boolean {
    if (behavior == NovelAutoScrollChapterEndBehavior.StopAtEnd) return false
    if (progressPercent < 0) return false
    return progressPercent.coerceIn(0, 100) >= thresholdPercent.coerceIn(1, 100)
}

internal fun resolveAutoScrollSpeedFactor(
    currentFactor: Float,
    inCooldown: Boolean,
    delta: Float,
): Float {
    val safeDelta = delta.coerceIn(0f, 1f)
    return when {
        inCooldown -> (currentFactor - safeDelta).coerceAtLeast(0f)
        currentFactor < 1f -> (currentFactor + safeDelta).coerceAtMost(1f)
        else -> 1f
    }
}

internal fun isAutoScrollHandoffExpired(
    requestedAtMs: Long,
    nowMs: Long,
    ttlMs: Long = AUTO_SCROLL_HANDOFF_TTL_MS,
): Boolean {
    return nowMs - requestedAtMs > ttlMs.coerceAtLeast(0L)
}

internal fun resolveWebViewAutoScrollNearEnd(
    totalScrollablePx: Int,
    scrollYPx: Int,
    endOffsetPx: Int,
): Boolean {
    if (totalScrollablePx <= 0) return true
    val distanceToBottomPx = (totalScrollablePx - scrollYPx).coerceAtLeast(0)
    return distanceToBottomPx <= endOffsetPx.coerceAtLeast(0)
}

/** What the reader loop should do after handing one frame to the controller. */
internal enum class NovelAutoScrollFrameResult {
    /** Nothing happened this frame: warm-up frame, damped cooldown, or sub-pixel step. */
    Skipped,

    /** The surface moved. */
    Scrolled,

    /** The surface is stably at its end, so the reader should run the end-of-chapter dwell. */
    ReachedEnd,
}

/**
 * Single owner of the auto-scroll state machine.
 *
 * Mode transitions, the speed ramp, the touch cooldown, the sub-pixel remainder and the
 * end-of-chapter stability counter all live here instead of being re-implemented per surface in the
 * reader composable. The reader only feeds frames in and reacts to [NovelAutoScrollFrameResult].
 */
internal class NovelAutoScrollController {

    var mode: NovelAutoScrollMode = NovelAutoScrollMode.Off
        private set

    var speedFactor: Float = 0f
        private set

    var stableEndFrames: Int = 0
        private set

    private var remainderPx: Float = 0f
    private var previousFrameNanos: Long? = null
    private var cooldownUntilNanos: Long = 0L

    val isEnabled: Boolean
        get() = mode != NovelAutoScrollMode.Off

    fun start() {
        mode = NovelAutoScrollMode.Running
        speedFactor = 0f
        cooldownUntilNanos = 0L
        resetFrameState()
    }

    fun stop() {
        mode = NovelAutoScrollMode.Off
        speedFactor = 0f
        resetFrameState()
    }

    /** Holds scrolling while the reader UI is visible without dropping the enabled state. */
    fun pause() {
        if (!isEnabled) return
        mode = NovelAutoScrollMode.Paused
        resetFrameState()
    }

    fun resume() {
        if (mode == NovelAutoScrollMode.Paused) {
            mode = NovelAutoScrollMode.Running
        }
    }

    fun enterDwell() {
        if (!isEnabled) return
        mode = NovelAutoScrollMode.EndDwell
        resetFrameState()
    }

    fun enterHandoff() {
        mode = NovelAutoScrollMode.Handoff
        resetFrameState()
    }

    fun noteTouch(nowNanos: Long, cooldownMs: Long) {
        cooldownUntilNanos = nowNanos + cooldownMs.coerceAtLeast(0L) * 1_000_000L
    }

    fun resetFrameState() {
        remainderPx = 0f
        previousFrameNanos = null
        stableEndFrames = 0
    }

    /**
     * Ramps the speed factor for this frame. Returns false while a touch cooldown has scrolling
     * fully damped, so the caller can idle instead of stepping the surface.
     */
    fun tickSpeedFactor(nowNanos: Long, delta: Float): Boolean {
        val inCooldown = nowNanos < cooldownUntilNanos
        speedFactor = resolveAutoScrollSpeedFactor(
            currentFactor = speedFactor,
            inCooldown = inCooldown,
            delta = delta,
        )
        when {
            inCooldown -> mode = NovelAutoScrollMode.Cooldown
            mode == NovelAutoScrollMode.Cooldown -> mode = NovelAutoScrollMode.Running
        }
        return !(inCooldown && speedFactor <= 0f)
    }

    /**
     * Pixel step for the frame that started at [frameTimeNanos], carrying the sub-pixel remainder
     * so slow speeds still advance. Returns 0 on the first frame after a reset, when no frame delta
     * is known yet.
     */
    fun frameStepPx(speed: Int, frameTimeNanos: Long): Int {
        val previousNanos = previousFrameNanos
        previousFrameNanos = frameTimeNanos
        if (previousNanos == null) return 0
        val frameDeltaNanos = (frameTimeNanos - previousNanos).coerceAtLeast(1L)
        val rawStepPx = autoScrollFrameStepPx(
            speed = speed,
            frameDeltaNanos = frameDeltaNanos,
        ) * speedFactor
        val resolvedStep = resolveAutoScrollStep(rawStepPx, remainderPx)
        remainderPx = resolvedStep.remainderPx
        return resolvedStep.stepPx
    }

    /** Folds one surface scroll result into the shared end-of-chapter stability counter. */
    fun onScrolled(
        canScrollForward: Boolean,
        scrollConsumedPx: Float,
        isContentReady: Boolean,
        hasCompletedInitialLayout: Boolean,
        hasRenderableItems: Boolean,
    ): NovelAutoScrollFrameResult {
        val endState = resolveNovelAutoScrollEndState(
            canScrollForward = canScrollForward,
            scrollConsumedPx = scrollConsumedPx,
            isContentReady = isContentReady,
            hasCompletedInitialLayout = hasCompletedInitialLayout,
            hasRenderableItems = hasRenderableItems,
            previousStableEndFrameCount = stableEndFrames,
        )
        stableEndFrames = endState.stableEndFrameCount
        return when {
            endState.shouldEnterDwell -> NovelAutoScrollFrameResult.ReachedEnd
            scrollConsumedPx > 0f -> NovelAutoScrollFrameResult.Scrolled
            else -> NovelAutoScrollFrameResult.Skipped
        }
    }
}
