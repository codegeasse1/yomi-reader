package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBlockAnchor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsNavigationAdapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsNavigationAnchor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsSegment
import java.util.Locale

/**
 * Follow-along script for the book document.
 *
 * The previous version searched the whole document for the spoken text: it walked every paragraph of
 * every resident section and compared normalized strings. That was O(document) per utterance, it
 * missed as soon as a translation replaced the text, and in a compiled artifact it never matched at
 * all. Blocks now carry their own address - `data-an-b="<chapterId>:<blockIndex>"`, written by
 * `annotateNovelBlockAnchors` - so the lookup is a single `querySelector`.
 *
 * When the exact block is not in the document (an older cached section, or a block the parser split
 * differently) the script falls back to the nearest earlier anchor of the same chapter, and finally
 * to the chapter's section. The voice then stays inside the right chapter instead of jumping to a
 * random paragraph that happened to share a substring.
 */
internal fun buildBookTtsAnchorSyncJavascript(
    anchorDomId: String?,
    chapterId: Long?,
    sectionIndex: Int?,
): String {
    val anchor = anchorDomId.orEmpty().replace("\\", "").replace("'", "")
    val chapterPrefix = chapterId?.let { "'$it:'" } ?: "null"
    val sectionSelector = sectionIndex
        ?.let { "'#" + bookSectionElementId(it) + "'" }
        ?: "null"
    return listOf(
        "(function() {",
        "  var anchor = '$anchor';",
        "  var chapterPrefix = $chapterPrefix;",
        "  var sectionSelector = $sectionSelector;",
        "  var previous = document.querySelectorAll('[data-an-tts-highlight]');",
        "  for (var i = 0; i < previous.length; i++) {",
        "    previous[i].removeAttribute('data-an-tts-highlight');",
        "  }",
        "  if (!anchor) { return 'cleared'; }",
        "  var target = document.querySelector('[data-an-b=\"' + anchor + '\"]');",
        "  if (!target && chapterPrefix) {",
        "    var wanted = parseInt(anchor.split(':')[1], 10);",
        "    var siblings = document.querySelectorAll('[data-an-b^=\"' + chapterPrefix + '\"]');",
        "    for (var j = 0; j < siblings.length; j++) {",
        "      var index = parseInt((siblings[j].getAttribute('data-an-b') || '').split(':')[1], 10);",
        "      if (!isNaN(index) && index <= wanted) { target = siblings[j]; }",
        "    }",
        "  }",
        "  if (!target && sectionSelector) { target = document.querySelector(sectionSelector); }",
        "  if (!target) { return 'not-found'; }",
        "  target.setAttribute('data-an-tts-highlight', 'true');",
        "  var engine = window.__anBookEngine;",
        "  var viewport = document.getElementById('an-book-viewport');",
        "  if (viewport && (!engine || typeof engine.scrollBy !== 'function')) {",
        "    var offset = target.getBoundingClientRect().top - viewport.getBoundingClientRect().top;",
        "    viewport.scrollTop = Math.max(0, viewport.scrollTop + offset - (viewport.clientHeight / 3));",
        "    return 'scrolled';",
        "  }",
        "  if (viewport) {",
        "    var delta = target.getBoundingClientRect().top - viewport.getBoundingClientRect().top -",
        "      (viewport.clientHeight / 3);",
        "    if (Math.abs(delta) > 4) { engine.scrollBy(Math.round(delta)); }",
        "    return 'scrolled';",
        "  }",
        "  target.scrollIntoView({ block: 'center' });",
        "  return 'scrolled';",
        "})()",
    ).joinToString("\n")
}

/**
 * Whether the anchor script found its target.
 *
 * `evaluateJavascript` hands the returned string back quoted and escaped, so `"scrolled"` arrives as
 * `"\"scrolled\""`. Only `not-found` and a missing answer (script never ran, document torn down)
 * mean the anchor still has to be replayed.
 */
internal fun isBookTtsSyncApplied(rawResult: String?): Boolean {
    val value = rawResult?.trim()?.trim('"')?.lowercase(Locale.US) ?: return false
    return value == "scrolled" || value == "cleared"
}

/**
 * [NovelTtsNavigationAdapter] for book mode.
 *
 * Book mode used to fall through to the native-scroll adapter, which drives the per-chapter lazy
 * list. That list is empty over a book, so "follow along with the voice" moved nothing at all.
 *
 * Both book renderers are served here, by the same address. The WebView renderer receives the
 * anchor script above; the native renderer scrolls its list to the section that holds the chapter
 * and publishes the anchor through [onNativeAnchor], which is what paints the highlight on the
 * block itself. Nothing is matched by text, so a translated chapter follows along exactly like an
 * untranslated one.
 */
