package eu.kanade.tachiyomi.data.book.novel

/**
 * One render window of the compiled book. Blocks are aligned to chapter boundaries so a chapter is
 * never cut in half, and they are appended into the same document while reading, so a block boundary
 * is not a page boundary and never leaves a half empty page.
 */
data class NovelBookBlock(
    val index: Int,
    val charStart: Int,
    val charEnd: Int,
    val byteStart: Long,
    val byteLength: Int,
    val firstChapterId: Long,
    val lastChapterId: Long,
) {
    val charLength: Int get() = charEnd - charStart
}

object NovelBookBlockPlanner {

    /**
     * Characters per render window.
     *
     * A window used to be worth 200k characters because it was a single HTML string handed to a
     * WebView, where the cost of a bigger window was close to zero. The native renderer keeps a
     * window as live block objects instead, so the same 200k characters cost tens of thousands of
     * allocations that stay in memory. Smaller windows are appended more often, which is cheap
     * with pre-compiled blocks and keeps chapter boundaries intact all the same.
     */
    const val DEFAULT_TARGET_CHARS = 40_000
    const val MIN_TARGET_CHARS = 20_000

    /** Groups consecutive chapters into windows of roughly [targetChars] characters. */
    fun plan(index: NovelBookIndex, targetChars: Int = DEFAULT_TARGET_CHARS): List<NovelBookBlock> {
        val chapters = index.chapters
        if (chapters.isEmpty()) return emptyList()
        val target = targetChars.coerceAtLeast(MIN_TARGET_CHARS)
        val blocks = mutableListOf<NovelBookBlock>()
        var start = 0
        while (start < chapters.size) {
            var end = start
            var chars = 0
            while (end < chapters.size && (end == start || chars + chapters[end].charLength <= target)) {
                chars += chapters[end].charLength
                end += 1
            }
            val first = chapters[start]
            val last = chapters[end - 1]
            blocks.add(
                NovelBookBlock(
                    index = blocks.size,
                    charStart = first.charStart,
                    charEnd = last.charStart + last.charLength,
                    byteStart = first.byteStart,
                    byteLength = (last.byteStart + last.byteLength - first.byteStart).toInt(),
                    firstChapterId = first.chapterId,
                    lastChapterId = last.chapterId,
                ),
            )
            start = end
        }
        return blocks
    }

    fun blockAt(blocks: List<NovelBookBlock>, charOffset: Int): NovelBookBlock? {
        if (blocks.isEmpty()) return null
        val clamped = charOffset.coerceAtLeast(0)
        val match = blocks.lastOrNull { block -> block.charStart <= clamped }
        return match ?: blocks[0]
    }

    fun chapterAt(index: NovelBookIndex, charOffset: Int): NovelBookChapterEntry? {
        val chapters = index.chapters
        if (chapters.isEmpty()) return null
        val clamped = charOffset.coerceAtLeast(0)
        val match = chapters.lastOrNull { chapter -> chapter.charStart <= clamped }
        return match ?: chapters[0]
    }

    fun chapterById(index: NovelBookIndex, chapterId: Long): NovelBookChapterEntry? =
        index.chapters.firstOrNull { chapter -> chapter.chapterId == chapterId }

    /** Exact whole book progress, which is the point of merging the chapters in the first place. */
    fun progressOf(meta: NovelBookMeta, charOffset: Int): Float {
        if (meta.totalChars <= 0) return 0f
        return (charOffset.toFloat() / meta.totalChars.toFloat()).coerceIn(0f, 1f)
    }
}
