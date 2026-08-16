package tachiyomi.source.local.image.novel

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.io.novel.LocalNovelSourceFileSystem
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"
private const val GENERATED_COVER_NAME = "generated_cover.jpg"

private val DIRECTORY_COVER_NAMES = listOf("cover", "folder", "poster", "thumbnail")
private const val GENERATED_COVER_BASENAME = "generated_cover"

// User-provided covers always win; app-generated covers are only a fallback.
private val ALL_DIRECTORY_COVER_NAMES = DIRECTORY_COVER_NAMES + GENERATED_COVER_BASENAME

actual class LocalNovelCoverManager(
    private val context: Context,
    private val fileSystem: LocalNovelSourceFileSystem,
) {

    actual fun find(novelUrl: String): UniFile? {
        val novelDir = fileSystem.getNovelDirectory(novelUrl)
        return if (novelDir != null) {
            novelDir.listFiles().orEmpty()
                .filter { it.isFile }
                .filter { isPreferredDirectoryCoverName(it.nameWithoutExtension) }
                .sortedBy { coverNamePriority(it.nameWithoutExtension) }
                .firstOrNull {
                    ImageUtil.isImage(it.name) { it.openInputStream() }
                }
        } else {
            val baseDir = fileSystem.getBaseDirectory() ?: return null
            val nameWithoutExt = novelUrl.substringBeforeLast('.')
            baseDir.listFiles().orEmpty()
                .filter {
                    it.isFile &&
                        !it.name.equals(novelUrl, ignoreCase = true) &&
                        it.nameWithoutExtension.equals(nameWithoutExt, ignoreCase = true)
                }
                .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
        }
    }

    private fun findUserCover(novelUrl: String): UniFile? {
        val novelDir = fileSystem.getNovelDirectory(novelUrl) ?: return null
        return novelDir.listFiles().orEmpty()
            .filter { it.isFile }
            .filter { it.nameWithoutExtension.orEmpty().lowercase() in DIRECTORY_COVER_NAMES }
            .sortedBy { DIRECTORY_COVER_NAMES.indexOf(it.nameWithoutExtension.orEmpty().lowercase()) }
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    private fun findGeneratedCover(novelUrl: String): UniFile? {
        val novelDir = fileSystem.getNovelDirectory(novelUrl) ?: return null
        return novelDir.listFiles().orEmpty()
            .filter {
                it.isFile && it.nameWithoutExtension.orEmpty().equals(GENERATED_COVER_BASENAME, ignoreCase = true)
            }
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    private fun isPreferredDirectoryCoverName(nameWithoutExtension: String?): Boolean {
        return coverNamePriority(nameWithoutExtension) != Int.MAX_VALUE
    }

    private fun coverNamePriority(nameWithoutExtension: String?): Int {
        return ALL_DIRECTORY_COVER_NAMES.indexOfFirst {
            it.equals(nameWithoutExtension, ignoreCase = true)
        }
            .takeIf { it >= 0 }
            ?: Int.MAX_VALUE
    }

    actual fun update(
        novel: SNovel,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getNovelDirectory(novel.url)
        val targetFile = if (directory != null) {
            // Only ever writes the user cover file. Auto-generated covers go through
            // [generateCover] so they can never overwrite a user-provided cover.
            findUserCover(novel.url) ?: directory.createFile(DEFAULT_COVER_NAME)!!
        } else {
            val baseDir = fileSystem.getBaseDirectory() ?: return null
            val nameWithoutExt = novel.url.substringBeforeLast('.')
            find(novel.url) ?: baseDir.createFile("$nameWithoutExt.jpg")!!
        }

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (directory != null) {
            DiskUtil.createNoMediaFile(directory, context)
        }

        novel.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }

    /**
     * Writes an auto-generated cover (e.g. an embedded EPUB/FB2 cover) to the
     * dedicated `generated_cover` file. Never touches a user-provided cover.
     */
    actual fun generateCover(
        novel: SNovel,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getNovelDirectory(novel.url)
        val targetFile = if (directory != null) {
            findGeneratedCover(novel.url) ?: directory.createFile(GENERATED_COVER_NAME)!!
        } else {
            // Standalone book file: keep the cover as a sidecar next to the file.
            val baseDir = fileSystem.getBaseDirectory() ?: return null
            val nameWithoutExt = novel.url.substringBeforeLast('.')
            find(novel.url) ?: baseDir.createFile("$nameWithoutExt.jpg")!!
        }

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (directory != null) {
            DiskUtil.createNoMediaFile(directory, context)
        }

        novel.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }
}
