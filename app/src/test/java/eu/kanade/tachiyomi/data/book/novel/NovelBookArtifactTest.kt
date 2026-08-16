package eu.kanade.tachiyomi.data.book.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookArtifactSource
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class NovelBookArtifactTest {

    private fun chapter(id: Long) = NovelBookSourceChapter(id = id, name = "Chapter $id", url = "/c$id")

    private fun body(text: String) = "<div><p>$text</p></div>"

    private fun request(chapters: List<NovelBookSourceChapter>) = NovelBookBuildRequest(
        sourceId = 7L,
        novelId = 42L,
        novelTitle = "Test novel",
        chapterSetHash = NovelBookArtifact.chapterSetHash(chapters),
        builtAt = 1_000L,
    )

    @Test
    fun `builds a contiguous book and reads chapters back by byte range`(@TempDir root: File) {
        val chapters = listOf(chapter(1), chapter(2), chapter(3))
        val result = NovelBookArtifactWriter(root).build(
            request = request(chapters),
            chapters = chapters,
            loadHtml = { chapter -> body("Body of ${chapter.id}") },
        )

        result.missingChapterIds shouldBe emptyList()
        result.meta.complete shouldBe true
        result.meta.chapterCount shouldBe 3
        val entries = result.index.chapters
        entries.map { entry -> entry.order } shouldBe listOf(0, 1, 2)
        entries[0].charStart shouldBe 0
        entries[1].charStart shouldBe entries[0].charStart + entries[0].charLength
        entries[2].charStart shouldBe entries[1].charStart + entries[1].charLength
        result.meta.totalChars shouldBe entries.sumOf { entry -> entry.charLength }
        NovelBookArtifact.exists(root) shouldBe true

        val second = entries[1]
        val sectionHtml = NovelBookArtifact.readRange(root, second.byteStart, second.byteLength)
        sectionHtml.contains("Body of 2") shouldBe true
        sectionHtml.contains("Body of 1") shouldBe false
        sectionHtml.contains("nb-ch-2") shouldBe true
    }

    @Test
    fun `empty rebuild keeps the previous artifact`(@TempDir root: File) {
        val chapters = listOf(chapter(1), chapter(2))
        val writer = NovelBookArtifactWriter(root)
        val first = writer.build(
            request = request(chapters),
            chapters = chapters,
            loadHtml = { chapter -> body("Body of ${chapter.id}") },
        )
        val bodyBefore = NovelBookArtifact.readRange(root, 0L, first.meta.totalBytes.toInt())

        val failed = writer.build(
            request = request(chapters),
            chapters = chapters,
            loadHtml = { null },
        )

        failed.index.chapters shouldBe emptyList()
        NovelBookArtifact.exists(root) shouldBe true
        NovelBookArtifact.readIndex(root) shouldBe first.index
        NovelBookArtifact.readMeta(root) shouldBe first.meta
        NovelBookArtifact.readRange(root, 0L, first.meta.totalBytes.toInt()) shouldBe bodyBefore
    }

    @Test
    fun `append keeps existing offsets and extends the book`(@TempDir root: File) {
        val initial = listOf(chapter(1), chapter(2))
        val writer = NovelBookArtifactWriter(root)
        val first = writer.build(
            request = request(initial),
            chapters = initial,
            loadHtml = { chapter -> body("Body of ${chapter.id}") },
        )

        val second = writer.append(
            request = request(initial + listOf(chapter(3))),
            existing = first.index,
            newChapters = listOf(chapter(2), chapter(3)),
            loadHtml = { chapter -> body("Body of ${chapter.id}") },
            bookVersion = 2,
        )

        second.index.chapters.size shouldBe 3
        second.index.chapters.take(2) shouldBe first.index.chapters
        second.meta.bookVersion shouldBe 2
        second.meta.totalChars shouldBe second.index.chapters.sumOf { entry -> entry.charLength }
        val third = second.index.chapters[2]
        third.charStart shouldBe first.meta.totalChars
        third.order shouldBe 2
        NovelBookArtifact.readRange(root, third.byteStart, third.byteLength).contains("Body of 3") shouldBe true
        NovelBookArtifact.readIndex(root) shouldBe second.index
        NovelBookArtifact.readMeta(root) shouldBe second.meta
    }

    @Test
    fun `missing chapter bodies are reported and mark the book incomplete`(@TempDir root: File) {
        val chapters = listOf(chapter(1), chapter(2), chapter(3))
        val result = NovelBookArtifactWriter(root).build(
            request = request(chapters),
            chapters = chapters,
            loadHtml = { chapter -> if (chapter.id == 2L) null else body("Body of ${chapter.id}") },
        )

        result.missingChapterIds shouldBe listOf(2L)
        result.meta.complete shouldBe false
        result.index.chapters.map { entry -> entry.chapterId } shouldBe listOf(1L, 3L)
        result.index.chapters[1].charStart shouldBe result.index.chapters[0].charLength
    }

    @Test
    fun `offsets map back to chapters and whole book progress`(@TempDir root: File) {
        val chapters = (1L..6L).map { id -> chapter(id) }
        val result = NovelBookArtifactWriter(root).build(
            request = request(chapters),
            chapters = chapters,
            loadHtml = { chapter -> body("Body of ${chapter.id}") },
        )

        val blocks = NovelBookBlockPlanner.plan(result.index)
        blocks.size shouldBe 1
        blocks[0].charStart shouldBe 0
        blocks[0].charEnd shouldBe result.meta.totalChars
        blocks[0].byteLength shouldBe result.meta.totalBytes.toInt()
        NovelBookBlockPlanner.blockAt(blocks, result.meta.totalChars) shouldBe blocks[0]

        val entry = result.index.chapters[4]
        NovelBookBlockPlanner.chapterAt(result.index, entry.charStart) shouldBe entry
        NovelBookBlockPlanner.chapterAt(result.index, entry.charStart + entry.charLength - 1) shouldBe entry
        NovelBookBlockPlanner.chapterById(result.index, 3L)?.chapterId shouldBe 3L
        NovelBookBlockPlanner.progressOf(result.meta, 0) shouldBe 0f
        NovelBookBlockPlanner.progressOf(result.meta, result.meta.totalChars) shouldBe 1f
    }

    @Test
    fun `section chapters resolve per block for translation safety`(@TempDir root: File) {
        // Each chapter body is ~40k chars, so a default 40k block contains exactly one chapter.
        val chapters = (1L..4L).map { id -> chapter(id) }
        val result = NovelBookArtifactWriter(root).build(
            request = request(chapters),
            chapters = chapters,
            loadHtml = { chapter -> body("Chapter ${chapter.id} " + "text ".repeat(8_000)) },
        )

        val source = NovelBookArtifactSource.open(root)
            ?: throw AssertionError("artifact should open")
        val blocks = source.blocks

        blocks.forEachIndexed { index, block ->
            val chaptersOfSection = source.chaptersOfSection(index)
            chaptersOfSection.size shouldBe 1
            chaptersOfSection.first() shouldBe block.firstChapterId
        }
    }

    @Test
    fun `blocks split on chapter boundaries once the target size is exceeded`(@TempDir root: File) {
        val chapters = (1L..6L).map { id -> chapter(id) }
        val longBody = body("a".repeat(9_000))
        val result = NovelBookArtifactWriter(root).build(
            request = request(chapters),
            chapters = chapters,
            loadHtml = { longBody },
        )

        val blocks = NovelBookBlockPlanner.plan(result.index, NovelBookBlockPlanner.MIN_TARGET_CHARS)
        blocks.size shouldBe 3
        blocks[0].firstChapterId shouldBe 1L
        blocks[0].lastChapterId shouldBe 2L
        blocks[0].charStart shouldBe 0
        blocks.last().charEnd shouldBe result.meta.totalChars
        blocks.zipWithNext().all { pair -> pair.first.charEnd == pair.second.charStart } shouldBe true
        blocks.map { block -> block.index } shouldBe listOf(0, 1, 2)
    }

    @Test
    fun `chapter set hash changes when the chapter list changes`() {
        val base = listOf(chapter(1), chapter(2))
        NovelBookArtifact.chapterSetHash(base) shouldBe NovelBookArtifact.chapterSetHash(base)

        val added = listOf(chapter(1), chapter(2), chapter(3))
        (NovelBookArtifact.chapterSetHash(base) == NovelBookArtifact.chapterSetHash(added)) shouldBe false

        val renamed = listOf(chapter(1), NovelBookSourceChapter(id = 2L, name = "Chapter 2 v2", url = "/c2"))
        (NovelBookArtifact.chapterSetHash(base) == NovelBookArtifact.chapterSetHash(renamed)) shouldBe false
    }

    /**
     * Load shape of a long running novel: a thousand chapters of roughly fifteen thousand
     * characters each, which is the ~15 MB book size this feature has to stay usable at. The
     * assertions guard the invariants that make random access work at that size, and the timings
     * are deliberately loose so the test fails only on an algorithmic regression, not on a slow
     * machine.
     */
    @Test
    fun `handles a thousand chapter book with contiguous offsets and cheap random reads`(@TempDir root: File) {
        val chapters = (1L..1_000L).map { id -> chapter(id) }
        val filler = "a".repeat(15_000)
        val writer = NovelBookArtifactWriter(root)

        val result: NovelBookBuildResult
        val buildMs = measureTimeMillis {
            result = writer.build(
                request = request(chapters),
                chapters = chapters,
                loadHtml = { chapter -> body("MARK-${chapter.id}-END $filler") },
            )
        }

        result.missingChapterIds shouldBe emptyList()
        result.meta.complete shouldBe true
        result.meta.chapterCount shouldBe 1_000
        result.meta.totalChars shouldBe result.index.chapters.sumOf { entry -> entry.charLength }
        (result.meta.totalChars > 10_000_000) shouldBe true
        (result.meta.totalBytes == NovelBookArtifact.bodyFile(root).length()) shouldBe true

        // No gaps and no overlaps: every saved reading position maps to exactly one chapter.
        val entries = result.index.chapters
        entries.zipWithNext().all { (left, right) ->
            left.charStart + left.charLength == right.charStart &&
                left.byteStart + left.byteLength == right.byteStart
        } shouldBe true

        val blocks: List<NovelBookBlock>
        val planMs = measureTimeMillis { blocks = NovelBookBlockPlanner.plan(result.index) }
        blocks.first().charStart shouldBe 0
        blocks.last().charEnd shouldBe result.meta.totalChars
        blocks.zipWithNext().all { (left, right) -> left.charEnd == right.charStart } shouldBe true
        // Blocks may only start where a chapter starts, otherwise a block would render half a
        // paragraph and the chapter heading would drift away from its text.
        val chapterStarts = entries.map { entry -> entry.charStart }.toSet()
        blocks.all { block -> block.charStart in chapterStarts } shouldBe true

        val random = Random(20_260_729)
        val probes = List(200) { random.nextInt(entries.size) }
        val readMs = measureTimeMillis {
            probes.forEach { position ->
                val entry = entries[position]
                val html = NovelBookArtifact.readRange(root, entry.byteStart, entry.byteLength)
                html.contains("MARK-${entry.chapterId}-END") shouldBe true
                html.contains("MARK-${entry.chapterId + 1}-END") shouldBe false
            }
        }

        // Offsets must survive an append, or every position saved before the update would shift.
        val newChapters = (1_001L..1_050L).map { id -> chapter(id) }
        val appended = writer.append(
            request = request(chapters + newChapters),
            existing = result.index,
            newChapters = newChapters,
            loadHtml = { chapter -> body("MARK-${chapter.id}-END $filler") },
            bookVersion = 2,
        )
        appended.index.chapters.size shouldBe 1_050
        appended.index.chapters.take(1_000) shouldBe entries
        appended.index.chapters[1_000].charStart shouldBe result.meta.totalChars

        val untouched = entries[500]
        NovelBookArtifact.readRange(root, untouched.byteStart, untouched.byteLength)
            .contains("MARK-${untouched.chapterId}-END") shouldBe true

        (buildMs < 120_000) shouldBe true
        (planMs < 5_000) shouldBe true
        (readMs < 15_000) shouldBe true
    }
}
