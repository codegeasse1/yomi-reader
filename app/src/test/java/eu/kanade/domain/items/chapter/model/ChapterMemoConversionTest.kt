package eu.kanade.domain.items.chapter.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter

/**
 * The reader hands the db chapter model straight to Source.getPageList, and a 1.6 source reads its
 * own context (e.g. a rotating slug) out of `memo`. Both conversions used to drop it - the SChapter
 * interface default is a no-op setter - which made the source throw on every chapter open.
 */
class ChapterMemoConversionTest {

    private val memo = JsonObject(mapOf("mangaSlug" to JsonPrimitive("abc-123")))

    private val chapter = Chapter.create().copy(
        id = 5L,
        mangaId = 1L,
        url = "/chapter/1",
        name = "Chapter 1",
        memo = memo,
    )

    @Test
    fun `the reader db model keeps the memo`() {
        chapter.toDbChapter().memo shouldBe memo
    }

    @Test
    fun `the source model keeps the memo`() {
        chapter.toSChapter().memo shouldBe memo
    }
}
