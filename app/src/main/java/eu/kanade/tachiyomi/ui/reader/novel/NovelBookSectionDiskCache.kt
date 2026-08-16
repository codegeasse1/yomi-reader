package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.tachiyomi.ui.reader.novel.cache.NovelReaderCacheReporter
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.util.safeCacheDir
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private const val DEFAULT_BOOK_SECTION_CACHE_MAX_ENTRIES = 4000
private const val DEFAULT_BOOK_SECTION_CACHE_MAX_TOTAL_BYTES = 256L * 1024L * 1024L
private const val DEFAULT_BOOK_SECTION_CACHE_MAX_ENTRY_BYTES = 4L * 1024L * 1024L

/** Payload format marker, bumped when the on-disk encoding changes. */
private const val BOOK_SECTION_PAYLOAD_VERSION = "v1"

internal data class NovelBookSectionDiskCacheConfig(
    val maxEntries: Int = DEFAULT_BOOK_SECTION_CACHE_MAX_ENTRIES,
    val maxTotalBytes: Long = DEFAULT_BOOK_SECTION_CACHE_MAX_TOTAL_BYTES,
    val maxEntryBytes: Long = DEFAULT_BOOK_SECTION_CACHE_MAX_ENTRY_BYTES,
    val unlimited: Boolean = false,
)

internal data class NovelBookSectionDiskCacheStats(
    val entryCount: Int,
    val totalBytes: Long,
)

/**
 * Long term storage for reader-ready book-mode section HTML.
 *
 * Book mode needs its own cache: the chapter disk cache holds the *raw* chapter payload that the
 * chapter-by-chapter reader re-parses, while a book section is already normalized, heading-wrapped
 * and possibly translated. Mixing both in one directory would let one reader serve the other's
 * markup, so sections live here under keys that carry their scope (novel + spine kind) and their
 * rendering variant (headings, translation visibility).
 *
 * Entries are gzipped and pruned LRU by last modification, and the class only depends on [File], so
 * it can be unit tested against a temporary directory.
 */
