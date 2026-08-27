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