internal class BookTtsNavigationAdapter(
    private val surface: () -> NovelBookScrollSurface?,
    private val sectionIndexForChapter: (Long) -> Int?,
    /** Scrolls the native book list to the item rendering [sectionIndex]; false when not mounted. */
    private val scrollNativeToSection: suspend (Int) -> Boolean = { false },
    /** Publishes the block the voice is reading, for the native renderer's highlight. */
    private val onNativeAnchor: (NovelBlockAnchor?) -> Unit = {},
    private val isNativeRenderer: () -> Boolean = { false },
    /** True once the native renderer has laid the block out, i.e. the anchor really landed. */
    private val isNativeBlockLaidOut: (NovelBlockAnchor) -> Boolean = { false },
) : NovelTtsNavigationAdapter {

    /** Last block the voice reached, so a manual anchor is a real position instead of an empty one. */
    private var lastAnchor: NovelBlockAnchor? = null

    /**
     * Anchor that was requested but not located yet.
     *
     * Navigation used to be a single shot per utterance: if the section holding the block was not
     * mounted yet - the usual case right after a chapter border, or when playback starts before the
     * resident window is filled - the script answered `not-found`, the native list had no item for
     * the section, and nothing ever asked again. The request is kept here instead and replayed by
     * [retryPendingAnchor] whenever the document, the spine or the native entries change.
     */
    private var pendingAnchor: NovelBlockAnchor? = null

    /** Block the voice is on, whether or not it could be shown yet. */
    val requestedAnchor: NovelBlockAnchor? get() = lastAnchor

    /** Block that is still waiting for its section to mount, or null when everything landed. */
    val unresolvedAnchor: NovelBlockAnchor? get() = pendingAnchor

    override suspend fun syncToSegment(segment: NovelTtsSegment) {
        val anchor = NovelBlockAnchor(chapterId = segment.chapterId, blockIndex = segment.blockIndex)
        applyAnchor(anchor)
    }

    /**
     * Re-applies the anchor that could not be shown yet.
     *
     * Called when something that can make it resolvable changed: a new book document, a new spine,
     * new native entries. Does nothing once the anchor landed, so this is cheap to call often.
     */
    suspend fun retryPendingAnchor() {
        val anchor = pendingAnchor ?: return
        applyAnchor(anchor)
    }

    override fun captureManualAnchor(
        pageIndex: Int?,
        blockIndex: Int?,
        scrollOffsetPx: Int,
    ): NovelTtsNavigationAnchor {
        // The book is one continuous document, so a page index means nothing here. The block the
        // voice last reached does: it is the pair the book itself addresses positions by, and it is
        // what lets restorePosition put the reader back instead of only clearing the highlight.
        val anchor = lastAnchor
        return NovelTtsNavigationAnchor(
            chapterId = anchor?.chapterId,
            blockIndex = blockIndex ?: anchor?.blockIndex,
            scrollOffsetPx = scrollOffsetPx,
        )
    }

    override suspend fun restorePosition(anchor: NovelTtsNavigationAnchor) {
        val chapterId = anchor.chapterId
        val blockIndex = anchor.blockIndex
        if (chapterId == null || blockIndex == null) {
            clearHighlight()
            return
        }
        applyAnchor(NovelBlockAnchor(chapterId = chapterId, blockIndex = blockIndex))
    }

    private suspend fun applyAnchor(anchor: NovelBlockAnchor) {
        lastAnchor = anchor
        val sectionIndex = sectionIndexForChapter(anchor.chapterId)
        if (isNativeRenderer()) {
            // The highlight is published first: it only needs the address, not a mounted section,
            // and publishing it late is what made the block flash in after the scroll.
            onNativeAnchor(anchor)
            val scrolled = sectionIndex != null && scrollNativeToSection(sectionIndex)
            pendingAnchor = if (scrolled || isNativeBlockLaidOut(anchor)) null else anchor
            return
        }
        val target = surface()
        if (target == null) {
            // The book document is not mounted yet; the retry above picks this up.
            pendingAnchor = anchor
            return
        }
        pendingAnchor = anchor
        target.evaluate(
            buildBookTtsAnchorSyncJavascript(
                anchorDomId = anchor.domId,
                chapterId = anchor.chapterId,
                sectionIndex = sectionIndex,
            ),
        ) { result ->
            if (isBookTtsSyncApplied(result) && pendingAnchor == anchor) {
                pendingAnchor = null
            }
        }
    }

    private fun clearHighlight() {
        pendingAnchor = null
        onNativeAnchor(null)
        surface()?.evaluate(
            buildBookTtsAnchorSyncJavascript(anchorDomId = null, chapterId = null, sectionIndex = null),
        )
    }
}
