package eu.kanade.tachiyomi.ui.reader.model

sealed class ChapterTransition {

    abstract val from: ReaderChapter
    abstract val to: ReaderChapter?
    abstract val showInfo: Boolean

    class Prev(
        override val from: ReaderChapter,
        override val to: ReaderChapter?,
        override val showInfo: Boolean = true,
    ) : ChapterTransition()

    class Next(
        override val from: ReaderChapter,
        override val to: ReaderChapter?,
        override val showInfo: Boolean = true,
    ) : ChapterTransition()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChapterTransition) return false
        if (showInfo != other.showInfo) return false
        if (from == other.from && to == other.to) return true
        if (from == other.to && to == other.from) return true
        return false
    }

    override fun hashCode(): Int {
        var result = from.hashCode() + (to?.hashCode() ?: 0)
        result = 31 * result + showInfo.hashCode()
        return result
    }

    override fun toString(): String {
        return "${javaClass.simpleName}(from=${from.chapter.url}, to=${to?.chapter?.url}, showInfo=$showInfo)"
    }
}

internal fun shouldShowChapterTransitionInfo(
    alwaysShowChapterTransition: Boolean,
    hasMissingChapters: Boolean,
    destinationChapter: ReaderChapter?,
): Boolean {
    return alwaysShowChapterTransition || hasMissingChapters || destinationChapter == null
}

internal fun shouldShowChapterTransitionLoading(
    showInfo: Boolean,
    state: ReaderChapter.State,
): Boolean {
    return state is ReaderChapter.State.Loading ||
        (!showInfo && (state is ReaderChapter.State.Wait || state is ReaderChapter.State.Loaded))
}
