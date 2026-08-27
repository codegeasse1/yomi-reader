package eu.kanade.tachiyomi.ui.browse.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hippo.unifile.UniFile
import eu.kanade.domain.entries.novel.LocalNovelBookImport
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

/**
 * Imports manga/novel files picked with the system document picker into the
 * local sources, so they can be read fully offline.
 *
 * Manga imports land in `local/<Title>/<file>` (one series folder per picked
 * file, matching how [tachiyomi.source.local.entries.manga.LocalMangaSource]
 * lays out series), novels land directly in `localnovel/` as standalone books.
 */
object LocalSourceImport {

    val MANGA_IMPORT_MIME_TYPES: Array<String> = arrayOf(
        "application/epub+zip",
        "application/pdf",
        "application/zip",
        "application/x-zip-compressed",
        "application/vnd.comicbook+zip",
        "application/x-cbz",
        "application/vnd.comicbook-rar",
        "application/x-rar-compressed",
        "application/x-cbr",
        // Some OEM file managers only expose files under application/octet-stream.
        "application/octet-stream",
    )

    val NOVEL_IMPORT_MIME_TYPES: Array<String> = arrayOf(
        "application/epub+zip",
        "application/x-fictionbook+xml",
        "application/xml",
        "text/xml",
        "text/plain",
        "text/markdown",
        "text/html",
        "application/zip",
        "application/x-zip-compressed",
        "application/vnd.comicbook+zip",
        "application/x-cbz",
        "application/vnd.comicbook-rar",
        "application/x-rar-compressed",
        "application/x-cbr",
        "application/octet-stream",
    )

    /**
     * Copies the picked file into `local/<Title>/` as a single-chapter series.
     * If the file is a merged multi-chapter CBZ (a zip whose top-level entries
     * are chapter directories), each directory is unpacked so every chapter
     * shows up in the local source from a single import.
     * @return an error message, or `null` on success.
     */
    suspend fun importManga(context: Context, uri: Uri): String? = withIOContext {
        val baseDir = Injekt.get<StorageManager>().getLocalMangaSourceDirectory()
            ?: return@withIOContext "Local source directory unavailable"

        val fileName = LocalNovelBookImport.sanitizeFileName(
            resolveDisplayName(context, uri) ?: return@withIOContext "Could not read file name",
        )
        if (fileName.isBlank()) return@withIOContext "Could not read file name"

        val title = LocalNovelBookImport.sanitizeFileName(titleFromFileName(fileName))
        if (title.isBlank()) return@withIOContext "Could not read file name"

        val mangaDir = baseDir.createDirectory(title)
            ?: return@withIOContext "Could not create series folder"

        if (isMergedDirectoryZip(context, uri)) {
            return@withIOContext unpackMergedZip(context, uri, mangaDir)
        }

        val target = mangaDir.createFile(fileName)
            ?: return@withIOContext "Could not create file in local source"

        copyTo(context, uri, target)
    }

    /**
     * Copies the picked file into `localnovel/` as a standalone book.
     * @return an error message, or `null` on success.
     */
    suspend fun importNovel(context: Context, uri: Uri): String? = withIOContext {
        val baseDir = Injekt.get<StorageManager>().getLocalNovelSourceDirectory()
            ?: return@withIOContext "Local source directory unavailable"

        val fileName = LocalNovelBookImport.sanitizeFileName(
            resolveDisplayName(context, uri) ?: return@withIOContext "Could not read file name",
        )
        if (fileName.isBlank()) return@withIOContext "Could not read file name"

        val target = baseDir.createFile(fileName)
            ?: return@withIOContext "Could not create file in local source"

        copyTo(context, uri, target)
    }

    private fun copyTo(context: Context, uri: Uri, target: UniFile): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return "Could not open selected file"
            input.use { stream ->
                target.openOutputStream().use { output ->
                    stream.copyTo(output)
                }
            }
            null
        } catch (e: Exception) {
            e.message ?: "Import failed"
        }
    }

    /**
     * True when the picked zip has at least two top-level chapter directories
     * and no bare files at its root - i.e. a merged multi-chapter CBZ produced
     * by the "Export as CBZ" feature.
     */
    private fun isMergedDirectoryZip(context: Context, uri: Uri): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { stream ->
                val zip = ZipInputStream(BufferedInputStream(stream))
                val topLevelDirs = mutableSetOf<String>()
                var hasBareFile = false
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name.isNotEmpty()) {
                        val slash = name.indexOf('/')
                        if (slash > 0) {
                            topLevelDirs += name.substring(0, slash)
                        } else {
                            hasBareFile = true
                        }
                    }
                    entry = zip.nextEntry
                }
                topLevelDirs.size >= 2 && !hasBareFile
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Unpacks a merged multi-chapter CBZ into `local/<Title>/<chapter>/<pages>`,
     * where each top-level directory becomes one chapter in the local source.
     */
    private fun unpackMergedZip(context: Context, uri: Uri, mangaDir: UniFile): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return "Could not open selected file"
            var count = 0
            input.use { stream ->
                val zip = ZipInputStream(BufferedInputStream(stream))
                val knownDirs = mutableMapOf<String, UniFile>()
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name.isNotEmpty()) {
                        val slash = name.indexOf('/')
                        if (slash > 0) {
                            val chapterDirName = name.substring(0, slash)
                            val fileName = name.substring(slash + 1)
                            if (fileName.isNotEmpty()) {
                                val chapterDir = knownDirs.getOrPut(chapterDirName) {
                                    val safeName = LocalNovelBookImport.sanitizeFileName(chapterDirName)
                                        .ifBlank { "chapter-${knownDirs.size}" }
                                    mangaDir.createDirectory(safeName)
                                        ?: return "Could not create chapter folder"
                                }
                                val target = chapterDir.createFile(
                                    LocalNovelBookImport.sanitizeFileName(fileName),
                                )
                                if (target != null) {
                                    target.openOutputStream().use { out ->
                                        zip.copyTo(out)
                                    }
                                    count++
                                }
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            if (count == 0) "Could not read any chapter from archive" else null
        } catch (e: Exception) {
            e.message ?: "Import failed"
        }
    }

    private fun titleFromFileName(fileName: String): String {
        val name = fileName.trim()
        return name.substringBeforeLast('.').ifBlank { name }.ifBlank { "untitled" }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        val fromCursor = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            }
        }.getOrNull()
        if (!fromCursor.isNullOrBlank()) return fromCursor
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }
}
