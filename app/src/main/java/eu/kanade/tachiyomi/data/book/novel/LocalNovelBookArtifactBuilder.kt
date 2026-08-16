package eu.kanade.tachiyomi.data.book.novel

import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.book.novel.repository.NovelBookStateRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.source.local.entries.novel.LocalNovelSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Compiles a local book (.epub / .fb2) into a [NovelBookArtifact].
 *
 * Local files are finished books: every "chapter" is just a spine entry or an FB2 section of one
 * file, so there is nothing to download and nothing to append later. Merging them into the same
 * artifact the network novels use gives local books the seamless reading experience (continuous
 * text, exact progress, no half-empty pages between chapters) instead of the old per-fragment
 * slicing, and keeps a single reading engine in the reader.
 */
class LocalNovelBookArtifactBuilder(
    private val bookBuilder: NovelBookBuilder = NovelBookBuilder(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val repository: NovelBookStateRepository = Injekt.get(),
) {

    /** True when [novel] comes from the local source and can be compiled from its own file. */
    fun isLocalBook(novel: Novel): Boolean = novel.source == LocalNovelSource.ID

    /**
     * Builds the artifact of a local [novel] if it does not exist yet or if its chapter set changed.
     *
     * This is the lazy migration entry point: previously imported books are compiled the first time
     * they are opened, so no separate migration pass over the library is needed.
     *
     * @return true when an artifact is available for the title afterwards.
     */
    suspend fun ensureArtifact(
        novel: Novel,
        chapters: List<NovelChapter>,
        onProgress: (NovelBookBuildProgress) -> Unit = {},
    ): Boolean {
        if (!isLocalBook(novel) || chapters.isEmpty()) return false

        val source = sourceManager.get(novel.source) as? LocalNovelSource ?: return false
        val directory = NovelBookArtifact.directoryFor(
            root = NovelBookBuilder.defaultRootDirectory(),
            sourceId = novel.source,
            novelId = novel.id,
        )

        val sourceChapters = chapters.map { chapter ->
            NovelBookSourceChapter(id = chapter.id, name = chapter.name, url = chapter.url)
        }
        val expectedHash = NovelBookArtifact.chapterSetHash(sourceChapters)
        val currentMeta = NovelBookArtifact.readMeta(directory)?.takeIf {
            NovelBookArtifact.exists(directory)
        }
        if (currentMeta != null && currentMeta.chapterSetHash == expectedHash) {
            // The artifact is current, but it may predate the native block stream. Upgrading it
            // here is the same lazy migration this class already does for the artifact itself,
            // so an imported book opened once keeps opening instantly afterwards.
            bookBuilder.ensureNativeStream(novel, onProgress)
            return true
        }

        val outcome = bookBuilder.build(
            novel = novel,
            chapters = chapters,
            downloadMissing = false,
            enableAfterBuild = true,
            loadHtml = { chapter ->
                runCatching {
                    val html = source.readChapterText(
                        SNovelChapter.create().apply { url = chapter.url },
                    )
                    // Illustrations arrive as inline base64 data URIs; store them next to the
                    // artifact so the merged body stays text and the images work offline.
                    NovelBookImageExtractor.externalize(html, directory)
                }.getOrNull()
            },
            onProgress = onProgress,
        )

        return when (outcome) {
            is NovelBookBuildOutcome.Built -> {
                // A local file is finite and has no network source, so the book is always complete:
                // this keeps the "+N new chapters" badge and the append button off local titles.
                if (!outcome.state.complete) {
                    repository.upsertBookState(outcome.state.copy(complete = true))
                }
                true
            }
            is NovelBookBuildOutcome.MissingDownloads -> false
            NovelBookBuildOutcome.NothingToBuild -> {
                logcat(LogPriority.WARN) { "Local book produced no artifact: novel=${novel.id}" }
                false
            }
        }
    }
}
