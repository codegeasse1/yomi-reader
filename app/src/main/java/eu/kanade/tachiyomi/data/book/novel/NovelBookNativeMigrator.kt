package eu.kanade.tachiyomi.data.book.novel

import java.io.File
import java.io.FileOutputStream

/**
 * Builds the native block stream of a book that was compiled before this format existed.
 *
 * The migration reads the book's own `book.body.html` chapter by chapter, so nothing is downloaded
 * again and the offsets are, by construction, identical to the ones already stored in the index and
 * in the user's saved reading position. It is safe to interrupt: the artifact keeps working in HTML
 * mode until the native stream is written and the meta flag flipped.
 */
object NovelBookNativeMigrator {

    /** True when [directory] holds a book whose native stream is missing or outdated. */
    fun needsMigration(directory: File): Boolean {
        val meta = NovelBookArtifact.readMeta(directory) ?: return false
        if (meta.nativeFormatVersion == NovelBookNativeCodec.FORMAT_VERSION &&
            NovelBookArtifact.nativeFile(directory).exists()
        ) {
            return false
        }
        return NovelBookArtifact.readIndex(directory)?.chapters?.isNotEmpty() == true
    }

    /**
     * Compiles the native stream for an existing artifact and rewrites the index and meta.
     *
     * @param onProgress invoked with (done, total) after every chapter.
     * @return true when the artifact now has an up to date native stream.
     */
    fun migrate(
        directory: File,
        imageReferer: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): Boolean {
        val index = NovelBookArtifact.readIndex(directory) ?: return false
        val meta = NovelBookArtifact.readMeta(directory) ?: return false
        if (index.chapters.isEmpty()) return false

        val target = NovelBookArtifact.nativeFile(directory)
        val temporary = File(target.absolutePath + ".tmp")
        temporary.delete()

        val entries = ArrayList<NovelBookChapterEntry>(index.chapters.size)
        var nativeOffset = 0L
        var failed = false

        FileOutputStream(temporary, false).use { output ->
            index.chapters.forEachIndexed { position, chapter ->
                if (isCancelled()) {
                    failed = true
                    return@forEachIndexed
                }
                val sectionHtml = NovelBookArtifact.readRange(
                    directory = directory,
                    byteStart = chapter.byteStart,
                    byteLength = chapter.byteLength,
                )
                val blocks = NovelBookNativeCompiler.compileSection(
                    sectionHtml = sectionHtml,
                    chapterId = chapter.chapterId,
                    imageReferer = imageReferer,
                )
                val bytes = NovelBookNativeCodec.encodeLines(blocks).toByteArray(Charsets.UTF_8)
                output.write(bytes)
                entries += chapter.copy(
                    nativeByteStart = nativeOffset,
                    nativeByteLength = bytes.size,
                )
                nativeOffset += bytes.size
                onProgress(position + 1, index.chapters.size)
            }
            output.flush()
        }

        if (failed) {
            temporary.delete()
            return false
        }

        if (target.exists()) target.delete()
        if (!temporary.renameTo(target)) {
            temporary.delete()
            return false
        }

        NovelBookArtifact.writeIndex(directory, NovelBookIndex(entries))
        NovelBookArtifact.writeMeta(
            directory,
            meta.copy(
                nativeFormatVersion = NovelBookNativeCodec.FORMAT_VERSION,
                nativeComplete = true,
            ),
        )
        return true
    }
}
