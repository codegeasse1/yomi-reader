package eu.kanade.presentation.reader.novel

internal object NovelReaderSelectionGestureArbiter {

    fun shouldPromoteSelectionCandidate(
        elapsedMillis: Long,
        movedDistancePx: Float,
        touchSlopPx: Float,
        longPressTimeoutMillis: Long,
    ): Boolean {
        if (elapsedMillis < longPressTimeoutMillis) return false
        return movedDistancePx <= touchSlopPx
    }

    fun shouldHandlePlainTap(
        elapsedMillis: Long,
        movedDistancePx: Float,
        touchSlopPx: Float,
        longPressTimeoutMillis: Long,
    ): Boolean {
        if (elapsedMillis >= longPressTimeoutMillis) return false
        return movedDistancePx <= touchSlopPx
    }
}
