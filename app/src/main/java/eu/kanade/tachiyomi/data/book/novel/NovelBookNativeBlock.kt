package eu.kanade.tachiyomi.data.book.novel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Storage model of one pre-compiled block of the book.
 *
 * This is deliberately a separate DTO from the reader's `NovelRichContentBlock`: the UI model may
 * change freely with the renderer, while this one is written to disk and has to stay decodable for
 * books that were compiled by an older app version. The mapping between the two lives in the reader
 * layer (`NovelBookNativeBlockMapper`).
 *
 * [charStart] and [charLength] are in the exact same book offset domain the normalizer writes into
 * `data-o` / `data-l`, so saved reading positions, the table of contents, progress and read marking
 * keep working unchanged whether the reader renders HTML or these blocks.
 */
@Serializable
data class NovelBookNativeBlock(
    val chapterId: Long,
    val charStart: Int,
    val charLength: Int,
    val kind: NovelBookNativeBlockKind,
    /** Heading level 1..6, only meaningful for [NovelBookNativeBlockKind.HEADING]. */
    val level: Int = 0,
    val align: NovelBookNativeAlign? = null,
    val indentEm: Float? = null,
    /** True for the chapter title the normalizer bakes in, so the reader can hide it by setting. */
    val isChapterHeading: Boolean = false,
    val segments: List<NovelBookNativeSegment> = emptyList(),
    val imageUrl: String? = null,
    val imageAlt: String? = null,
    /** Referer the image has to be loaded with, for sources that hotlink-protect their CDN. */
    val referer: String? = null,
)

@Serializable
enum class NovelBookNativeBlockKind {
    PARAGRAPH,
    HEADING,
    QUOTE,
    RULE,
    IMAGE,
}

@Serializable
enum class NovelBookNativeAlign {
    LEFT,
    CENTER,
    JUSTIFY,
    RIGHT,
}

/** One run of inline text with its styling. Field names are short because they are on disk. */
@Serializable
data class NovelBookNativeSegment(
    val t: String,
    val b: Boolean = false,
    val i: Boolean = false,
    val u: Boolean = false,
    val s: Boolean = false,
    val color: String? = null,
    val background: String? = null,
    val href: String? = null,
)

/**
 * JSONL codec for the native block stream.
 *
 * One block per line means the file is append-only exactly like `book.body.html`: adding chapters
 * never rewrites bytes that were already written, and a byte range read decodes only the blocks of
 * the window being rendered instead of the whole book.
 */
object NovelBookNativeCodec {

    const val FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encodeLine(block: NovelBookNativeBlock): String =
        json.encodeToString(NovelBookNativeBlock.serializer(), block)

    fun decodeLine(line: String): NovelBookNativeBlock? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            json.decodeFromString(NovelBookNativeBlock.serializer(), trimmed)
        }.getOrNull()
    }

    /**
     * Decodes a chunk of the stream. A byte range read can start or end in the middle of a line,
     * so partial first and last lines are simply dropped instead of failing the whole window.
     */
    fun decodeChunk(chunk: String): List<NovelBookNativeBlock> {
        if (chunk.isEmpty()) return emptyList()
        return chunk.lineSequence()
            .mapNotNull { decodeLine(it) }
            .toList()
    }

    fun encodeLines(blocks: List<NovelBookNativeBlock>): String =
        blocks.joinToString(separator = "") { encodeLine(it) + "\n" }
}
