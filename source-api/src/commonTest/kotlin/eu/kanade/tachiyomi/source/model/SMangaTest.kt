package eu.kanade.tachiyomi.source.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class SMangaTest {

    @Test
    fun `getGenres splits by multiple delimiters and trims values`() {
        val manga = SManga.create().apply {
            genre = " Action;Drama|Fantasy/School,Romance\nMystery  "
        }

        manga.getGenres() shouldBe listOf(
            "Action",
            "Drama",
            "Fantasy",
            "School",
            "Romance",
            "Mystery",
        )
    }

    @Test
    fun `getGenres removes duplicates case-insensitively and empty values`() {
        val manga = SManga.create().apply {
            genre = "Action, action,  ;, ACTION |  "
        }

        manga.getGenres() shouldBe listOf("Action")
    }
}
