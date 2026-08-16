package eu.kanade.tachiyomi.data.book.novel

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * A single chapter normalized into a continuous piece of the merged book.
 *
 * [html] is a self contained `<section>` where every top level block carries its absolute plain
 * text offset inside the book, so a scroll position maps to an exact offset without measuring text
 * at runtime.
 */
data class NovelBookNormalizedSection(
    val html: String,
    val charCount: Int,
    val blockCount: Int,
)

/**
 * Turns raw chapter HTML into a gap free section of the merged book artifact.
 *
 * Rules that matter for continuous whole book reading:
 * - the chapter title is always baked in (hidden with CSS when the user turns headings off), so
 *   toggling that setting can never shift offsets and invalidate a saved reading position;
 * - empty paragraphs and leading or trailing line breaks are dropped, so a chapter never ends with
 *   blank space before the next one starts;
 * - a title the source duplicated inside the chapter body is removed;
 * - `<br>` runs act as paragraph separators instead of vertical padding;
 * - every block carries [OFFSET_ATTR] (absolute offset) and [LENGTH_ATTR] (plain text length), and
 *   non textual blocks such as images or rules count as one character so offsets strictly increase.
 */
object NovelBookChapterNormalizer {

    const val CHAPTER_CLASS = "nb-chapter"
    const val TITLE_CLASS = "nb-title"
    const val OFFSET_ATTR = "data-o"
    const val LENGTH_ATTR = "data-l"
    const val CHAPTER_ID_ATTR = "data-cid"
    const val CHAPTER_START_ATTR = "data-start"
    const val CHAPTER_LENGTH_ATTR = "data-len"

    private const val MAX_UNWRAP_DEPTH = 12

    private val REMOVED_SELECTOR = "script, style, iframe, svg, canvas, object, embed, form, " +
        "input, button, select, textarea, noscript, meta, link"

    private val IMAGE_URL_ATTRIBUTES = listOf(
        "src",
        "srcset",
        "data-src",
        "data-srcset",
        "data-original",
        "data-lazy-src",
        "data-url",
    )

    /**
     * Rewrites every image URL attribute to an absolute URL resolved against [chapterWebUrl].
     *
     * Absolute `http(s)://`, `data:` and `file:` values are left untouched; `//host/...` and
     * relative paths are resolved the same way a browser would resolve them against the chapter
     * page. The native compiler reads the very same attributes, so both the WebView document and
     * the pre-compiled blocks end up with absolute URLs.
     */
    private fun absolutizeImageUrls(document: Document, chapterWebUrl: String) {
        val base = chapterWebUrl.trim().toHttpUrlOrNull() ?: return
        document.select("img, picture source").forEach { element ->
            IMAGE_URL_ATTRIBUTES.forEach { attribute ->
                if (!element.hasAttr(attribute)) return@forEach
                val raw = element.attr(attribute)
                val resolved = if (attribute == "srcset") {
                    rewriteSrcSet(base, raw)
                } else {
                    resolveImageUrl(base, raw)
                }
                if (resolved != null) {
                    element.attr(attribute, resolved)
                }
            }
        }
    }

    private fun rewriteSrcSet(base: HttpUrl, raw: String): String? {
        val candidates = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return null
        val rewritten = candidates.map { candidate ->
            val url = candidate.substringBefore(' ')
            val descriptor = candidate.substring(url.length).trim()
            val absolute = resolveImageUrl(base, url) ?: return null
            if (descriptor.isEmpty()) absolute else "$absolute $descriptor"
        }
        return rewritten.joinToString(", ")
    }

    private fun resolveImageUrl(base: HttpUrl, raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (
            trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("data:") ||
            trimmed.startsWith("file:") ||
            trimmed.startsWith("blob:")
        ) {
            return trimmed
        }
        return base.resolve(trimmed)?.toString()
    }

    private val BLOCK_TAGS = setOf(
        "p",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "blockquote",
        "pre",
        "ul",
        "ol",
        "dl",
        "table",
        "figure",
        "hr",
        "img",
    )

    private val CONTAINER_TAGS = setOf(
        "div",
        "section",
        "article",
        "main",
        "aside",
        "header",
        "footer",
        "center",
        "font",
    )

    private val BLOCK_SELECTOR = BLOCK_TAGS.joinToString(", ")

    /** Stable anchor of a chapter inside the book document. */
    fun chapterAnchorId(chapterId: Long): String = "nb-ch-$chapterId"

