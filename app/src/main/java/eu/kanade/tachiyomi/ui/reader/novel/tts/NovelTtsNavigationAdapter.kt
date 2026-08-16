package eu.kanade.tachiyomi.ui.reader.novel.tts

data class NovelTtsNavigationAnchor(
    val pageIndex: Int? = null,
    val blockIndex: Int? = null,
    val scrollOffsetPx: Int = 0,
    val webProgressPercent: Int? = null,
    /**
     * Chapter the [blockIndex] belongs to, when the surface addresses blocks per chapter.
     *
     * Book mode renders several chapters into one document, so a block index alone cannot name a
     * position there; the pair can.
     */
    val chapterId: Long? = null,
)

interface NovelTtsNavigationAdapter {
    suspend fun syncToSegment(segment: NovelTtsSegment)

    fun captureManualAnchor(
        pageIndex: Int? = null,
        blockIndex: Int? = null,
        scrollOffsetPx: Int = 0,
    ): NovelTtsNavigationAnchor

    suspend fun restorePosition(anchor: NovelTtsNavigationAnchor)
}