internal class NovelBookSectionDiskCache(
    private val directory: File,
    private val configProvider: () -> NovelBookSectionDiskCacheConfig = { NovelBookSectionDiskCacheConfig() },
) {
    private val lock = Any()

    /** Returns the stored section for [key], or null when it is missing or unreadable. */
    fun read(key: String): NovelBookPreparedSection? {
        synchronized(lock) {
            val config = configProvider()
            val file = fileFor(key)
            if (!file.isFile) return null
            if (file.length() <= 0L || (!config.unlimited && file.length() > config.maxEntryBytes)) {
                file.delete()
                return null
            }
            val payload = runCatching {
                GZIPInputStream(file.inputStream().buffered()).use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }.onFailure { file.delete() }.getOrNull() ?: return null
            val section = decodePayload(payload)
            if (section == null) {
                file.delete()
                return null
            }
            touchLocked(file)
            return section
        }
    }

    fun write(key: String, section: NovelBookPreparedSection) {
        if (section.html.isBlank()) return
        val config = configProvider()
        val compressed = runCatching { gzip(encodePayload(section)) }.getOrNull() ?: return
        val compressedSize = compressed.size.toLong()
        if (compressedSize <= 0L) return
        if (!config.unlimited && compressedSize > config.maxEntryBytes) return

        synchronized(lock) {
            ensureDirectory()
            val file = fileFor(key)
            val tempFile = File(directory, file.name + ".tmp")
            runCatching {
                tempFile.outputStream().buffered().use { output -> output.write(compressed) }
                if (file.exists() && !file.delete()) {
                    throw IOException("Failed to replace book section cache file")
                }
                if (!tempFile.renameTo(file)) {
                    throw IOException("Failed to move book section cache file into place")
                }
                touchLocked(file)
                pruneLocked(config)
            }.onFailure {
                tempFile.delete()
                logcat(LogPriority.WARN, it) { "Failed to write novel book section cache" }
            }
        }
    }

    fun contains(key: String): Boolean {
        synchronized(lock) {
            val file = fileFor(key)
            return file.isFile && file.length() > 0L
        }
    }

    fun remove(key: String) {
        synchronized(lock) {
            fileFor(key).delete()
        }
    }

    /**
     * Drops every entry of one scope, e.g. when a book is rebuilt and its cached sections must not
     * survive into the next session.
     */
    fun removeScope(scopePrefix: String) {
        if (scopePrefix.isBlank()) return
        val prefix = sanitize(scopePrefix)
        synchronized(lock) {
            sectionFilesLocked()
                .filter { it.name.startsWith(prefix) }
                .forEach { it.delete() }
        }
    }

    /**
     * Drops every entry of one scope except those built by the current transformation chain.
     *
     * Section markup depends on the pipeline version, the heading setting and the visible
     * translation, all of which are part of the key. Entries of older variants used to stay on disk
     * forever, because nothing ever looked them up again; they are deleted here instead, the first
     * time the current variant misses.
     */
    fun removeScopeExcept(scopePrefix: String, keepPrefix: String) {
        if (scopePrefix.isBlank() || keepPrefix.isBlank()) return
        val scope = sanitize(scopePrefix)
        val keep = sanitize(keepPrefix)
        synchronized(lock) {
            sectionFilesLocked()
                .filter { it.name.startsWith(scope) && !it.name.startsWith(keep) }
                .forEach { it.delete() }
        }
    }

    fun clear() {
        synchronized(lock) {
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    fun stats(): NovelBookSectionDiskCacheStats {
        // The directory walk is the expensive part; the byte sum runs after the lock is released
        // so a background budget walk cannot stall a main-thread read for its whole duration.
        val files = synchronized(lock) { sectionFilesLocked() }
        return NovelBookSectionDiskCacheStats(
            entryCount = files.size,
            totalBytes = files.sumOf { it.length().coerceAtLeast(0L) },
        )
    }

    fun trimToLimits(config: NovelBookSectionDiskCacheConfig = configProvider()) {
        synchronized(lock) {
            pruneLocked(config)
        }
    }

    /**
     * Drops the oldest entries until the cache fits into [targetBytes].
     *
     * The global cache coordinator calls this when the whole reader cache exceeds its budget; the
     * limit-based trim above would only enforce this cache's own caps and could leave it oversized.
     */
    fun trimToTargetBytes(targetBytes: Long) {
        if (targetBytes <= 0L) {
            clear()
            return
        }
        synchronized(lock) {
            val files = sectionFilesLocked()
                .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
                .toMutableList()
            var totalBytes = files.sumOf { it.length().coerceAtLeast(0L) }
            while (totalBytes > targetBytes) {
                val oldest = files.removeFirstOrNull() ?: break
                totalBytes -= oldest.length().coerceAtLeast(0L)
                oldest.delete()
            }
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    private fun pruneLocked(config: NovelBookSectionDiskCacheConfig) {
        if (config.unlimited) return
        val files = sectionFilesLocked()
            .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
            .toMutableList()
        var totalBytes = files.sumOf { it.length().coerceAtLeast(0L) }
        while (files.size > config.maxEntries || totalBytes > config.maxTotalBytes) {
            val oldest = files.removeFirstOrNull() ?: break
            totalBytes -= oldest.length().coerceAtLeast(0L)
            oldest.delete()
        }
    }

    private fun sectionFilesLocked(): List<File> {
        return directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("gz", ignoreCase = true) }
            .orEmpty()
    }

    private fun fileFor(key: String): File = File(directory, fileNameOf(key))

    /**
     * File name of [key]: the sanitized key keeps the scope readable (so [removeScope] can match on
     * it) and the appended hash keeps distinct keys distinct after sanitizing and truncating.
     */
    private fun fileNameOf(key: String): String {
        val sanitized = sanitize(key).take(MAX_NAME_CHARS)
        return sanitized + "-" + Integer.toHexString(key.hashCode()) + ".gz"
    }

    private fun sanitize(value: String): String = value.replace(UNSAFE_NAME_CHARS, "_")

    private fun touchLocked(file: File) {
        file.setLastModified(System.currentTimeMillis())
    }

    private fun encodePayload(section: NovelBookPreparedSection): String =
        BOOK_SECTION_PAYLOAD_VERSION + "\n" + section.baseUrl.orEmpty() + "\n" + section.html

    private fun decodePayload(payload: String): NovelBookPreparedSection? {
        val parts = payload.split("\n", limit = 3)
        if (parts.size < 3 || parts[0] != BOOK_SECTION_PAYLOAD_VERSION) return null
        val html = parts[2]
        if (html.isBlank()) return null
        return NovelBookPreparedSection(
            html = html,
            baseUrl = parts[1].takeIf { it.isNotBlank() },
        )
    }

    private fun gzip(payload: String): ByteArray {
        val sourceBytes = payload.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream(sourceBytes.size.coerceAtMost(8192))
        GZIPOutputStream(output).use { gzip ->
            ByteArrayInputStream(sourceBytes).use { input ->
                input.copyTo(gzip)
            }
        }
        return output.toByteArray()
    }

    companion object {
        internal const val DEFAULT_MAX_ENTRIES = DEFAULT_BOOK_SECTION_CACHE_MAX_ENTRIES
        internal const val DEFAULT_MAX_TOTAL_BYTES = DEFAULT_BOOK_SECTION_CACHE_MAX_TOTAL_BYTES
        internal const val DEFAULT_MAX_ENTRY_BYTES = DEFAULT_BOOK_SECTION_CACHE_MAX_ENTRY_BYTES
        private const val MAX_NAME_CHARS = 120
        private val UNSAFE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}

/** App wide instance of the book-mode section cache, registered with the global cache coordinator. */
internal object NovelBookSectionDiskCacheStore {
    private val prefs by lazy { Injekt.get<NovelReaderPreferences>() }

    private fun config(unlimitedOverride: Boolean? = null): NovelBookSectionDiskCacheConfig {
        return NovelBookSectionDiskCacheConfig(
            unlimited = unlimitedOverride ?: prefs.cacheReadChaptersUnlimited().get(),
        )
    }

    private val cache by lazy {
        val app = Injekt.get<Application>()
        NovelBookSectionDiskCache(
            directory = File(app.safeCacheDir(), "novel_book_section_cache"),
            configProvider = { config() },
        )
    }

    init {
        GlobalCacheCoordinator.instance.register(object : NovelReaderCacheReporter {
            override fun cacheId(): String = "novel-book-section-disk"
            override fun currentBytes(): Long = cache.stats().totalBytes
            override fun trimToTargetBytes(targetBytes: Long) {
                cache.trimToTargetBytes(targetBytes)
            }
        })
    }

    fun read(key: String): NovelBookPreparedSection? = cache.read(key)

    fun write(key: String, section: NovelBookPreparedSection) = cache.write(key, section)

    fun contains(key: String): Boolean = cache.contains(key)

    fun remove(key: String) = cache.remove(key)

    fun removeScope(scopePrefix: String) = cache.removeScope(scopePrefix)

    fun removeScopeExcept(scopePrefix: String, keepPrefix: String) =
        cache.removeScopeExcept(scopePrefix, keepPrefix)

    fun stats(): NovelBookSectionDiskCacheStats = cache.stats()

    fun trimToCurrentLimits(unlimitedOverride: Boolean? = null) = cache.trimToLimits(config(unlimitedOverride))

    fun clear() = cache.clear()
}
