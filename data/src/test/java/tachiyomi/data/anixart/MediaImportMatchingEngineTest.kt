package tachiyomi.data.anixart

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class MediaImportMatchingEngineTest {

    @Test
    fun `matchRows returns summarized report`() = runBlocking {
        val rows = listOf("Naruto", "Unknown")
        val search: suspend (String) -> List<AnixartMatcher.SearchCandidate> = { query ->
            if (query == "Naruto") {
                listOf(
                    AnixartMatcher.SearchCandidate(
                        id = 1L,
                        sourceId = 1L,
                        displayTitle = "Naruto",
                        titles = listOf("Naruto"),
                        url = "u1",
                    ),
                )
            } else {
                emptyList()
            }
        }

        val (results, report) = MediaImportMatchingEngine.matchRows(
            rows = rows,
            toInput = { MediaImportMatchingEngine.RowInput(listOf(it), listOf(it)) },
            search = search,
            sourceNames = mapOf(1L to "Test Source"),
        )

        results.size shouldBe 2
        report.total shouldBe 2
        report.auto shouldBe 1
        report.noMatch shouldBe 1
        results.first().matchedSourceName shouldBe "Test Source"
    }
}
