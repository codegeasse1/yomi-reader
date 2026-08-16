package eu.kanade.tachiyomi.data.book.novel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Chapter metadata the builder needs; chapter bodies are streamed lazily while building. */
data class NovelBookSourceChapter(
    val id: Long,
    val name: String,
    val url: String,
)

/**
 * Where a chapter lives inside the merged book: [charStart] in the book offset domain (used for
 * progress and saved positions) and [byteStart] in the artifact file (used for random access reads).
 */
@Serializable
data class NovelBookChapterEntry(
    val chapterId: Long,
    val order: Int,
    val title: String,
    val anchorId: String,
    val charStart: Int,
    val charLength: Int,
    val byteStart: Long,
    val byteLength: Int,
    /**
     * Where this chapter's pre-compiled native blocks live in `book.native.jsonl`.
     * Zero length means the chapter predates the native stream and must be migrated.
     */
    val nativeByteStart: Long = 0L,
    val nativeByteLength: Int = 0,
)

@Serializable
data class NovelBookIndex(
    val chapters: List<NovelBookChapterEntry> = emptyList(),
)

@Serializable
data class NovelBookMeta(
    val formatVersion: Int = NovelBookArtifact.FORMAT_VERSION,
    val bookVersion: Int = 1,
    val sourceId: Long = 0L,
    val novelId: Long = 0L,
    val novelTitle: String = "",
    val language: String? = null,
    val chapterSetHash: String = "",
    val totalChars: Int = 0,
    val totalBytes: Long = 0L,
    val chapterCount: Int = 0,
    val builtAt: Long = 0L,
    val complete: Boolean = false,
    /** 0 means no native block stream was written for this book yet. */
    val nativeFormatVersion: Int = 0,
    val nativeComplete: Boolean = false,
)

/** Everything the writer needs that does not come from the chapters themselves. */
data class NovelBookBuildRequest(
    val sourceId: Long,
    val novelId: Long,
    val novelTitle: String,
    val chapterSetHash: String,
    val language: String? = null,
    val builtAt: Long = System.currentTimeMillis(),
)

data class NovelBookBuildResult(
    val index: NovelBookIndex,
    val meta: NovelBookMeta,
    val missingChapterIds: List<Long>,
)

/**
 * On disk layout of a compiled book.
 *
 * - `book.body.html`: every chapter normalized and concatenated, with no wrapper markup, so new
 *   chapters can be appended without rewriting anything that was already read.
 * - `book.index.json`: chapter to offset map, both in characters and in file bytes.
 * - `book.meta.json`: version, chapter set hash and total size of the book.
 *
 * Reading a window is a byte range read over the body file, so nothing needs to be unzipped and the
 * whole book never has to be held in memory.
 */
object NovelBookArtifact {

    const val FORMAT_VERSION = 1
    const val BODY_FILE = "book.body.html"
    const val INDEX_FILE = "book.index.json"
    const val META_FILE = "book.meta.json"

    /**
     * Pre-compiled native blocks, one JSON object per line, in the same order as the body file.
     *
     * It is a second append-only stream next to the body instead of a replacement for it: the
     * WebView renderer, custom CSS/JS and every existing byte offset keep working untouched, while
     * the native renderer can read a ready made window without running Jsoup at scroll time.
     */
    const val NATIVE_FILE = "book.native.jsonl"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Artifacts are scoped per source and per novel, so different translations never mix. */
    fun directoryFor(root: File, sourceId: Long, novelId: Long): File =
        File(File(root, sourceId.toString()), novelId.toString())

    fun bodyFile(directory: File): File = File(directory, BODY_FILE)

    fun nativeFile(directory: File): File = File(directory, NATIVE_FILE)

    /** True when the artifact carries a native block stream this app version can read. */
    fun hasNativeStream(directory: File, meta: NovelBookMeta?): Boolean =
        meta != null &&
            meta.nativeFormatVersion == NovelBookNativeCodec.FORMAT_VERSION &&
            nativeFile(directory).exists()

    fun exists(directory: File): Boolean = bodyFile(directory).exists() && readMeta(directory) != null

