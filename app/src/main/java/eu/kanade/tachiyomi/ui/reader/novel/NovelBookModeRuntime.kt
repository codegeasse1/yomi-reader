package eu.kanade.tachiyomi.ui.reader.novel

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Spine kind of a session, kept in the cache key so artifacts never read a foreign cache entry. */
private const val SPINE_KIND_ARTIFACT = "artifact"

/**
 * Single entry point the novel reader screen model uses to drive book mode.
 *
 * It owns the spine, the section store/loader and the reading position, so the screen model only
 * has to start/stop it and report where the reader is. Everything here is Android free: the chapter
 * payload and HTML normalization come in as lambdas, which keeps the chapter-by-chapter reader
 * untouched and this whole flow unit testable.
 *
 * The runtime does not render and does not track what a renderer holds: it publishes the window
 * ([windowState]) and serves section markup on demand. Which sections are mounted is the renderer's
 * business alone.
 */
internal class NovelBookModeRuntime(
    private val sectionRepository: BookSectionRepository,
    private val showChapterHeadings: () -> Boolean = { true },
    private val prepareAhead: () -> Int = { BookWindowPolicy.DEFAULT_PREFETCH_AHEAD },
    /**
     * Scope the prepared sections are cached under, e.g. the novel plus the translation variant the
     * HTML was built with. Returning null keeps sections in memory only, which is what tests want.
     */
    private val sectionCacheScope: () -> String? = { null },
    private val readCachedSection: (String) -> NovelBookPreparedSection? = { null },
    private val writeCachedSection: (String, NovelBookPreparedSection) -> Unit = { _, _ -> },
    private val deleteCachedSection: (String) -> Unit = {},
    /**
     * Drops every cached section of this scope that was not built by the current transformation
     * chain. Called once per session, on the first cache miss.
     */
    private val pruneCachedSections: (String, String) -> Unit = { _, _ -> },
) {

    private var spine: NovelBookSpine = NovelBookSpine.EMPTY

    private val store = NovelBookSectionStore(
        diskReadSection = { sectionKey ->
            val cached = sectionCacheKey(sectionKey)?.let(readCachedSection)
            if (cached == null) pruneStaleCachedSections()
            cached
        },
        diskWriteSection = { sectionKey, section ->
            sectionCacheKey(sectionKey)?.let { key -> writeCachedSection(key, section) }
        },
        diskDelete = { sectionKey -> sectionCacheKey(sectionKey)?.let(deleteCachedSection) },
    )

    /**
     * Disk key of one section, or null while no scope is configured.
     *
     * The heading setting is part of the key on purpose: the same section can be prepared with or
     * without its chapter heading, so one shared key would serve the wrong markup.
     */
    private fun sectionCacheKey(sectionKey: Long): String? =
        sectionCacheKeyPrefix()?.let { prefix -> "${prefix}s$sectionKey" }

    /**
     * Key prefix every section of the current scope and transformation chain shares.
     *
     * The signature carries the pipeline version, the heading setting and the visible translation,
     * so entries built by a different chain can be recognized - and deleted - instead of piling up
     * on disk forever.
     */
    private fun sectionCacheKeyPrefix(): String? {
        val scope = sectionCacheScope()?.takeIf { it.isNotBlank() } ?: return null
        return "$scope-$SPINE_KIND_ARTIFACT-${sectionRepository.transformSignature()}-"
    }

    /**
     * Deletes stale entries of this scope once per session.
     *
     * A miss means the current chain has nothing cached here, which is exactly the moment the
     * entries of older chains are known to be dead weight.
     */
    private fun pruneStaleCachedSections() {
        if (staleSectionsPruned) return
        val scope = sectionCacheScope()?.takeIf { it.isNotBlank() } ?: return
        val keepPrefix = sectionCacheKeyPrefix() ?: return
        staleSectionsPruned = true
        runCatching { pruneCachedSections(scope, keepPrefix) }
    }

    private var staleSectionsPruned = false

    private val resolver = NovelBookSectionHtmlResolver(
        currentSpine = { spine },
        repository = sectionRepository,
    )

    private var loader = NovelBookSectionLoader(
        store = store,
        fetchSectionBaseUrl = resolver::resolvedBaseUrl,
        fetchSectionHtml = { sectionKey -> resolver.resolve(sectionKey) },
    )

    /** True between [startWithSpine] and [stop]; a book is being read. */
    private var running = false

    private var currentLocation: NovelBookLocation = NovelBookLocation.START

    /**
     * Window policy of the running session.
     *
     * Prefetching used to rebuild the default configuration instead of reusing the one the session
     * was started with, so a book started with a wider window kept prefetching (and pruning) with
     * the narrow default.
     */
    private var activePolicy: BookWindowPolicy = BookWindowPolicy.DEFAULT

    val isActive: Boolean get() = running

    val location: NovelBookLocation get() = currentLocation

    val engineSpine: NovelBookSpine get() = spine

    val windowPolicy: BookWindowPolicy get() = activePolicy

    val currentChapterId: Long? get() = spine.sectionAt(currentLocation.sectionIndex)?.chapterId

    /** Stops book mode and frees every prepared section. */
    fun stop() {
        running = false
        spine = NovelBookSpine.EMPTY
        currentLocation = NovelBookLocation.START
        loader.clear()
        loader = NovelBookSectionLoader(
            store = store,
            fetchSectionBaseUrl = resolver::resolvedBaseUrl,
            fetchSectionHtml = { sectionKey -> resolver.resolve(sectionKey) },
        )
    }

    /**
     * Drops every prepared section so the next pull runs the pipeline again.
     *
     * The disk cache separates translated from untranslated markup through the cache scope, but the
     * in-memory store is keyed by section alone. Hiding or showing a translation therefore left the
     * neighbouring sections serving the markup of the variant that was just switched away from,
     * which is why the "show original" button appeared to do nothing.
     */
    fun invalidatePreparedSections() {
        loader.clear()
        staleSectionsPruned = false
    }

    /** Forgets one section's markup, so the next pull rebuilds exactly that section. */
    fun invalidatePreparedSection(sectionIndex: Int) {
        val section = spine.sectionAt(sectionIndex) ?: return
        loader.invalidate(section.loaderKey)
    }

    fun moveTo(sectionIndex: Int, sectionFraction: Float) {
        if (!running) return
        val section = spine.sectionAt(sectionIndex) ?: return
        currentLocation = spine.clampLocation(
            NovelBookLocation(
                sectionIndex = sectionIndex,
                charOffset = (section.charCount * sectionFraction).toInt(),
            ),
        )
    }

    /** Moves to an exact renderer-owned text position without converting through a pixel fraction. */
    fun moveTo(location: NovelBookLocation) {
        if (!running) return
        currentLocation = spine.clampLocation(location)
    }

    fun moveToChapter(chapterId: Long, fraction: Float = 0f): Boolean {
        if (!running) return false
        val target = spine.locationForChapterFraction(chapterId, fraction) ?: return false
        currentLocation = spine.clampLocation(target)
        return true
    }

    fun sectionAt(sectionIndex: Int): NovelBookSection? = spine.sectionAt(sectionIndex)

    fun currentSection(): NovelBookSection? = spine.sectionAt(currentLocation.sectionIndex)

    /** The window the mounted renderer has to hold for the current position. */
    fun windowState(sectionRevisions: Map<Int, Long> = emptyMap()): NovelBookWindowState {
        if (!running || spine.isEmpty) return NovelBookWindowState.EMPTY
        return NovelBookWindowState(
            sectionCount = spine.sections.size,
            residentSections = activePolicy.residentSections(
                center = currentLocation.sectionIndex,
                sectionCount = spine.sections.size,
            ),
            sectionRevisions = sectionRevisions,
        )
    }

    /** Markup of one section, preparing it first when the renderer asks for it before the prefetch. */
    suspend fun sectionHtml(sectionIndex: Int): String? {
        val section = spine.sectionAt(sectionIndex) ?: return null
        loader.preparedHtml(section.loaderKey)?.let { return it }
        return when (val result = loader.prepare(section.loaderKey)) {
            is NovelBookSectionResult.Ready -> result.html
            is NovelBookSectionResult.Failed -> null
        }
    }

    /** Prepares one section's HTML by spine index, e.g. for the "prepare book" action. */
    suspend fun prepareSection(sectionKey: Long): NovelBookSectionResult = loader.prepare(sectionKey)

    suspend fun loadEngineDocument(section: NovelBookSection): NovelBookDocument {
        return when (val result = prepareSection(section.loaderKey)) {
            is NovelBookSectionResult.Ready -> NovelBookDocument(
                sectionIndex = section.index,
                chapterId = section.chapterId,
                html = result.html,
                baseUrl = result.baseUrl,
            )
            is NovelBookSectionResult.Failed -> error(
                result.message ?: "Failed to load book section ${section.index}",
            )
        }
    }

    /**
     * Prepares the sections around [sectionIndex] and releases the ones that left the window.
     *
     * This is the only place that decides what is worth holding: one policy, one caller, so the
     * window cannot drift apart from what the renderer mounts.
     */
    suspend fun prefetchAround(sectionIndex: Int): List<NovelBookSectionResult> = coroutineScope {
        if (!running || spine.isEmpty) return@coroutineScope emptyList()
        val policy = activePolicy
        val sectionCount = spine.sections.size
        val prepared = spine.sections
            .filter { loader.isPrepared(it.loaderKey) }
            .mapTo(mutableSetOf()) { it.index }
        val inFlight = loader.inFlightSectionKeys.mapTo(mutableSetOf()) { key -> key.toInt() }
        policy.releasableSections(sectionIndex, sectionCount, prepared).forEach { index ->
            spine.sectionAt(index)?.let { section -> loader.release(section.loaderKey) }
        }
        policy.prefetchOrder(
            center = sectionIndex,
            sectionCount = sectionCount,
            prepared = prepared,
            inFlight = inFlight,
        )
            .mapNotNull(spine::sectionAt)
            .map { section -> async { loader.prepare(section.loaderKey) } }
            .awaitAll()
    }

    suspend fun retrySection(sectionKey: Long): NovelBookSectionResult = loader.retry(sectionKey)

    /** Chapter behind a spine position, so the reader can act on a section it only knows by index. */
    fun chapterIdOfSection(sectionIndex: Int): Long? = spine.sectionAt(sectionIndex)?.chapterId

    fun uiState(): NovelReaderScreenModel.State.ReaderBookModeState {
        if (!running) return NovelReaderScreenModel.State.ReaderBookModeState()
        return NovelReaderScreenModel.State.ReaderBookModeState(
            isEnabled = true,
            sectionCount = spine.sections.size,
            currentSectionIndex = currentLocation.sectionIndex,
            currentSectionFraction = spine.sectionProgressOf(currentLocation),
            bookProgressFraction = spine.progressOf(currentLocation),
            renderedSectionIndices = activePolicy.residentSections(
                center = currentLocation.sectionIndex,
                sectionCount = spine.sections.size,
            ),
            preparingSectionIndices = inFlightSectionIndices().sorted(),
            failedSectionIndices = failedSectionIndices().sorted(),
            showChapterHeadings = showChapterHeadings(),
        )
    }

    private fun inFlightSectionIndices(): Set<Int> = sectionIndicesOf(loader.inFlightSectionKeys)

    private fun failedSectionIndices(): Set<Int> = sectionIndicesOf(loader.failedSectionKeys)

    private fun sectionIndicesOf(sectionKeys: Set<Long>): Set<Int> = sectionKeys
        .map { it.toInt() }
        .filter { spine.sectionAt(it) != null }
        .toSet()

    /**
     * Starts book mode from a pre-built [spine] (e.g. from a compiled book artifact) and a custom
     * section fetcher. The fetcher receives [NovelBookSection.loaderKey], i.e. the spine index of
     * the section, and must return the HTML for that section.
     */
    fun startWithSpine(
        spine: NovelBookSpine,
        resumeLocation: NovelBookLocation,
        /**
         * Window of the compiled book. Blocks are much smaller than chapters, so the caller sizes
         * the window from the block length instead of leaving it at the per-chapter default.
         */
        windowPolicy: BookWindowPolicy = BookWindowPolicy.DEFAULT,
        fetchSectionHtml: suspend (Long) -> String,
    ): NovelBookLocation {
        this.spine = spine
        loader = NovelBookSectionLoader(
            store = store,
            fetchSectionBaseUrl = { null },
            fetchSectionHtml = fetchSectionHtml,
        )
        activePolicy = windowPolicy.copy(
            prefetchAhead = prepareAhead().coerceAtLeast(windowPolicy.prefetchAhead),
        )
        running = true
        currentLocation = spine.clampLocation(resumeLocation)
        return currentLocation
    }
}