    /**
     * Normalizes [rawHtml] into a book section starting at [startOffset] in the book offset domain.
     */
    fun normalize(
        rawHtml: String,
        chapterId: Long,
        chapterName: String,
        startOffset: Int,
        chapterWebUrl: String? = null,
    ): NovelBookNormalizedSection {
        val document = Jsoup.parseBodyFragment(rawHtml)
        document.outputSettings().prettyPrint(false)
        document.select(REMOVED_SELECTOR).remove()
        // Sources that reference images by relative paths would break the merged book: a section can
        // span chapters (no single base URL for the WebView) and the native blocks are compiled
        // without any base at all. Resolve them against the chapter URL so the artifact always
        // carries absolute image URLs.
        if (chapterWebUrl != null) {
            absolutizeImageUrls(document, chapterWebUrl)
        }

        val title = chapterName.trim()
        val blocks = dropDuplicatedTitle(collectBlocks(document.body(), depth = 0), title)

        val section = Element("section")
            .addClass(CHAPTER_CLASS)
            .attr("id", chapterAnchorId(chapterId))
            .attr(CHAPTER_ID_ATTR, chapterId.toString())

        var offset = startOffset
        offset = appendBlock(section, Element("h2").addClass(TITLE_CLASS).text(title), offset)
        blocks.forEach { block -> offset = appendBlock(section, block, offset) }

        val charCount = offset - startOffset
        section.attr(CHAPTER_START_ATTR, startOffset.toString())
        section.attr(CHAPTER_LENGTH_ATTR, charCount.toString())
        return NovelBookNormalizedSection(
            html = section.outerHtml(),
            charCount = charCount,
            blockCount = section.children().size,
        )
    }

    private fun appendBlock(section: Element, block: Element, offset: Int): Int {
        trimEdgeBreaks(block)
        val length = block.text().length.coerceAtLeast(1)
        block.attr(OFFSET_ATTR, offset.toString())
        block.attr(LENGTH_ATTR, length.toString())
        section.appendChild(block)
        return offset + length
    }

    private fun collectBlocks(parent: Element, depth: Int): List<Element> {
        val blocks = mutableListOf<Element>()
        val pending = mutableListOf<Node>()

        fun flushPending() {
            if (pending.isEmpty()) return
            val paragraph = Element("p")
            pending.forEach { node -> paragraph.appendChild(node.clone()) }
            pending.clear()
            if (!isBlankBlock(paragraph)) {
                blocks.add(paragraph)
            }
        }

        parent.childNodes().toList().forEach { node ->
            when (node) {
                is TextNode -> if (node.text().isNotBlank()) pending.add(node)
                is Element -> {
                    val tag = node.tagName().lowercase()
                    when {
                        tag == "br" -> flushPending()
                        tag in BLOCK_TAGS -> {
                            flushPending()
                            if (!isBlankBlock(node)) {
                                blocks.add(node.clone())
                            }
                        }
                        depth < MAX_UNWRAP_DEPTH && (tag in CONTAINER_TAGS || hasBlockChild(node)) -> {
                            flushPending()
                            blocks.addAll(collectBlocks(node, depth + 1))
                        }
                        else -> pending.add(node)
                    }
                }
                else -> Unit
            }
        }
        flushPending()
        return blocks
    }

    private fun hasBlockChild(element: Element): Boolean = element.select(BLOCK_SELECTOR).isNotEmpty()

    private fun isBlankBlock(element: Element): Boolean {
        if (element.tagName().equals("hr", ignoreCase = true)) return false
        // A bare <img> is a block of its own: `select("img")` below only finds descendants, so an
        // image at the top level of a chapter would otherwise be dropped as "blank".
        if (element.tagName().equals("img", ignoreCase = true)) return false
        if (element.select("img").isNotEmpty()) return false
        return element.text().isBlank()
    }

    private fun trimEdgeBreaks(block: Element) {
        while (true) {
            val first = block.childNodes().firstOrNull() ?: break
            if (!isTrimmable(first)) break
            first.remove()
        }
        while (true) {
            val last = block.childNodes().lastOrNull() ?: break
            if (!isTrimmable(last)) break
            last.remove()
        }
    }

    private fun isTrimmable(node: Node): Boolean = when (node) {
        is Element -> node.tagName().equals("br", ignoreCase = true)
        is TextNode -> node.text().isBlank()
        else -> false
    }

    private fun dropDuplicatedTitle(blocks: List<Element>, title: String): List<Element> {
        if (title.isEmpty() || blocks.isEmpty()) return blocks
        val target = comparable(title)
        if (target.isEmpty()) return blocks
        val limit = minOf(2, blocks.size)
        val duplicateIndex = (0 until limit).firstOrNull { position ->
            val block = blocks[position]
            val tag = block.tagName().lowercase()
            val headingLike = tag == "p" || (tag.length == 2 && tag.startsWith("h") && tag[1].isDigit())
            headingLike && comparable(block.text()) == target
        } ?: return blocks
        return blocks.filterIndexed { position, _ -> position != duplicateIndex }
    }

    private fun comparable(text: String): String = text
        .lowercase()
        .filter { it.isLetterOrDigit() || it.isWhitespace() }
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}
