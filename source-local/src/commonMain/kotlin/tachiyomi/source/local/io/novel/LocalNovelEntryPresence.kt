package tachiyomi.source.local.io.novel

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension

/**
 * Resolves a local-novel DB [url] against the `localnovel/` base directory.
 *
 * Browse and chapter list both use this resolution model so a missing entry
 * is treated consistently (no browse card, no chapters, purge cached ghosts).
 */
fun LocalNovelSourceFileSystem.resolveLocalNovelEntry(url: String): UniFile? {
    if (url.isBlank()) return null
    val base = getBaseDirectory() ?: return null
    base.findFile(url)?.let { return it }
    return getFilesInBaseDirectory().firstOrNull { file ->
        !file.isDirectory &&
            file.nameWithoutExtension.orEmpty().equals(url, ignoreCase = true) &&
            LocalNovelFormats.isSupportedExtension(file.extension)
    }
}

fun LocalNovelSourceFileSystem.hasLocalNovelEntry(url: String): Boolean {
    return resolveLocalNovelEntry(url) != null
}

/**
 * True when the resolved entry can produce at least one supported chapter
 * (directory with supported files, or a single supported file).
 */
fun LocalNovelSourceFileSystem.hasSupportedLocalNovelContent(url: String): Boolean {
    val entry = resolveLocalNovelEntry(url) ?: return false
    if (!entry.isDirectory) {
        return LocalNovelFormats.isSupportedExtension(entry.extension)
    }
    return entry.listFiles().orEmpty().any { child ->
        !child.name.orEmpty().startsWith('.') &&
            (
                (child.isDirectory && hasSupportedFilesRecursively(child)) ||
                    LocalNovelFormats.isSupportedExtension(child.extension)
                )
    }
}

private fun hasSupportedFilesRecursively(directory: UniFile): Boolean {
    return directory.listFiles().orEmpty().any { child ->
        if (child.name.orEmpty().startsWith('.')) return@any false
        when {
            child.isDirectory -> hasSupportedFilesRecursively(child)
            else -> LocalNovelFormats.isSupportedExtension(child.extension)
        }
    }
}
