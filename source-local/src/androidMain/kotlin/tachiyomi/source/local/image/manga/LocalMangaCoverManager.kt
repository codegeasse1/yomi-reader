package tachiyomi.source.local.image.manga

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"
private const val GENERATED_COVER_NAME = "generated_cover.jpg"

// User-provided covers always win; app-generated covers are only a fallback.
private val COVER_BASENAMES = listOf("cover", "generated_cover")

actual class LocalMangaCoverManager(
    private val context: Context,
    private val fileSystem: LocalMangaSourceFileSystem,
) {

    actual fun find(mangaUrl: String): UniFile? {
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            // User covers first, generated covers only as a fallback.
            .filter { it.isFile && it.nameWithoutExtension.orEmpty().lowercase() in COVER_BASENAMES }
            .sortedBy { COVER_BASENAMES.indexOf(it.nameWithoutExtension.orEmpty().lowercase()) }
            // Get the first actual image
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    private fun findUserCover(mangaUrl: String): UniFile? {
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            // Only the user-provided cover file (never the generated one).
            .filter { it.isFile && it.nameWithoutExtension.orEmpty().equals("cover", ignoreCase = true) }
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    private fun findGeneratedCover(mangaUrl: String): UniFile? {
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            .filter { it.isFile && it.nameWithoutExtension.orEmpty().equals("generated_cover", ignoreCase = true) }
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    actual fun update(
        manga: SManga,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getMangaDirectory(manga.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        // Only ever writes the user cover file. Auto-generated thumbnails go through
        // [generateCover] so they can never overwrite a user-provided cover.
        val targetFile = findUserCover(manga.url) ?: directory.createFile(DEFAULT_COVER_NAME)!!

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        manga.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }

    /**
     * Writes an auto-generated cover (e.g. the rendered first page of a chapter file)
     * to the dedicated `generated_cover` file. Never touches a user-provided cover.
     */
    actual fun generateCover(
        manga: SManga,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getMangaDirectory(manga.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        val targetFile = findGeneratedCover(manga.url) ?: directory.createFile(GENERATED_COVER_NAME)!!

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        manga.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }
}
