package eu.kanade.tachiyomi.animesource.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class SAnimeTest {

    @Test
    fun `getGenres splits by multiple delimiters and trims values`() {
        val anime = SAnime.create().apply {
            genre = " Action;Drama|Fantasy/School,Romance\nMystery  "
        }

        anime.getGenres() shouldBe listOf(
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
        val anime = SAnime.create().apply {
            genre = "Action, action,  ;, ACTION |  "
        }

        anime.getGenres() shouldBe listOf("Action")
    }
}
