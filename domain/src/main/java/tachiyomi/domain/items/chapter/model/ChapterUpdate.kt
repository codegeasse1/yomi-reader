package tachiyomi.domain.items.chapter.model

import kotlinx.serialization.json.JsonObject

data class ChapterUpdate(
    val id: Long,
    val mangaId: Long? = null,
    val read: Boolean? = null,
    val bookmark: Boolean? = null,
    val lastPageRead: Long? = null,
    val dateFetch: Long? = null,
    val sourceOrder: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val dateUpload: Long? = null,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
    val version: Long? = null,
    /** Source-owned context (1.6 extensions keep e.g. a rotating slug here). */
    val memo: JsonObject? = null,
)

fun Chapter.toChapterUpdate(): ChapterUpdate {
    return ChapterUpdate(
        id,
        mangaId,
        read,
        bookmark,
        lastPageRead,
        dateFetch,
        sourceOrder,
        url,
        name,
        dateUpload,
        chapterNumber,
        scanlator,
        version,
        // An empty memo never overwrites a stored one: chapters built from scratch elsewhere would
        // otherwise wipe the context the source gave us.
        memo.takeIf { it.isNotEmpty() },
    )
}
