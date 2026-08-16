package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.presentation.reader.novel.buildBookSectionHtml
import eu.kanade.tachiyomi.data.book.novel.NovelBookChapterNormalizer
import eu.kanade.tachiyomi.ui.reader.novel.replace.applyReplaceRulesToHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** One chapter's raw payload, before it gets normalized into reader-ready HTML. */
internal data class NovelBookRawSection(
    val chapterId: Long,
    val chapterName: String,
    val rawHtml: String,
    val chapterWebUrl: String? = null,
)

/** Reader-ready markup of one section plus the base URL its links resolve against. */
internal data class BookSectionContent(
    val html: String,
    val baseUrl: String? = null,
)

/**
 * One chapter's slice of a section.
 *
 * Translation maps are indexed per chapter, so a section that covers two chapters has to be split
 * before the maps can be applied: a single map over the whole section would shift every index past
 * the chapter boundary.
 */
internal data class BookChapterFragment(
    val chapterId: Long,
    val html: String,
)

/**
 * Version of the transformation chain below.
 *
 * It is part of [BookSectionRepository.transformSignature] and therefore of the disk cache key, so
 * changing the pipeline invalidates previously prepared sections instead of serving markup that was
 * built by an older version of the chain.
 */
private const val BOOK_SECTION_PIPELINE_VERSION = "p2"
// p2: blocks carry `data-an-b` anchors. Sections prepared by p1 have none, so TTS follow-along and
// the highlight would silently do nothing over every already-cached book until it was re-read.

/**
 * Single owner of "raw chapter payload in, reader-ready section HTML out".
 *
 * Book mode used to build section markup in two unrelated places: the section pipeline for
 * chapter-fed spines and [NovelBookReaderController.loadBookEngineDocument] for compiled artifacts.
 * The two applied different steps, which is why an artifact section could be shown untranslated
 * while the very same chapter was translated in the other path. Every transformation now lives
 * here, in one fixed order:
 *
 * 1. structured payload normalization,
 * 2. chapter heading,
 * 3. reader sanitizing,
 * 4. translation overlay (always per chapter),
 * 5. section wrapper.
 *
 * Steps 1-3 and 5 do not apply to a compiled artifact: its body was normalized at build time, so
 * only the translation overlay runs there.
 */
internal interface BookSectionRepository {

    /**
     * Identity of everything that influences the produced markup.
     *
     * Prepared sections are cached on disk, so a cached entry may only be reused when it was built
     * by the same chain with the same settings and the same visible translation.
     */
    fun transformSignature(): String

    /** Builds the section markup of a chapter-fed spine section. */
    suspend fun prepareChapterSection(section: NovelBookSection): BookSectionContent

    /**
     * Applies the translation overlay to a compiled-artifact section.
     *
     * [chapterIds] are the chapters the section covers, in reading order; they are only a fallback
     * for content that carries no chapter marker of its own.
     */
    suspend fun applyArtifactTranslations(html: String, chapterIds: List<Long>): String
}

/**
 * Production [BookSectionRepository].
 *
 * Every dependency is a lambda, so the whole chain stays free of Android and of the screen model and
 * can be exercised in unit tests.
 */
