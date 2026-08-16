package eu.kanade.tachiyomi.data.book.novel

import android.app.Application
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.book.novel.model.NovelBookState
import tachiyomi.domain.book.novel.repository.NovelBookStateRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.coroutines.coroutineContext

/** Progress of a book build, reported per chapter so the UI can show a determinate bar. */
data class NovelBookBuildProgress(
    val phase: Phase,
    val done: Int,
    val total: Int,
) {
    /**
     * PARSING covers compiling the native block stream of a book that was built before that
     * format existed. It is reported separately because it downloads nothing and can be
     * cancelled at any point without losing the book.
     */
    enum class Phase { DOWNLOADING, MERGING, PARSING }

    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** Outcome of a build request. */
sealed interface NovelBookBuildOutcome {
    data class Built(val state: NovelBookState, val missingChapterIds: List<Long>) : NovelBookBuildOutcome
    data class MissingDownloads(val missingChapters: List<NovelChapter>) : NovelBookBuildOutcome
    data object NothingToBuild : NovelBookBuildOutcome
}

/**
 * Compiles the downloaded chapters of a novel into a single book artifact and records its state.
 *
 * The builder never fetches chapter text itself: chapters must already be downloaded, or
 * [downloadMissing] has to be set so the download manager can fill the gaps first. That keeps the
 * merge step deterministic and lets a build resume after a crash, because the artifact is written
 * chapter by chapter in source order and every offset is recorded in the index.
 */
class NovelBookBuilder(
    private val rootDirectory: File = defaultRootDirectory(),
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val repository: NovelBookStateRepository = Injekt.get(),
    private val now: () -> Long = { System.currentTimeMillis() },
    /**
     * Referer stored with the images of the compiled book.
     *
     * Many sites reject hotlinked illustrations, so the chapter-by-chapter reader already
     * sends the site URL as a referer. The compiled book has to carry it too, otherwise
     * remote illustrations that load in chapter mode would break in book mode.
     */
    private val imageReferer: (Novel) -> String? = { novel -> defaultImageReferer(novel) },
) {

    /**
     * Builds (or rebuilds) the book of [novel] from [chapters].
     *
     * @param chapters chapters in reading order; the caller decides the range to include.
     * @param downloadMissing when false, a build that would skip chapters returns
     *   [NovelBookBuildOutcome.MissingDownloads] instead so the caller can ask the user first.
     * @param enableAfterBuild marks the title as read-as-book once the artifact is ready.
     * @param loadHtml optional chapter text provider. Local books (.epub/.fb2) carry their own
     *   text, so when a provider is given the download checks are skipped entirely and the text is
     *   pulled straight from the file instead of the per-chapter download cache.
     */
    suspend fun build(
        novel: Novel,
        chapters: List<NovelChapter>,
        downloadMissing: Boolean = false,
        enableAfterBuild: Boolean = true,
        loadHtml: ((NovelBookSourceChapter) -> String?)? = null,
        onProgress: (NovelBookBuildProgress) -> Unit = {},
    ): NovelBookBuildOutcome {
        if (chapters.isEmpty()) return NovelBookBuildOutcome.NothingToBuild

        val notDownloaded = if (loadHtml != null) {
            emptyList()
        } else {
            chapters.filterNot { downloadManager.isChapterDownloaded(novel, it.id) }
        }
        if (notDownloaded.isNotEmpty()) {
            if (!downloadMissing) {
                return NovelBookBuildOutcome.MissingDownloads(notDownloaded)
            }
            notDownloaded.forEachIndexed { index, chapter ->
                coroutineContext.ensureActive()
                onProgress(
                    NovelBookBuildProgress(
                        phase = NovelBookBuildProgress.Phase.DOWNLOADING,
                        done = index,
                        total = notDownloaded.size,
                    ),
                )
                runCatching { downloadManager.downloadChapter(novel, chapter) }
                    .onFailure { error ->
                        logcat(LogPriority.WARN, error) {
                            "Book build could not download chapter ${chapter.id} of novel ${novel.id}"
                        }
                    }
            }
        }

        val sourceChapters = chapters.map { chapter ->
            NovelBookSourceChapter(id = chapter.id, name = chapter.name, url = chapter.url)
        }
        val directory = NovelBookArtifact.directoryFor(rootDirectory, novel.source, novel.id)
        val writer = NovelBookArtifactWriter(directory)
        val request = NovelBookBuildRequest(
            sourceId = novel.source,
            novelId = novel.id,
            novelTitle = novel.title,
            chapterSetHash = NovelBookArtifact.chapterSetHash(sourceChapters),
            builtAt = now(),
        )

        val previous = repository.getBookState(novel.id)
        val result = writer.build(
            request = request,
            chapters = sourceChapters,
            imageReferer = imageReferer(novel),
            loadHtml = { chapter ->
                loadHtml?.invoke(chapter)
                    ?: downloadManager.getDownloadedChapterText(novel, chapter.id)
            },
            onProgress = { done, total ->
                onProgress(
                    NovelBookBuildProgress(
                        phase = NovelBookBuildProgress.Phase.MERGING,
                        done = done,
                        total = total,
                    ),
                )
            },
        )

        if (result.index.chapters.isEmpty()) {
            // A build that compiled nothing (e.g. every chapter failed to load) must not destroy a
            // book that was already on disk: the previous artifact keeps working, and the caller is
            // left to report the failure. The writer has already left the old files untouched.
            return NovelBookBuildOutcome.NothingToBuild
        }

        val timestamp = now()
        val state = NovelBookState(
            novelId = novel.id,
            enabled = enableAfterBuild || previous?.enabled == true,
            bookVersion = (previous?.bookVersion ?: 0L) + 1L,
            sourceId = novel.source,
            chapterSetHash = result.meta.chapterSetHash,
            totalChars = result.meta.totalChars.toLong(),
            chapterCount = result.meta.chapterCount,
            // A rebuild rewrites the body file, so an old character offset can point into different
            // text. When the chapter set is unchanged the offset stays valid and the reading position
            // is preserved; otherwise the book starts at its beginning instead of resuming mid-way
            // through a chapter that no longer matches the stored position.
            charOffset = if (result.meta.chapterSetHash == previous?.chapterSetHash) {
                previous.charOffset
            } else {
                0L
            },
            blockIndex = if (result.meta.chapterSetHash == previous?.chapterSetHash) {
                previous.blockIndex
            } else {
                0
            },
            chapterCharOffset = if (result.meta.chapterSetHash == previous?.chapterSetHash) {
                previous.chapterCharOffset
            } else {
                0
            },
            lastChapterId = if (result.meta.chapterSetHash == previous?.chapterSetHash) {
                previous.lastChapterId
            } else {
                result.index.chapters.first().chapterId
            },
            // A position kept from an older row still has to be converted once; a position that was
            // just reset to the start of the book has nothing left to migrate.
            progressMigrated = if (result.meta.chapterSetHash == previous?.chapterSetHash) {
                previous.progressMigrated
            } else {
                true
            },
            complete = result.meta.complete,
            builtAt = result.meta.builtAt,
            updatedAt = timestamp,
        )
        repository.upsertBookState(state)

        logcat(LogPriority.INFO) {
            "Built novel book: novel=${novel.id}, chapters=${state.chapterCount}, " +
                "chars=${state.totalChars}, missing=${result.missingChapterIds.size}"
        }
        return NovelBookBuildOutcome.Built(state, result.missingChapterIds)
    }

    /**
     * Appends chapters that appeared after the last build, keeping every existing offset intact.
     *
     * A rebuild would invalidate the saved reading position, so new chapters are streamed to the end
     * of the body file and only the index, meta and chapter count change. The stored position and
     * the read-as-book flag are preserved.
     *
     * @param chapters the full chapter list in reading order; chapters already in the artifact are
     *   skipped, so only the tail is written.
     */
    suspend fun appendNewChapters(
        novel: Novel,
        chapters: List<NovelChapter>,
        downloadMissing: Boolean = false,
        onProgress: (NovelBookBuildProgress) -> Unit = {},
    ): NovelBookBuildOutcome {
        if (chapters.isEmpty()) return NovelBookBuildOutcome.NothingToBuild

        val directory = NovelBookArtifact.directoryFor(rootDirectory, novel.source, novel.id)
        val existingIndex = NovelBookArtifact.readIndex(directory)
        if (existingIndex == null || existingIndex.chapters.isEmpty()) {
            // Nothing to extend: fall back to a normal build so the button still does the obvious
            // thing when the artifact is missing.
            return build(
                novel = novel,
                chapters = chapters,
                downloadMissing = downloadMissing,
                enableAfterBuild = false,
                onProgress = onProgress,
            )
        }

        val known = existingIndex.chapters.map { entry -> entry.chapterId }.toSet()
        val newChapters = chapters.filterNot { chapter -> chapter.id in known }
        if (newChapters.isEmpty()) return NovelBookBuildOutcome.NothingToBuild

        val notDownloaded = newChapters.filterNot { downloadManager.isChapterDownloaded(novel, it.id) }
        if (notDownloaded.isNotEmpty()) {
            if (!downloadMissing) {
                return NovelBookBuildOutcome.MissingDownloads(notDownloaded)
            }
            notDownloaded.forEachIndexed { index, chapter ->
                coroutineContext.ensureActive()
                onProgress(
                    NovelBookBuildProgress(
                        phase = NovelBookBuildProgress.Phase.DOWNLOADING,
                        done = index,
                        total = notDownloaded.size,
                    ),
                )
                runCatching { downloadManager.downloadChapter(novel, chapter) }
                    .onFailure { error ->
                        logcat(LogPriority.WARN, error) {
                            "Book append could not download chapter ${chapter.id} of novel ${novel.id}"
                        }
                    }
            }
        }

        val sourceChapters = chapters.map { chapter ->
            NovelBookSourceChapter(id = chapter.id, name = chapter.name, url = chapter.url)
        }
        val newSourceChapters = newChapters.map { chapter ->
            NovelBookSourceChapter(id = chapter.id, name = chapter.name, url = chapter.url)
        }
        val previous = repository.getBookState(novel.id)
        val writer = NovelBookArtifactWriter(directory)
        val request = NovelBookBuildRequest(
            sourceId = novel.source,
            novelId = novel.id,
            novelTitle = novel.title,
            chapterSetHash = NovelBookArtifact.chapterSetHash(sourceChapters),
            builtAt = now(),
        )
        val result = writer.append(
            request = request,
            existing = existingIndex,
            newChapters = newSourceChapters,
            imageReferer = imageReferer(novel),
            loadHtml = { chapter -> downloadManager.getDownloadedChapterText(novel, chapter.id) },
            bookVersion = ((previous?.bookVersion ?: 1L) + 1L).toInt(),
            onProgress = { done, total ->
                onProgress(
                    NovelBookBuildProgress(
                        phase = NovelBookBuildProgress.Phase.MERGING,
                        done = done,
                        total = total,
                    ),
                )
            },
        )

        val timestamp = now()
        val state = NovelBookState(
            novelId = novel.id,
            enabled = previous?.enabled == true,
            bookVersion = (previous?.bookVersion ?: 1L) + 1L,
            sourceId = novel.source,
            chapterSetHash = result.meta.chapterSetHash,
            totalChars = result.meta.totalChars.toLong(),
            chapterCount = result.meta.chapterCount,
            charOffset = previous?.charOffset ?: 0L,
            blockIndex = previous?.blockIndex ?: 0,
            chapterCharOffset = previous?.chapterCharOffset ?: 0,
            // A row that never existed has nothing to migrate; an existing one keeps its flag so a
            // position stored by an older version is still converted on the next open.
            progressMigrated = previous?.progressMigrated ?: true,
            lastChapterId = previous?.lastChapterId ?: result.index.chapters.first().chapterId,
            complete = result.meta.complete,
            builtAt = result.meta.builtAt,
            updatedAt = timestamp,
        )
        repository.upsertBookState(state)

        logcat(LogPriority.INFO) {
            "Appended novel book chapters: novel=${novel.id}, added=${newChapters.size}, " +
                "chapters=${state.chapterCount}, chars=${state.totalChars}"
        }
        return NovelBookBuildOutcome.Built(state, result.missingChapterIds)
    }

    /**
     * Compiles the native block stream of an already built book when it does not have one yet.
     *
     * Books compiled before the native format existed keep working in HTML mode, so this is an
     * upgrade and never a precondition for reading. It reads the artifact's own body file, downloads
     * nothing, and leaves every character offset untouched, which is what keeps the saved reading
     * position valid across the upgrade.
     *
     * @return true when the book has an up to date native stream afterwards.
     */
    suspend fun ensureNativeStream(
        novel: Novel,
        onProgress: (NovelBookBuildProgress) -> Unit = {},
    ): Boolean {
        val directory = NovelBookArtifact.directoryFor(rootDirectory, novel.source, novel.id)
        if (!NovelBookNativeMigrator.needsMigration(directory)) {
            return NovelBookArtifact.nativeFile(directory).exists()
        }
        return withContext(Dispatchers.IO) {
            val scope = this
            runCatching {
                NovelBookNativeMigrator.migrate(
                    directory = directory,
                    imageReferer = imageReferer(novel),
                    onProgress = { done, total ->
                        onProgress(
                            NovelBookBuildProgress(
                                phase = NovelBookBuildProgress.Phase.PARSING,
                                done = done,
                                total = total,
                            ),
                        )
                    },
                    // Leaving a half written stream behind is harmless: the migrator writes to a
                    // temporary file and only swaps it in once every chapter is compiled.
                    isCancelled = { !scope.isActive },
                )
            }.onFailure { error ->
                logcat(LogPriority.WARN, error) {
                    "Native book stream migration failed for novel ${novel.id}"
                }
            }.getOrDefault(false)
        }
    }

    /** Drops the artifact and the stored state, returning the title to per-chapter reading. */
    suspend fun delete(novel: Novel) {
        NovelBookArtifact.delete(NovelBookArtifact.directoryFor(rootDirectory, novel.source, novel.id))
        repository.deleteBookState(novel.id)
    }

    /** Deletes the per-chapter downloads the book was built from, keeping the artifact. */
    fun deleteSourceChapters(novel: Novel, chapterIds: Collection<Long>) {
        if (chapterIds.isEmpty()) return
        downloadManager.deleteChapters(novel, chapterIds)
    }

    companion object {
        const val ROOT_DIRECTORY_NAME = "novel_books"

        /**
         * Site URL of the novel's source, or null for local books and unknown sources.
         *
         * Resolved lazily through Injekt so a builder can still be constructed in tests where
         * no source manager is registered.
         */
        fun defaultImageReferer(novel: Novel): String? = runCatching {
            (Injekt.get<NovelSourceManager>().get(novel.source) as? NovelSiteSource)?.siteUrl
        }.getOrNull()

        /** App-private location of the artifacts: they need real file offsets, so no SAF here. */
        fun defaultRootDirectory(): File {
            val filesDir = runCatching { Injekt.get<Application>().filesDir }.getOrNull()
            // Unit tests never register an Application (or register a relaxed mock without a files
            // dir), so fall back to the JVM temp dir instead of failing on a null parent.
            return if (filesDir != null) {
                File(filesDir, ROOT_DIRECTORY_NAME)
            } else {
                File(System.getProperty("java.io.tmpdir"), ROOT_DIRECTORY_NAME)
            }
        }
    }
}
