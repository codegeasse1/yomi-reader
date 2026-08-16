package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Holds reader-ready section HTML for the continuous ("book mode") reader.
 *
 * Memory keeps only the sections around the reader (LRU), while [diskWrite] / [diskRead] delegate
 * long term storage to the existing chapter disk cache. Nothing here touches Android or IO directly,
 * so it stays unit testable and cannot affect the chapter-by-chapter reader.
 */
internal class NovelBookSectionStore(
    private val maxResidentEntries: Int = DEFAULT_MAX_RESIDENT_ENTRIES,
    private val diskRead: (Long) -> String? = { null },
    private val diskWrite: (Long, String) -> Unit = { _, _ -> },
    private val diskBaseUrlRead: (Long) -> String? = { null },
    private val diskBaseUrlWrite: (Long, String?) -> Unit = { _, _ -> },
    private val diskDelete: (Long) -> Unit = {},
    /**
     * Combined long term storage hooks.
     *
     * The split html/baseUrl hooks above exist for the chapter disk cache, which stores both under
     * separate keys. A book section cache stores one payload per section, so going through the split
     * hooks would need a read-modify-write for every write. When [diskWriteSection] is set it
     * replaces them, and [diskReadSection] is consulted before them.
     */
    private val diskReadSection: (Long) -> NovelBookPreparedSection? = { null },
    private val diskWriteSection: ((Long, NovelBookPreparedSection) -> Unit)? = null,
) {
    private val maxEntries = maxResidentEntries.coerceAtLeast(1)

    /**
     * Guards the resident map.
     *
     * Sections are prepared concurrently (the loader allows parallel loads and the reader prepares
     * on the IO dispatcher while the planner reads the store), and an access ordered LinkedHashMap
     * mutates on plain reads, so unsynchronized access could corrupt it.
     */
    private val lock = Any()

    private val resident = object : LinkedHashMap<Long, NovelBookPreparedSection>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, NovelBookPreparedSection>,
        ): Boolean {
            return size > maxEntries
        }
    }

    /** Section keys currently held in memory, oldest use first. */
    val residentSectionKeys: Set<Long> get() = synchronized(lock) { resident.keys.toSet() }

    val residentCount: Int get() = synchronized(lock) { resident.size }

    fun put(
        sectionKey: Long,
        html: String,
        baseUrl: String? = null,
        persist: Boolean = true,
    ) {
        if (html.isBlank()) return
        synchronized(lock) {
            resident[sectionKey] = NovelBookPreparedSection(html = html, baseUrl = baseUrl)
        }
        if (persist) {
            val writeSection = diskWriteSection
            if (writeSection != null) {
                runCatching {
                    writeSection(sectionKey, NovelBookPreparedSection(html = html, baseUrl = baseUrl))
                }
            } else {
                runCatching { diskWrite(sectionKey, html) }
                runCatching { diskBaseUrlWrite(sectionKey, baseUrl) }
            }
        }
    }

    /** Returns the section HTML from memory, falling back to disk and promoting it back into memory. */
    fun get(sectionKey: Long): String? = getPrepared(sectionKey)?.html

    fun getPrepared(sectionKey: Long): NovelBookPreparedSection? {
        synchronized(lock) { resident[sectionKey] }?.let { return it }
        val prepared = readFromDisk(sectionKey) ?: return null
        synchronized(lock) { resident[sectionKey] = prepared }
        return prepared
    }

    fun isResident(sectionKey: Long): Boolean = synchronized(lock) { resident.containsKey(sectionKey) }

    /** True when the section can be shown without going back to the source. */
    fun isPrepared(sectionKey: Long): Boolean {
        if (isResident(sectionKey)) return true
        return readFromDisk(sectionKey) != null
    }

    fun release(sectionKey: Long) {
        synchronized(lock) { resident.remove(sectionKey) }
    }

    /**
     * Forgets a section completely, in memory and in long term storage.
     *
     * [release] deliberately only frees memory, so an offline prepared book survives window pruning.
     * A forced reload needs this stronger variant: without it the refetched HTML would be shadowed
     * by the stale copy still sitting on disk.
     */
    fun invalidate(sectionKey: Long) {
        synchronized(lock) { resident.remove(sectionKey) }
        runCatching { diskDelete(sectionKey) }
    }

    fun clear() {
        synchronized(lock) { resident.clear() }
    }

    /** Long term storage lookup: the combined hook wins, the split chapter-cache hooks follow. */
    private fun readFromDisk(sectionKey: Long): NovelBookPreparedSection? {
        runCatching { diskReadSection(sectionKey) }
            .getOrNull()
            ?.takeIf { it.html.isNotBlank() }
            ?.let { return it }
        val html = runCatching { diskRead(sectionKey) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return NovelBookPreparedSection(
            html = html,
            baseUrl = runCatching { diskBaseUrlRead(sectionKey) }.getOrNull(),
        )
    }

    companion object {
        const val DEFAULT_MAX_RESIDENT_ENTRIES = 6
    }
}

internal data class NovelBookPreparedSection(
    val html: String,
    val baseUrl: String? = null,
)
