package tachiyomi.domain.book.novel.model

/**
 * Persisted state of the compiled book artifact of a single novel.
 *
 * The artifact itself (body, index, meta) lives on disk; this row only keeps what the library and
 * the reader need without touching the filesystem: whether the title is currently read as a book,
 * which build the reading position belongs to, and the position itself as a global character
 * offset over the whole book.
 */
data class NovelBookState(
    val novelId: Long,
    val enabled: Boolean,
    val bookVersion: Long,
    val sourceId: Long,
    val chapterSetHash: String,
    val totalChars: Long,
    val chapterCount: Int,
    val charOffset: Long,
    val lastChapterId: Long?,
    val complete: Boolean,
    val builtAt: Long,
    val updatedAt: Long,
    /**
     * Artifact block that held the position when it was stored. Lookup hint only: it is recomputed
     * from [lastChapterId] plus [chapterCharOffset] whenever the book is opened, so a rebuilt
     * artifact cannot move the reader.
     */
    val blockIndex: Int = 0,
    /** Character offset inside [lastChapterId]; the part of the position that survives a rebuild. */
    val chapterCharOffset: Int = 0,
    /**
     * Whether the stored position is already expressed as a locator.
     *
     * Rows written by versions that only knew the whole-book [charOffset] (or that kept the position
     * encoded in the chapter row) are converted once, the first time the book is opened; this flag
     * records that the conversion ran so it never repeats and never overwrites a newer position.
     */
    val progressMigrated: Boolean = false,
) {

    /** Whole book progress in the 0f..1f range. */
    val progress: Float
        get() = if (totalChars <= 0L) 0f else (charOffset.toFloat() / totalChars.toFloat()).coerceIn(0f, 1f)

    /** True when the artifact was built from a different chapter set than [hash]. */
    fun isStale(hash: String): Boolean = chapterSetHash != hash

    companion object {
        fun create(novelId: Long, sourceId: Long): NovelBookState = NovelBookState(
            novelId = novelId,
            enabled = false,
            bookVersion = 0L,
            sourceId = sourceId,
            chapterSetHash = "",
            totalChars = 0L,
            chapterCount = 0,
            charOffset = 0L,
            lastChapterId = null,
            complete = false,
            builtAt = 0L,
            updatedAt = 0L,
            blockIndex = 0,
            chapterCharOffset = 0,
            // A row created by the current version starts out in the locator format.
            progressMigrated = true,
        )
    }
}