    fun readIndex(directory: File): NovelBookIndex? {
        val file = File(directory, INDEX_FILE)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(NovelBookIndex.serializer(), file.readText()) }.getOrNull()
    }

    fun readMeta(directory: File): NovelBookMeta? {
        val file = File(directory, META_FILE)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(NovelBookMeta.serializer(), file.readText()) }.getOrNull()
    }

    fun writeIndex(directory: File, index: NovelBookIndex) {
        writeAtomically(File(directory, INDEX_FILE), json.encodeToString(NovelBookIndex.serializer(), index))
    }

    fun writeMeta(directory: File, meta: NovelBookMeta) {
        writeAtomically(File(directory, META_FILE), json.encodeToString(NovelBookMeta.serializer(), meta))
    }

    /** Reads a byte range of the book body, used to render one window of the continuous text. */
    fun readRange(directory: File, byteStart: Long, byteLength: Int): String {
        val file = bodyFile(directory)
        if (!file.exists() || byteLength <= 0 || byteStart < 0) return ""
        val available = (file.length() - byteStart).coerceAtMost(byteLength.toLong())
        if (available <= 0) return ""
        RandomAccessFile(file, "r").use { access ->
            access.seek(byteStart)
            val buffer = ByteArray(available.toInt())
            access.readFully(buffer)
            return String(buffer, Charsets.UTF_8)
        }
    }

    /** Reads a byte range of the native block stream and decodes the complete lines in it. */
    fun readNativeRange(directory: File, byteStart: Long, byteLength: Int): List<NovelBookNativeBlock> {
        val file = nativeFile(directory)
        if (!file.exists() || byteLength <= 0 || byteStart < 0) return emptyList()
        val available = (file.length() - byteStart).coerceAtMost(byteLength.toLong())
        if (available <= 0) return emptyList()
        RandomAccessFile(file, "r").use { access ->
            access.seek(byteStart)
            val buffer = ByteArray(available.toInt())
            access.readFully(buffer)
            return NovelBookNativeCodec.decodeChunk(String(buffer, Charsets.UTF_8))
        }
    }

    /**
     * Identity of the chapter set the artifact was built from. A changed hash means the chapter list
     * changed in the middle of the book, which requires a rebuild instead of an append.
     */
    fun chapterSetHash(chapters: List<NovelBookSourceChapter>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chapters.forEach { chapter ->
            digest.update("${chapter.id}|${chapter.url}|${chapter.name}\n".toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun delete(directory: File) {
        directory.deleteRecursively()
    }

    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.absolutePath + ".tmp")
        temporary.writeText(text)
        if (target.exists()) {
            target.delete()
        }
        if (!temporary.renameTo(target)) {
            target.writeText(text)
            temporary.delete()
        }
    }
}

/**
 * Builds and extends the compiled book of a single novel.
 *
 * Chapters are normalized one by one and streamed to disk, so building a book with thousands of
 * chapters never holds more than one chapter in memory. Appending keeps every existing offset
 * untouched, which is what makes saved reading positions survive new chapters.
 */
class NovelBookArtifactWriter(private val directory: File) {

    fun build(
        request: NovelBookBuildRequest,
        chapters: List<NovelBookSourceChapter>,
        loadHtml: (NovelBookSourceChapter) -> String?,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        imageReferer: String? = null,
    ): NovelBookBuildResult {
        // A full rebuild writes into temporary files and only swaps them in once every chapter is
        // compiled. An interrupted build (cancel, crash, IO error) must never destroy the artifact
        // that was already on disk, or the book would silently disappear until it is rebuilt.
        val body = NovelBookArtifact.bodyFile(directory)
        val native = NovelBookArtifact.nativeFile(directory)
        val tmpBody = File(body.parentFile, "${body.name}.tmp")
        val tmpNative = File(native.parentFile, "${native.name}.tmp")
        tmpBody.delete()
        tmpNative.delete()
        val result = writeSections(
            request = request,
            chapters = chapters,
            loadHtml = loadHtml,
            onProgress = onProgress,
            existingChapters = emptyList(),
            bookVersion = 1,
            imageReferer = imageReferer,
            bodyTarget = tmpBody,
            nativeTarget = tmpNative,
        )
        if (result.index.chapters.isEmpty()) {
            // Nothing was compiled (every chapter failed to load): leave the previous artifact
            // untouched and report the failure.
            tmpBody.delete()
            tmpNative.delete()
            return result
        }
        // Swap the finished files in; both renames stay on the same filesystem, so they are atomic.
        if (!tmpBody.renameTo(body)) {
            tmpBody.copyTo(body, overwrite = true)
            tmpBody.delete()
        }
        if (!tmpNative.renameTo(native)) {
            tmpNative.copyTo(native, overwrite = true)
            tmpNative.delete()
        }
        // The index and meta describe the body that is now in place, so they are written last.
        NovelBookArtifact.writeIndex(directory, result.index)
        NovelBookArtifact.writeMeta(directory, result.meta)
        return result
    }

    fun append(
        request: NovelBookBuildRequest,
        existing: NovelBookIndex,
        newChapters: List<NovelBookSourceChapter>,
        loadHtml: (NovelBookSourceChapter) -> String?,
        bookVersion: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        imageReferer: String? = null,
    ): NovelBookBuildResult {
        val known = existing.chapters.map { chapter -> chapter.chapterId }.toSet()
        val result = writeSections(
            request = request,
            chapters = newChapters.filterNot { chapter -> chapter.id in known },
            loadHtml = loadHtml,
            onProgress = onProgress,
            existingChapters = existing.chapters,
            bookVersion = bookVersion,
            imageReferer = imageReferer,
            // Appending extends the live files directly: it never rewrites already-written content,
            // so an interrupted append simply leaves the book at its previous state.
            bodyTarget = NovelBookArtifact.bodyFile(directory),
            nativeTarget = NovelBookArtifact.nativeFile(directory),
        )
        // The index and meta are refreshed only after the body has been extended.
        NovelBookArtifact.writeIndex(directory, result.index)
        NovelBookArtifact.writeMeta(directory, result.meta)
        return result
    }

