package eu.kanade.presentation.reader.novel

import android.view.KeyEvent

/** Outcome of a volume-key press in the reader. */
internal enum class VolumeKeyAction {
    /** The key is not handled: let the system see it (system volume UI may appear). */
    NONE,

    /** The key is consumed but produces no reader action (e.g. ACTION_DOWN, or UI is visible). */
    CONSUME,

    /** Move backwards in the reader (VOLUME_UP). */
    BACKWARD,

    /** Move forwards in the reader (VOLUME_DOWN). */
    FORWARD,
}

/**
 * Resolves what a volume-key event should do in the reader.
 *
 * The four outcomes matter: collapsing "consumed, no action" into NONE would let the event fall
 * through to the system and pop the volume slider on every ACTION_DOWN and whenever the reader UI
 * is visible.
 *
 * Takes primitives instead of [KeyEvent] so the decision is unit-testable on the JVM; the
 * composable extracts them from the event before calling.
 *
 * [showReaderUi] is read at call time (the caller passes `latestShowReaderUi` via a lambda), not a
 * snapshot from composition.
 */
internal fun resolveVolumeKeyAction(
    keyCode: Int,
    action: Int,
    useVolumeButtons: Boolean,
    showReaderUi: () -> Boolean,
): VolumeKeyAction {
    if (!useVolumeButtons) return VolumeKeyAction.NONE
    if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
        return VolumeKeyAction.NONE
    }
    if (action == KeyEvent.ACTION_DOWN) return VolumeKeyAction.CONSUME
    if (action != KeyEvent.ACTION_UP) return VolumeKeyAction.NONE
    if (showReaderUi()) return VolumeKeyAction.CONSUME
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> VolumeKeyAction.BACKWARD
        KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKeyAction.FORWARD
        else -> VolumeKeyAction.NONE
    }
}
