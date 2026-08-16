package tachiyomi.data.shikimori

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShikimoriImportEntryTest {

    @Test
    fun `searchQueries include raw titles before cleaned variants`() {
        val entry = ShikimoriImportEntry(
            mediaType = ShikimoriImportMediaType.MANGA,
            rateId = 1L,
            remoteId = 2L,
            name = "Sousou no Frieren",
            russian = "Провожающая в последний путь Фрирен",
            status = "reading",
            score = 0,
            progress = 1,
            totalCount = null,
            thumbnailUrl = null,
        )

        val queries = entry.searchQueries()

        queries.first() shouldBe "Sousou no Frieren"
        queries.shouldContain("Провожающая в последний путь Фрирен")
        queries.size shouldBe queries.distinctBy { it.lowercase() }.size
    }
}
