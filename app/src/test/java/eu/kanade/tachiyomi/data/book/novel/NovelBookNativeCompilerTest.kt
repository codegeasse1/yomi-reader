package eu.kanade.tachiyomi.data.book.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class NovelBookNativeCompilerTest {

    private fun normalized(startOffset: Int = 0): NovelBookNormalizedSection =
        NovelBookChapterNormalizer.normalize(
            rawHtml = """
                <p style="text-align:center">First <b>bold</b> line</p>
                <p>Second line with <a href="https://example.org">a link</a></p>
                <hr>
                <p><img src="https://cdn.example.org/1.jpg" alt="art"></p>
            """.trimIndent(),
            chapterId = 7L,
            chapterName = "Chapter 1",
            startOffset = startOffset,
        )

    @Test
    fun `compiled blocks reuse the offsets stored in the normalized html`() {
        val section = normalized(startOffset = 1_000)
        val blocks = NovelBookNativeCompiler.compileSection(section.html, chapterId = 7L)

        // The chapter heading is a block like any other: dropping it would shift every offset after
        // it and invalidate saved reading positions.
        blocks.first().isChapterHeading shouldBe true
        blocks.first().charStart shouldBe 1_000
        blocks.all { it.chapterId == 7L } shouldBe true

        // Offsets are contiguous and cover exactly the chapter's character range.
        blocks.zipWithNext().forEach { (previous, next) ->
            next.charStart shouldBe previous.charStart + previous.charLength
        }
        val last = blocks.last()
        last.charStart + last.charLength shouldBe 1_000 + section.charCount
    }

    @Test
    fun `block kinds, styling and images survive compilation`() {
        val blocks = NovelBookNativeCompiler.compileSection(
            sectionHtml = normalized().html,
            chapterId = 7L,
            imageReferer = "https://example.org/",
        )

        blocks.map { it.kind } shouldBe listOf(
            NovelBookNativeBlockKind.HEADING,
            NovelBookNativeBlockKind.PARAGRAPH,
            NovelBookNativeBlockKind.PARAGRAPH,
            NovelBookNativeBlockKind.RULE,
            NovelBookNativeBlockKind.IMAGE,
        )
        blocks[1].align shouldBe NovelBookNativeAlign.CENTER
        blocks[1].segments.any { it.b && it.t == "bold" } shouldBe true
        blocks[2].segments.any { it.href == "https://example.org" } shouldBe true
        blocks[4].imageUrl shouldBe "https://cdn.example.org/1.jpg"
        blocks[4].imageAlt shouldBe "art"
        blocks[4].referer shouldBe "https://example.org/"
    }

    @Test
    fun `codec round trips a block through one jsonl line`() {
        val block = NovelBookNativeCompiler.compileSection(normalized().html, chapterId = 7L)[1]
        val line = NovelBookNativeCodec.encodeLine(block)

        line.contains('\n') shouldBe false
        NovelBookNativeCodec.decodeLine(line) shouldBe block
        NovelBookNativeCodec.decodeChunk(NovelBookNativeCodec.encodeLines(listOf(block, block))).size shouldBe 2
        NovelBookNativeCodec.decodeLine("{not json") shouldBe null
    }

    @Test
    fun `building a book writes a native stream that matches the html body`(@TempDir root: File) {
        val directory = NovelBookArtifact.directoryFor(root, sourceId = 1L, novelId = 2L)
        val chapters = listOf(
            NovelBookSourceChapter(id = 1L, name = "Chapter 1", url = "/1"),
            NovelBookSourceChapter(id = 2L, name = "Chapter 2", url = "/2"),
        )
        val result = NovelBookArtifactWriter(directory).build(
            request = NovelBookBuildRequest(
                sourceId = 1L,
                novelId = 2L,
                novelTitle = "Novel",
                chapterSetHash = NovelBookArtifact.chapterSetHash(chapters),
                builtAt = 0L,
            ),
            chapters = chapters,
            loadHtml = { chapter -> "<p>Body of ${chapter.name}</p><p>Second paragraph</p>" },
        )

        result.meta.nativeFormatVersion shouldBe NovelBookNativeCodec.FORMAT_VERSION
        result.meta.nativeComplete shouldBe true
        NovelBookArtifact.hasNativeStream(directory, result.meta) shouldBe true

        result.index.chapters.forEach { entry ->
            entry.nativeByteLength shouldNotBe 0
            val blocks = NovelBookArtifact.readNativeRange(
                directory = directory,
                byteStart = entry.nativeByteStart,
                byteLength = entry.nativeByteLength,
            )
            blocks.first().charStart shouldBe entry.charStart
            val last = blocks.last()
            last.charStart + last.charLength shouldBe entry.charStart + entry.charLength
            blocks.all { it.chapterId == entry.chapterId } shouldBe true
        }
    }

    @Test
    fun `an old book without a native stream can be migrated from its own body`(@TempDir root: File) {
        val directory = NovelBookArtifact.directoryFor(root, sourceId = 1L, novelId = 3L)
        val chapters = listOf(NovelBookSourceChapter(id = 1L, name = "Chapter 1", url = "/1"))
        val built = NovelBookArtifactWriter(directory).build(
            request = NovelBookBuildRequest(
                sourceId = 1L,
                novelId = 3L,
                novelTitle = "Novel",
                chapterSetHash = "hash",
                builtAt = 0L,
            ),
            chapters = chapters,
            loadHtml = { "<p>Only paragraph</p>" },
        )

        // Simulate a book compiled before the native stream existed.
        NovelBookArtifact.nativeFile(directory).delete()
        NovelBookArtifact.writeIndex(
            directory,
            NovelBookIndex(built.index.chapters.map { it.copy(nativeByteStart = 0L, nativeByteLength = 0) }),
        )
        NovelBookArtifact.writeMeta(
            directory,
            built.meta.copy(nativeFormatVersion = 0, nativeComplete = false),
        )

        NovelBookNativeMigrator.needsMigration(directory) shouldBe true
        NovelBookNativeMigrator.migrate(directory) shouldBe true
        NovelBookNativeMigrator.needsMigration(directory) shouldBe false

        val migrated = NovelBookArtifact.readIndex(directory)!!.chapters.single()
        // Offsets must be byte-for-byte the ones the original build produced, otherwise saved
        // reading positions would jump after the migration.
        migrated.charStart shouldBe built.index.chapters.single().charStart
        migrated.charLength shouldBe built.index.chapters.single().charLength
        NovelBookArtifact.readNativeRange(directory, migrated.nativeByteStart, migrated.nativeByteLength)
            .first().charStart shouldBe migrated.charStart
    }
}
