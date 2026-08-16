package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact
import eu.kanade.tachiyomi.data.book.novel.NovelBookBlock
import eu.kanade.tachiyomi.data.book.novel.NovelBookBlockPlanner
import eu.kanade.tachiyomi.data.book.novel.NovelBookChapterEntry
import eu.kanade.tachiyomi.data.book.novel.NovelBookIndex
import eu.kanade.tachiyomi.data.book.novel.NovelBookMeta
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlock
import eu.kanade.tachiyomi.ui.reader.novel.replace.applyReplaceRulesToHtml
import java.io.File

/**
 * Reader-side view over a compiled book artifact.
 *
 * The book is read as fixed-size blocks of the continuous body instead of one document per chapter,
 * which is what removes the half-empty page at every chapter boundary: a block simply continues with
 * the next chapter heading right after the previous paragraph.
 *
 * Block lengths come from the artifact index, so every section is measured from the start and
 * whole-book progress is exact instead of being rescaled while chapters are measured.
 */
class NovelBookArtifactSource(
    val directory: File,
    val index: NovelBookIndex,
    val meta: NovelBookMeta,
    val blocks: List<NovelBookBlock>,
    /** Applies user text-replacement rules to block markup at render time. */
    private val replaceTextHtml: (String) -> String = { it },
) {

    /**
     * Spine of blocks. Section indices are block indices, not chapter indices.
     *
     * Sections carry the real chapter ids they cover. The synthetic key that used to sit in
     * [NovelBookSection.chapterId] is gone: it silently broke every `spine.indexOf(chapterId)`
     * lookup, which is why TTS follow-along and translations never resolved a section over a
     * compiled book.
     */
    val spine: NovelBookSpine = NovelBookSpine(
        blocks.map { block ->
            val blockChapterIds = chaptersOverlapping(block.charStart, block.charStart + block.charLength)
            NovelBookSection(
                chapterId = blockChapterIds.firstOrNull() ?: block.firstChapterId,
                index = block.index,
                name = NovelBookBlockPlanner.chapterById(index, block.firstChapterId)?.title.orEmpty(),
                charCount = block.charLength.coerceAtLeast(1),
                isMeasured = true,
                chapterIds = blockChapterIds,
            )
        },
    )

    private fun chaptersOverlapping(startChar: Int, endChar: Int): List<Long> = index.chapters
        .asSequence()
        .filter { chapter -> chapter.charStart < endChar && chapter.charStart + chapter.charLength > startChar }
        .map { it.chapterId }
        .toList()

    val totalChars: Int get() = meta.totalChars

    /** Reads the body slice of a block as the renderer document for that section. */
    fun documentFor(sectionIndex: Int): NovelBookDocument? {
        val block = blocks.getOrNull(sectionIndex) ?: return null
        val html = NovelBookArtifact.readRange(
            directory = directory,
            byteStart = block.byteStart,
            byteLength = block.byteLength,
        )
        if (html.isEmpty()) return null
        return NovelBookDocument(
            sectionIndex = block.index,
            chapterId = block.firstChapterId,
            html = replaceTextHtml(html),
        )
    }

    /**
     * True when this book carries pre-compiled native blocks, so the native renderer can skip the
     * HTML parse at scroll time.
     */
    val hasNativeBlocks: Boolean = NovelBookArtifact.hasNativeStream(directory, meta) &&
        index.chapters.all { it.nativeByteLength > 0 }

    /**
     * Pre-compiled blocks of a section, or null when the book has no native stream yet and the
     * caller has to fall back to parsing [documentFor].
     *
     * A section is a window of the continuous book and can span several chapters, so the native
     * ranges of every chapter it touches are read and then clipped to the section's own character
     * range. That clipping uses the same offsets as the HTML path, which is why progress, the table
     * of contents and read marking behave identically in both renderers.
     */
    fun nativeBlocksFor(sectionIndex: Int): List<NovelBookNativeBlock>? =
        anchoredNativeBlocksFor(sectionIndex)?.map { it.block }

    /**
     * The same blocks, each carrying the index it has inside its own chapter.
     *
     * The numbering is done over the whole decoded chapter, before the section clip, so a chapter
     * that straddles a section boundary still numbers its blocks from its own beginning. That is
     * the address the book DOM carries as `data-an-b` and the one TTS speaks by, which is what lets
     * follow-along find a paragraph of a compiled book instead of guessing from its position in the
     * rendered window.
     */
    fun anchoredNativeBlocksFor(sectionIndex: Int): List<NovelBookAnchoredNativeBlock>? {
        if (!hasNativeBlocks) return null
        val block = blocks.getOrNull(sectionIndex) ?: return null
        val sectionStart = block.charStart
        val sectionEnd = block.charStart + block.charLength
        val chapters = index.chapters.filter { chapter ->
            chapter.charStart < sectionEnd && chapter.charStart + chapter.charLength > sectionStart
        }
        if (chapters.isEmpty()) return null

        // Chapters are contiguous in the native stream, so overlapping chapters collapse into a
        // single sequential read instead of one read per chapter.
        val nativeStart = chapters.first().nativeByteStart
        val nativeLength = chapters.sumOf { it.nativeByteLength }
        val decoded = NovelBookArtifact.readNativeRange(
            directory = directory,
            byteStart = nativeStart,
            byteLength = nativeLength,
        )
        if (decoded.isEmpty()) return null
        return decoded
            .withChapterBlockIndices()
            .filter { anchored ->
                val start = anchored.block.charStart
                start < sectionEnd && start + anchored.block.charLength > sectionStart
            }
    }

    /** Whole-book character offset of a block location, used as the persisted reading position. */
    fun charOffsetOf(location: NovelBookLocation): Int {
        val block = blocks.getOrNull(location.sectionIndex) ?: return 0
        val maxOffset = (block.charLength - 1).coerceAtLeast(0)
        return block.charStart + location.charOffset.coerceIn(0, maxOffset)
    }

    /** Block location of a whole-book character offset. */
    fun locationOf(charOffset: Int): NovelBookLocation {
        val block = NovelBookBlockPlanner.blockAt(blocks, charOffset) ?: return NovelBookLocation.START
        return NovelBookLocation(
            sectionIndex = block.index,
            charOffset = (charOffset - block.charStart).coerceAtLeast(0),
        )
    }

    /** Opens the book exactly at the first character of a chapter, for table-of-contents taps. */
    fun locationOfChapter(chapterId: Long): NovelBookLocation? {
        val chapter = NovelBookBlockPlanner.chapterById(index, chapterId) ?: return null
        return locationOf(chapter.charStart)
    }

    /** Chapter that owns a whole-book character offset, for the reader title and read marking. */
    fun chapterAt(charOffset: Int): NovelBookChapterEntry? =
        NovelBookBlockPlanner.chapterAt(index, charOffset)

    // ---------------------------------------------------------------------------------------------
    // Locators. BookLocator is the single position type of book mode; everything below converts
    // between it and the two shapes the renderer and the database still speak: a block location and
    // a whole-book character offset.
    // ---------------------------------------------------------------------------------------------

    /** Locator of a whole-book character offset. */
    fun locatorOf(charOffset: Int): BookLocator {
        val clamped = charOffset.coerceIn(0, (totalChars - 1).coerceAtLeast(0))
        val chapter = chapterAt(clamped)
        val block = NovelBookBlockPlanner.blockAt(blocks, clamped)
        return BookLocator(
            chapterId = chapter?.chapterId ?: BookLocator.NO_CHAPTER_ID,
            blockIndex = block?.index ?: 0,
            charOffset = (clamped - (chapter?.charStart ?: 0)).coerceAtLeast(0),
        )
    }

    /**
     * Whole-book character offset of a locator.
     *
     * The chapter is the anchor and the offset is resolved inside it, so a position stored before
     * the book was rebuilt still lands on the same paragraph as long as the chapter still exists.
     * [BookLocator.blockIndex] is never trusted here; it is only a lookup hint.
     */
    fun globalCharOffsetOf(locator: BookLocator): Int {
        val chapter = NovelBookBlockPlanner.chapterById(index, locator.chapterId)
            ?: return blocks.getOrNull(locator.blockIndex)?.charStart ?: 0
        val maxInside = (chapter.charLength - 1).coerceAtLeast(0)
        return chapter.charStart + locator.charOffset.coerceIn(0, maxInside)
    }

    /** Renderer position of a locator. */
    fun locationOf(locator: BookLocator): NovelBookLocation = locationOf(globalCharOffsetOf(locator))

    /** Locator of a renderer position. */
    fun locatorOf(location: NovelBookLocation): BookLocator = locatorOf(charOffsetOf(location))

    /** Whole-book progress of a locator in `0f..1f`. */
    fun progressOf(locator: BookLocator): Float = progressOf(globalCharOffsetOf(locator))

    /** Locator at the first character of a chapter, for table-of-contents taps. */
    fun locatorOfChapter(chapterId: Long): BookLocator? {
        val chapter = NovelBookBlockPlanner.chapterById(index, chapterId) ?: return null
        return BookLocator(
            chapterId = chapter.chapterId,
            blockIndex = NovelBookBlockPlanner.blockAt(blocks, chapter.charStart)?.index ?: 0,
            charOffset = 0,
        )
    }

    /**
     * Chapters whose text overlaps the section at [sectionIndex].
     *
     * Compiled blocks are windows over the continuous body and usually cover exactly one chapter;
     * blocks that straddle a chapter boundary cover two. Translation mapping is indexed per
     * chapter, so only single-chapter sections can be translated safely.
     */
    fun chaptersOfSection(sectionIndex: Int): List<Long> {
        val block = blocks.getOrNull(sectionIndex) ?: return emptyList()
        return chaptersOverlapping(block.charStart, block.charStart + block.charLength)
    }

    /**
     * Chapters whose text ends before [charOffset], i.e. the ones the reader scrolled completely
     * past. In book mode nothing is ever "closed", so this offset comparison replaces the
     * per-chapter read threshold; the chapter under the caret is intentionally excluded because it
     * is still being read.
     */
    fun chaptersFullyReadBefore(charOffset: Int): List<Long> = index.chapters
        .asSequence()
        .filter { it.charStart + it.charLength <= charOffset }
        .map { it.chapterId }
        .toList()

    /**
     * Chapters that were completely passed inside one reading session.
     *
     * Read marking used to look at everything before the caret, so re-opening a book always
     * re-marked every chapter before the stored position - which is why unmarking a chapter on the
     * novel screen looked like it did nothing: the next book session wrote it back. Only chapters
     * that start at or after where the session began are considered now, so the reader reports what
     * it actually read and manual read states survive.
     */
    fun chaptersFullyReadBetween(fromCharOffset: Int, toCharOffset: Int): List<Long> = index.chapters
        .asSequence()
        .filter { it.charStart >= fromCharOffset }
        .filter { it.charStart + it.charLength <= toCharOffset }
        .map { it.chapterId }
        .toList()

    /** First character of the chapter that owns [charOffset], used as a session start anchor. */
    fun chapterStartAt(charOffset: Int): Int = chapterAt(charOffset)?.charStart ?: charOffset

    fun progressOf(charOffset: Int): Float = NovelBookBlockPlanner.progressOf(meta, charOffset)

    companion object {
        /**
         * Opens the artifact of a novel, or returns null when no book was compiled for it yet or the
         * artifact belongs to a different source than the one the novel currently uses.
         */
        fun open(
            directory: File,
            targetChars: Int = NovelBookBlockPlanner.DEFAULT_TARGET_CHARS,
            replaceTextHtml: (String) -> String = { it },
        ): NovelBookArtifactSource? {
            if (!NovelBookArtifact.exists(directory)) return null
            val index = NovelBookArtifact.readIndex(directory) ?: return null
            val meta = NovelBookArtifact.readMeta(directory) ?: return null
            if (index.chapters.isEmpty()) return null
            val blocks = NovelBookBlockPlanner.plan(index, targetChars)
            if (blocks.isEmpty()) return null
            return NovelBookArtifactSource(
                directory = directory,
                index = index,
                meta = meta,
                blocks = blocks,
                replaceTextHtml = replaceTextHtml,
            )
        }
    }
}
