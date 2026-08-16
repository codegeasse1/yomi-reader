package eu.kanade.tachiyomi.ui.player.subtitle.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SubtitleTranslationDiskCache(
    private val rootDir: File,
    private val maxEntries: Int = 200,
    private val maxBytes: Long = 32L * 1024 * 1024,
) {
    suspend fun read(cacheKey: String): SubtitleDocument? = withContext(Dispatchers.IO) {
        val file = fileFor(cacheKey)
        if (!file.isFile) return@withContext null
        val raw = file.readText(Charsets.UTF_8)
        // Touch for LRU recency.
        runCatching { file.setLastModified(System.currentTimeMillis()) }
        runCatching { SubtitleParser.parse(raw, file.extension) }.getOrNull()
    }

    suspend fun write(cacheKey: String, document: SubtitleDocument): File = withContext(Dispatchers.IO) {
        if (!rootDir.exists()) rootDir.mkdirs()
        val file = fileFor(cacheKey, document.format.extension)
        val tmp = File(file.parentFile, file.name + ".tmp")
        // Atomic-ish write: write temp then rename so a crash never leaves a half file.
        tmp.writeText(SubtitleWriter.write(document), Charsets.UTF_8)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        prune()
        file
    }

    suspend fun file(cacheKey: String, format: SubtitleFormat = SubtitleFormat.Vtt): File = withContext(
        Dispatchers.IO,
    ) {
        fileFor(cacheKey, format.extension)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        rootDir.deleteRecursively()
        rootDir.mkdirs()
    }

    /** Evicts least-recently-used entries once count or total size limits are exceeded. */
    private fun prune() {
        val files = rootDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var count = 0
        var bytes = 0L
        files.forEach { file ->
            count++
            bytes += file.length()
            if (count > maxEntries || bytes > maxBytes) {
                runCatching { file.delete() }
            }
        }
    }

    private fun fileFor(cacheKey: String, extension: String? = null): File {
        if (!rootDir.exists()) rootDir.mkdirs()
        if (extension != null) return File(rootDir, "$cacheKey.$extension")
        return rootDir.listFiles()
            ?.firstOrNull { it.nameWithoutExtension == cacheKey && !it.name.endsWith(".tmp") }
            ?: File(rootDir, "$cacheKey.vtt")
    }
}
