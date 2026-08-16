package eu.kanade.domain.items.chapter.model

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.source.model.SChapter
import tachiyomi.domain.items.chapter.model.Chapter
import eu.kanade.tachiyomi.data.database.models.manga.Chapter as DbChapter

// TODO: Remove when all deps are migrated
fun Chapter.toSChapter(): SChapter {
    return SChapter.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload
        it.chapter_number = chapterNumber.toFloat()
        it.scanlator = scanlator
        // Source-owned context: 1.6 extensions store things like a rotating slug here and fail the
        // request when it comes back empty.
        it.memo = memo
    }
}

fun Chapter.copyFromSChapter(sChapter: SChapter): Chapter {
    return this.copy(
        name = sChapter.name,
        url = sChapter.url,
        dateUpload = sChapter.date_upload,
        chapterNumber = sChapter.chapter_number.toDouble(),
        scanlator = sChapter.scanlator?.ifBlank { null }?.trim(),
        // Keep whatever we already stored when the source sends nothing.
        memo = sChapter.memo.takeIf { it.isNotEmpty() } ?: memo,
    )
}

fun Chapter.toDbChapter(): DbChapter = ChapterImpl().also {
    it.id = id
    it.manga_id = mangaId
    it.url = url
    it.name = name
    it.scanlator = scanlator
    it.read = read
    it.bookmark = bookmark
    it.last_page_read = lastPageRead
    it.date_fetch = dateFetch
    it.date_upload = dateUpload
    it.chapter_number = chapterNumber.toFloat()
    it.source_order = sourceOrder.toInt()
    it.memo = memo
}
