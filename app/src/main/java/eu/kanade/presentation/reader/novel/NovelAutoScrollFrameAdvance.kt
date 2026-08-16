package eu.kanade.presentation.reader.novel

/**
 * What auto-scroll should do with one animation frame.
 *
 * The book, WebView and native list branches of the auto-scroll loop each repeated the same four
 * steps by hand: turn the frame delta into pixels, add the sub-pixel remainder carried over from the
 * previous frame, split the total into whole pixels plus a new remainder, and skip the frame when
 * nothing whole came out of it. Dropping the remainder or skipping the wrong frame is what made slow
 * speeds either stall completely or move in visible jumps, so the arithmetic lives here once and is
 * testable without Compose.
 */
internal data class NovelAutoScrollFrameAdvance(
    /** Whole pixels to scroll this frame. */
    val stepPx: Int,
    /** Sub-pixel leftover to carry into the next frame. */
    val remainderPx: Float,
    /**
     * True when this frame produced no whole pixel.
     *
     * The caller must keep the accumulated [remainderPx] and try again on the next frame instead of
     * treating the missing movement as the end of the document.
     */
    val shouldSkipFrame: Boolean,
)

/**
 * Resolves one frame of continuous auto-scroll.
 *
 * @param speed user-facing auto-scroll speed, 1..100.
 * @param speedFactor cooldown ramp, 0f while auto-scroll is fully suppressed after a touch and 1f
 * at full speed.
 * @param frameDeltaNanos time since the previous frame, so the distance stays the same whatever the
 * refresh rate is.
 * @param previousRemainderPx sub-pixel leftover from the previous frame.
 */
internal fun resolveAutoScrollFrameAdvance(
    speed: Int,
    speedFactor: Float,
    frameDeltaNanos: Long,
    previousRemainderPx: Float,
): NovelAutoScrollFrameAdvance {
    val frameStepPx = autoScrollFrameStepPx(
        speed = speed,
        frameDeltaNanos = frameDeltaNanos,
    ) * speedFactor.coerceIn(0f, 1f)
    val step = resolveAutoScrollStep(frameStepPx, previousRemainderPx)
    return NovelAutoScrollFrameAdvance(
        stepPx = step.stepPx,
        remainderPx = step.remainderPx,
        shouldSkipFrame = step.stepPx == 0,
    )
}
