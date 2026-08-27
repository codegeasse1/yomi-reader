package eu.kanade.tachiyomi.data.export.manga

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class MangaCbzExportOptions(
    val downloadedOnly: Boolean = true,
    val startChapter: Int? = null,
    val endChapter: Int? = null,
    val destinationTreeUri: String? = null,
)

data class MangaCbzExportReport(
    val totalSelected: Int,
    val includedChapters: Int,
    val skippedChapters: List<String> = emptyList(),
    val outputSizeBytes: Long = 0L,
)

data class MangaFolderDownloadReport(
    val totalChapters: Int,
    val copiedChapters: Int,
    val failedChapters: List<String> = emptyList(),
    val stalled: Boolean = false,
) {
    val success: Boolean get() = copiedChapters > 0
}

sealed interface MangaCbzExportResult {
    data class Success(
        val cacheFile: File,
        val destinationUri: Uri?,
        val report: MangaCbzExportReport,
    ) : MangaCbzExportResult

    data class Failure(
        val reason: MangaCbzExportFailure,
        val report: MangaCbzExportReport? = null,
    ) : MangaCbzExportResult
}

enum class MangaCbzExportFailure {
    NO_CHAPTERS_SELECTED,
    NO_DOWNLOADED_CHAPTERS,
    DESTINATION_PERMISSION_DENIED,
    UNKNOWN,
}

sealed interface MangaCbzExportProgress {
    data class Preparing(val totalChapters: Int) : MangaCbzExportProgress

    data class ChapterProcessed(
        val current: Int,
        val total: Int,
    ) : MangaCbzExportProgress

    data object Finalizing : MangaCbzExportProgress

    data class Done(val file: File) : MangaCbzExportProgress
}

/**
 * Exports downloaded manga chapters into a single .cbz archive, so one file can contain many
 * chapters (e.g. "Download all" -> one CBZ). The LocalMangaSource import accepts a CBZ whose
 * top-level entries are chapter directories, so importing the single file loads every chapter.
 */
