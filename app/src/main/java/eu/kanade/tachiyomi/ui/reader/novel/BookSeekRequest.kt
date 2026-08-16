package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Why the core asked the renderer to move.
 *
 * The reason is not cosmetic: the resume indicator is switched off by the *application* of a
 * [Resume] request, so the reader no longer has to guess with a timer whether the saved position
 * was reached.
 */
enum class BookSeekReason {
    Resume,
    Seekbar,
    TableOfContents,
    Tts,
    Search,
}

/**
 * An explicit, one-shot instruction to move the book renderer.
 *
 * Book mode used to feed the current position back into the renderer as a reactive input, so every
 * position the renderer reported came straight back down and had to be told apart from a real jump
 * by echo windows, char tolerances and three timer guards. The renderer now owns the position and
 * only ever moves on a request like this one, identified by a monotonic [id]: a request whose id is
 * not greater than the last applied one is ignored, which is the whole de-duplication rule.
 *
 * [locator] is the durable identity of the target (chapter + offset inside it) and [location] is the
 * spine coordinate the engine consumes. Both are produced by one conversion in the book controller,
 * so they cannot drift apart.
 */
data class BookSeekRequest(
    val id: Long,
    val locator: BookLocator,
    val location: NovelBookLocation,
    val reason: BookSeekReason,
)

/** True when [request] is a move the renderer has not applied yet. */
internal fun shouldApplyBookSeek(request: BookSeekRequest?, lastAppliedSeekId: Long): Boolean =
    request != null && request.id > lastAppliedSeekId