internal class DefaultBookSectionRepository(
    private val loadRawSection: suspend (Long) -> NovelBookRawSection,
    /** Overlays the translation of one chapter onto that chapter's markup. */
    private val translateChapterHtml: suspend (Long, String) -> String,
    /** Applies user text-replacement rules to sanitized chapter markup. */
    private val replaceTextHtml: (String) -> String = { it },
    /** Identity of the user's replacement rules, folded into the disk cache key. */
    private val replaceRulesFingerprint: () -> String = { "" },
    private val showChapterHeadings: () -> Boolean = { true },
    /** Visible translation variant, e.g. `gemini`, `google` or `raw`. */
    private val translationVariant: () -> String = { "raw" },
) : BookSectionRepository {

    override fun transformSignature(): String {
        val headings = if (showChapterHeadings()) "h1" else "h0"
        val base = "$BOOK_SECTION_PIPELINE_VERSION-$headings-${translationVariant()}"
        val rules = replaceRulesFingerprint()
        return if (rules.isBlank()) base else "$base-r$rules"
    }

    override suspend fun prepareChapterSection(section: NovelBookSection): BookSectionContent {
        val raw = loadRawSection(section.chapterId)
        val bodyHtml = withContext(Dispatchers.Default) {
            val withHeading = prependChapterHeadingIfMissing(
                rawHtml = raw.rawHtml.normalizeStructuredChapterPayload(),
                chapterName = raw.chapterName.ifBlank { section.name },
            )
            val sanitized = sanitizeChapterHtmlForReader(withHeading)
            if (sanitized.isBlank()) withHeading else replaceTextHtml(sanitized)
        }
        if (bodyHtml.isBlank()) {
            return BookSectionContent(html = "", baseUrl = raw.chapterWebUrl?.takeIf { it.isNotBlank() })
        }
        val translated = translateChapterHtml(raw.chapterId, bodyHtml)
        // Block anchors are written last, over exactly the markup the reader will see: TTS
        // follow-along addresses `(chapterId, blockIndex)` and a translation that changed the block
        // structure must not shift those indices.
        val anchored = withContext(Dispatchers.Default) {
            annotateNovelBlockAnchors(rawHtml = translated, chapterId = raw.chapterId)
        }
        return BookSectionContent(
            html = buildBookSectionHtml(
                sectionIndex = section.index,
                chapterId = section.chapterId,
                title = section.name,
                bodyHtml = anchored,
                showDivider = section.index > 0,
                showHeading = showChapterHeadings(),
            ),
            baseUrl = raw.chapterWebUrl?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun applyArtifactTranslations(html: String, chapterIds: List<Long>): String {
        if (html.isBlank()) return html
        val fragments = withContext(Dispatchers.Default) {
            splitArtifactSectionByChapter(html = html, fallbackChapterIds = chapterIds)
        }
        if (fragments.isEmpty()) return html
        val translated = fragments.map { fragment ->
            if (fragment.chapterId == BookLocator.NO_CHAPTER_ID) {
                fragment.html
            } else {
                val result = translateChapterHtml(fragment.chapterId, fragment.html)
                // The artifact was compiled without block anchors, so they are added per chapter
                // here, after the overlay: this is the only path a compiled book takes, and without
                // it TTS follow-along has nothing to address in an artifact section.
                withContext(Dispatchers.Default) {
                    annotateNovelBlockAnchors(rawHtml = result, chapterId = fragment.chapterId)
                }
            }
        }
        return translated.joinToString(separator = "")
    }
}

/**
 * Splits a compiled-artifact section into its per-chapter fragments.
 *
 * Blocks are aligned to chapter boundaries, so every chapter inside a section is a whole
 * `section.nb-chapter` element carrying its own chapter id. Content without such a marker (older
 * artifacts) is attributed to the section's first chapter, and stays untranslated when the section
 * has no known chapter at all.
 */
internal fun splitArtifactSectionByChapter(
    html: String,
    fallbackChapterIds: List<Long>,
): List<BookChapterFragment> {
    if (html.isBlank()) return emptyList()
    val fallbackChapterId = fallbackChapterIds.firstOrNull() ?: BookLocator.NO_CHAPTER_ID
    return runCatching {
        val document = Jsoup.parseBodyFragment(html)
        document.outputSettings().prettyPrint(false)
        val children = document.body().children()
        if (children.isEmpty()) return@runCatching emptyList()
        val fragments = mutableListOf<BookChapterFragment>()
        val pending = StringBuilder()
        var pendingChapterId = fallbackChapterId
        fun flush() {
            if (pending.isEmpty()) return
            fragments += BookChapterFragment(chapterId = pendingChapterId, html = pending.toString())
            pending.clear()
        }
        children.forEach { element ->
            val chapterId = element.chapterMarkerId()
            if (chapterId != null) {
                flush()
                pendingChapterId = chapterId
            }
            pending.append(element.outerHtml())
        }
        flush()
        fragments.toList()
    }.getOrElse {
        listOf(BookChapterFragment(chapterId = fallbackChapterId, html = html))
    }
}

/** Chapter id an artifact element announces, or null when it is not a chapter marker. */
private fun Element.chapterMarkerId(): Long? {
    val attribute = attr(NovelBookChapterNormalizer.CHAPTER_ID_ATTR).toLongOrNull()
    if (attribute != null) return attribute
    val id = id()
    if (!id.startsWith(CHAPTER_ANCHOR_PREFIX)) return null
    return id.removePrefix(CHAPTER_ANCHOR_PREFIX).toLongOrNull()
}

/** Prefix of [NovelBookChapterNormalizer.chapterAnchorId], kept in one place for the parser above. */
private val CHAPTER_ANCHOR_PREFIX = NovelBookChapterNormalizer.chapterAnchorId(0L).removeSuffix("0")