    private fun writeSections(
        request: NovelBookBuildRequest,
        chapters: List<NovelBookSourceChapter>,
        loadHtml: (NovelBookSourceChapter) -> String?,
        onProgress: (Int, Int) -> Unit,
        existingChapters: List<NovelBookChapterEntry>,
        bookVersion: Int,
        imageReferer: String? = null,
        bodyTarget: File,
        nativeTarget: File,
    ): NovelBookBuildResult {
        directory.mkdirs()
        val bodyFile = bodyTarget
        val nativeFile = nativeTarget
        val appending = existingChapters.isNotEmpty()
        val entries = existingChapters.toMutableList()
        val missing = mutableListOf<Long>()
        val lastExisting = existingChapters.lastOrNull()
        var charOffset = if (lastExisting == null) 0 else lastExisting.charStart + lastExisting.charLength
        var byteOffset = if (appending) bodyFile.length() else 0L
        // The native stream is append-only exactly like the body, so an append never rewrites the
        // blocks of chapters the user may already have read.
        var nativeOffset = if (appending && nativeFile.exists()) nativeFile.length() else 0L
        var order = existingChapters.size
        val nativeAppending = appending && nativeFile.exists()
        // Only a book whose whole native stream is present may claim to be natively readable.
        var nativeUsable = !appending ||
            (nativeAppending && existingChapters.all { it.nativeByteLength > 0 })

        FileOutputStream(bodyFile, appending).use { output ->
            FileOutputStream(nativeFile, nativeAppending).use { nativeOutput ->
                chapters.forEachIndexed { position, chapter ->
                    val rawHtml = loadHtml(chapter)
                    if (rawHtml.isNullOrBlank()) {
                        missing.add(chapter.id)
                    } else {
                        val section = NovelBookChapterNormalizer.normalize(
                            rawHtml = rawHtml,
                            chapterId = chapter.id,
                            chapterName = chapter.name,
                            startOffset = charOffset,
                            chapterWebUrl = chapter.url,
                        )
                        val bytes = (section.html + "\n").toByteArray(Charsets.UTF_8)
                        output.write(bytes)
                        // Compiling from the normalized section, not from the raw chapter, is what
                        // guarantees the native blocks share the offsets of the HTML body.
                        val nativeBytes = runCatching {
                            NovelBookNativeCodec.encodeLines(
                                NovelBookNativeCompiler.compileSection(
                                    sectionHtml = section.html,
                                    chapterId = chapter.id,
                                    imageReferer = imageReferer,
                                ),
                            ).toByteArray(Charsets.UTF_8)
                        }.getOrElse {
                            nativeUsable = false
                            ByteArray(0)
                        }
                        nativeOutput.write(nativeBytes)
                        entries.add(
                            NovelBookChapterEntry(
                                chapterId = chapter.id,
                                order = order,
                                title = chapter.name,
                                anchorId = NovelBookChapterNormalizer.chapterAnchorId(chapter.id),
                                charStart = charOffset,
                                charLength = section.charCount,
                                byteStart = byteOffset,
                                byteLength = bytes.size,
                                nativeByteStart = nativeOffset,
                                nativeByteLength = nativeBytes.size,
                            ),
                        )
                        charOffset += section.charCount
                        byteOffset += bytes.size
                        nativeOffset += nativeBytes.size
                        order += 1
                    }
                    onProgress(position + 1, chapters.size)
                }
                nativeOutput.flush()
            }
            output.flush()
        }

        val index = NovelBookIndex(entries.toList())
        val meta = NovelBookMeta(
            bookVersion = bookVersion,
            sourceId = request.sourceId,
            novelId = request.novelId,
            novelTitle = request.novelTitle,
            language = request.language,
            chapterSetHash = request.chapterSetHash,
            totalChars = charOffset,
            totalBytes = byteOffset,
            chapterCount = entries.size,
            builtAt = request.builtAt,
            complete = missing.isEmpty(),
            nativeFormatVersion = if (nativeUsable) NovelBookNativeCodec.FORMAT_VERSION else 0,
            nativeComplete = nativeUsable && missing.isEmpty(),
        )
        // The caller decides when the index and meta are written: [build] swaps the body in before
        // refreshing them, [append] refreshes them only after the body has been extended.
        return NovelBookBuildResult(index = index, meta = meta, missingChapterIds = missing.toList())
    }
}
