package eu.kanade.tachiyomi.ui.reader.novel.setting

/**
 * Actions assignable to the nine short-tap zones of the novel reader.
 *
 * Zones form a 3x3 grid indexed row by row: 0..2 top, 3..5 middle, 6..8 bottom.
 */
enum class NovelReaderTapZoneAction {
    NONE,
    TOGGLE_UI,
    FORWARD,
    BACKWARD,
    NEXT_CHAPTER,
    PREV_CHAPTER,
}

const val NOVEL_READER_TAP_ZONE_COUNT = 9

val NovelReaderDefaultTapZoneActions: List<NovelReaderTapZoneAction> = listOf(
    NovelReaderTapZoneAction.BACKWARD,
    NovelReaderTapZoneAction.BACKWARD,
    NovelReaderTapZoneAction.FORWARD,
    NovelReaderTapZoneAction.BACKWARD,
    NovelReaderTapZoneAction.TOGGLE_UI,
    NovelReaderTapZoneAction.FORWARD,
    NovelReaderTapZoneAction.BACKWARD,
    NovelReaderTapZoneAction.FORWARD,
    NovelReaderTapZoneAction.FORWARD,
)

fun serializeNovelReaderTapZoneActions(actions: List<NovelReaderTapZoneAction>): String {
    return actions.joinToString(separator = ",") { it.name }
}

val NOVEL_READER_DEFAULT_TAP_ZONE_ACTIONS: String =
    serializeNovelReaderTapZoneActions(NovelReaderDefaultTapZoneActions)

fun parseNovelReaderTapZoneActions(raw: String): List<NovelReaderTapZoneAction> {
    val tokens = raw.split(",").map { it.trim() }
    if (tokens.size != NOVEL_READER_TAP_ZONE_COUNT) return NovelReaderDefaultTapZoneActions
    return tokens.map { token ->
        NovelReaderTapZoneAction.entries.firstOrNull { it.name == token }
            ?: return NovelReaderDefaultTapZoneActions
    }
}

fun resolveNovelReaderTapZoneIndex(
    tapX: Float,
    tapY: Float,
    width: Float,
    height: Float,
): Int {
    val safeWidth = width.coerceAtLeast(1f)
    val safeHeight = height.coerceAtLeast(1f)
    val clampedX = tapX.coerceIn(0f, safeWidth)
    val clampedY = tapY.coerceIn(0f, safeHeight)
    val col = ((clampedX / safeWidth) * 3f).toInt().coerceIn(0, 2)
    val row = ((clampedY / safeHeight) * 3f).toInt().coerceIn(0, 2)
    return row * 3 + col
}

fun cycleNovelReaderTapZoneAction(action: NovelReaderTapZoneAction): NovelReaderTapZoneAction {
    val entries = NovelReaderTapZoneAction.entries
    return entries[(action.ordinal + 1) % entries.size]
}

/**
 * Resolves the action for a short tap.
 *
 * When [customTapZonesEnabled] is true, the 3x3 [tapZoneActions] grid is used.
 * Otherwise the legacy horizontal 30/40/30 behavior applies: the center always
 * toggles the UI, and the side bands page backward/forward when
 * [tapToScrollEnabled] is on.
 */
fun resolveConfiguredNovelReaderTapAction(
    tapX: Float,
    tapY: Float,
    width: Float,
    height: Float,
    customTapZonesEnabled: Boolean,
    tapZoneActions: List<NovelReaderTapZoneAction>,
    tapToScrollEnabled: Boolean,
): NovelReaderTapZoneAction {
    if (customTapZonesEnabled) {
        val index = resolveNovelReaderTapZoneIndex(tapX, tapY, width, height)
        return tapZoneActions.getOrElse(index) { NovelReaderTapZoneAction.TOGGLE_UI }
    }
    val safeWidth = width.coerceAtLeast(1f)
    val leftBoundary = safeWidth * 0.3f
    val rightBoundary = safeWidth * 0.7f
    val clampedTapX = tapX.coerceIn(0f, safeWidth)
    val inCenter = clampedTapX > leftBoundary && clampedTapX < rightBoundary
    if (inCenter || !tapToScrollEnabled) return NovelReaderTapZoneAction.TOGGLE_UI
    return if (clampedTapX <= leftBoundary) {
        NovelReaderTapZoneAction.BACKWARD
    } else {
        NovelReaderTapZoneAction.FORWARD
    }
}