class MangaCbzExporter(
    private val application: Application? = runCatching { Injekt.get<Application>() }.getOrNull(),
    private val downloadManager: MangaDownloadManager = Injekt.get(),
) {

    suspend fun exportWithResult(
        manga: Manga,
        chapters: List<Chapter>,
        source: MangaSource,
        options: MangaCbzExportOptions = MangaCbzExportOptions(),
        onProgress: (MangaCbzExportProgress) -> Unit = {},
    ): MangaCbzExportResult {
        val sorted = chapters.sortedBy { it.sourceOrder }
        val selected = applyRange(sorted, options.startChapter, options.endChapter)
        if (selected.isEmpty()) {
            return MangaCbzExportResult.Failure(
                MangaCbzExportFailure.NO_CHAPTERS_SELECTED,
                MangaCbzExportReport(totalSelected = 0, includedChapters = 0),
            )
        }
        onProgress(MangaCbzExportProgress.Preparing(totalChapters = selected.size))

        val skipped = mutableListOf<String>()
        val pagesByChapter = mutableListOf<Pair<String, List<Page>>>()

        for ((index, chapter) in selected.withIndex()) {
            currentCoroutineContext().ensureActive()
            try {
                val downloaded = downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    manga.title,
                    manga.source,
                )
                if (options.downloadedOnly && !downloaded) {
                    skipped += chapter.name
                    onProgress(MangaCbzExportProgress.ChapterProcessed(index + 1, selected.size))
                    continue
                }
                val pages = downloadManager.buildPageList(source, manga, chapter)
                if (pages.isEmpty()) {
                    skipped += chapter.name
                } else {
                    pagesByChapter += chapter.name to pages
                }
            } catch (e: Exception) {
                skipped += chapter.name
            }
            onProgress(MangaCbzExportProgress.ChapterProcessed(index + 1, selected.size))
        }

        if (pagesByChapter.isEmpty()) {
            return MangaCbzExportResult.Failure(
                MangaCbzExportFailure.NO_DOWNLOADED_CHAPTERS,
                MangaCbzExportReport(
                    totalSelected = selected.size,
                    includedChapters = 0,
                    skippedChapters = skipped,
                ),
            )
        }

        onProgress(MangaCbzExportProgress.Finalizing)

        val context = application
        val cbzFile = runCatching {
            val exportDir = File(context?.cacheDir, "exports/manga").apply { mkdirs() }
            val fileName = DiskUtil.buildValidFilename("${manga.title}_${System.currentTimeMillis()}.cbz")
            val file = File(exportDir, fileName)
            writeCbz(context, file, pagesByChapter)
            file
        }.getOrElse {
            return MangaCbzExportResult.Failure(
                MangaCbzExportFailure.UNKNOWN,
                MangaCbzExportReport(
                    totalSelected = selected.size,
                    includedChapters = pagesByChapter.size,
                    skippedChapters = skipped,
                ),
            )
        }

        val destinationUri = options.destinationTreeUri?.let { copyToDestinationTree(cbzFile, it) }
        if (options.destinationTreeUri != null && destinationUri == null) {
            return MangaCbzExportResult.Failure(
                MangaCbzExportFailure.DESTINATION_PERMISSION_DENIED,
                MangaCbzExportReport(
                    totalSelected = selected.size,
                    includedChapters = pagesByChapter.size,
                    skippedChapters = skipped,
                    outputSizeBytes = cbzFile.length(),
                ),
            )
        }

        onProgress(MangaCbzExportProgress.Done(cbzFile))
        return MangaCbzExportResult.Success(
            cacheFile = cbzFile,
            destinationUri = destinationUri,
            report = MangaCbzExportReport(
                totalSelected = selected.size,
                includedChapters = pagesByChapter.size,
                skippedChapters = skipped,
                outputSizeBytes = cbzFile.length(),
            ),
        )
    }

    private fun writeCbz(
        context: Context?,
        file: File,
        pagesByChapter: List<Pair<String, List<Page>>>,
    ) {
        ZipOutputStream(file.outputStream()).use { zos ->
            for ((chapterName, pages) in pagesByChapter) {
                val dirName = DiskUtil.buildValidFilename(chapterName)
                    .replace(Regex("[\\\\/:*?\"<>|]"), " ")
                for ((i, page) in pages.withIndex()) {
                    val uri = page.uri ?: continue
                    val ext = uri.lastPathSegment
                        ?.substringAfterLast('.', "")
                        ?.lowercase()
                        ?.takeIf { it in IMAGE_EXTENSIONS }
                        ?: "jpg"
                    val pageName = "${(i + 1).toString().padStart(3, '0')}.$ext"
                    zos.putNextEntry(ZipEntry("$dirName/$pageName"))
                    context?.contentResolver?.openInputStream(uri)?.use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun applyRange(
        chapters: List<Chapter>,
        startChapter: Int?,
        endChapter: Int?,
    ): List<Chapter> {
        if (chapters.isEmpty()) return emptyList()
        val startIndex = (startChapter ?: 1).coerceAtLeast(1) - 1
        val endIndex = ((endChapter ?: chapters.size).coerceAtMost(chapters.size) - 1)
        if (startIndex > endIndex || startIndex >= chapters.size) return emptyList()
        return chapters.subList(startIndex, endIndex + 1)
    }

    private fun copyToDestinationTree(
        cbzFile: File,
        destinationTreeUri: String,
    ): Uri? {
        val context = application ?: return null
        val treeUri = runCatching { Uri.parse(destinationTreeUri) }.getOrNull() ?: return null
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val target = root.createFile(CBZ_MIME_TYPE, cbzFile.name) ?: return null

        val copied = runCatching {
            context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                cbzFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } != null
        }.getOrDefault(false)
        return target.uri.takeIf { copied }
    }

    /**
     * Copies one downloaded chapter's page images into `folderRoot/<chapter name>/` as loose files.
     * Returns true if at least one page image was written.
     */
    fun copyDownloadedChapterToFolder(
        source: MangaSource,
        manga: Manga,
        chapter: Chapter,
        folderRoot: DocumentFile,
    ): Boolean {
        val context = application ?: return false
        val pages = runCatching { downloadManager.buildPageList(source, manga, chapter) }.getOrNull() ?: return false
        if (pages.isEmpty()) return false

        val chapterDirName = DiskUtil.buildValidFilename(chapter.name)
            .replace(Regex("[\\\\\\\\/:*?\"<>|]"), " ")
        val chapterFolder = folderRoot.createDirectory(chapterDirName) ?: return false

        var wroteAny = false
        for ((index, page) in pages.withIndex()) {
            val uri = page.uri ?: continue
            val srcFile = uri.path?.let { File(it) } ?: continue
            if (!srcFile.isFile) continue
            val ext = uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it in IMAGE_EXTENSIONS }
                ?: "jpg"
            val pageName = "${(index + 1).toString().padStart(3, '0')}.$ext"
            val dest = chapterFolder.createFile(imageMimeFor(ext), pageName) ?: continue
            val written = runCatching {
                context.contentResolver.openOutputStream(dest.uri, "w")?.use { output ->
                    srcFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } != null
            }.getOrDefault(false)
            if (written) wroteAny = true
        }
        return wroteAny
    }

    companion object {
        const val CBZ_MIME_TYPE = "application/vnd.comicbook+zip"

        private val IMAGE_EXTENSIONS = setOf(
            "jpg",
            "jpeg",
            "png",
            "gif",
            "webp",
            "bmp",
            "avif",
            "jxl",
        )

        private fun imageMimeFor(ext: String): String = when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "avif" -> "image/avif"
            "jxl" -> "image/jxl"
            else -> "image/jpeg"
        }
    }
}
