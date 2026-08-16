package tachiyomi.data.achievement.rules

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GenreAliasesTest {

    @Test
    fun `allGenreSearchTerms includes canonical genre and aliases`() {
        val terms = GenreAliases.allGenreSearchTerms("Harem")
        terms shouldBe listOf("Harem", "Гарем")
    }

    @Test
    fun `allGenreSearchTerms returns only canonical when no aliases`() {
        val terms = GenreAliases.allGenreSearchTerms("Comedy")
        terms shouldBe listOf("Comedy")
    }

    @Test
    fun `allTitleSearchTerms includes canonical pattern and aliases`() {
        val terms = GenreAliases.allTitleSearchTerms("jojo")
        terms shouldBe listOf("jojo", "джоджо", "джо джо")
    }

    @Test
    fun `genreMatches returns true for English canonical genre`() {
        GenreAliases.genreMatches("Harem", listOf("Harem")) shouldBe true
    }

    @Test
    fun `genreMatches returns true for Russian alias`() {
        GenreAliases.genreMatches("Гарем", listOf("Harem")) shouldBe true
    }

    @Test
    fun `genreMatches is case insensitive`() {
        GenreAliases.genreMatches("гарем", listOf("Harem")) shouldBe true
        GenreAliases.genreMatches("harem", listOf("Harem")) shouldBe true
    }

    @Test
    fun `genreMatches returns true when one of multiple canonical genres matches`() {
        GenreAliases.genreMatches("Трагедия", listOf("Tragedy", "Drama")) shouldBe true
        GenreAliases.genreMatches("Драма", listOf("Tragedy", "Drama")) shouldBe true
    }

    @Test
    fun `genreMatches returns false for unrelated genre`() {
        GenreAliases.genreMatches("Comedy", listOf("Harem")) shouldBe false
    }

    @Test
    fun `genreMatches handles Shounen Russian aliases`() {
        GenreAliases.genreMatches("Сёнэн", listOf("Shounen")) shouldBe true
        GenreAliases.genreMatches("Шонен", listOf("Shounen")) shouldBe true
    }

    @Test
    fun `genreMatches handles Super Power Russian aliases`() {
        GenreAliases.genreMatches("Суперсила", listOf("Super Power")) shouldBe true
        GenreAliases.genreMatches("Сверхспособности", listOf("Super Power")) shouldBe true
    }

    @Test
    fun `genreMatches handles Military Russian aliases`() {
        GenreAliases.genreMatches("Военное", listOf("Military")) shouldBe true
        GenreAliases.genreMatches("Военные", listOf("Military")) shouldBe true
    }

    @Test
    fun `genreMatches handles Psychological Russian aliases`() {
        GenreAliases.genreMatches("Психологическое", listOf("Psychological")) shouldBe true
        GenreAliases.genreMatches("Психологический", listOf("Psychological")) shouldBe true
    }

    @Test
    fun `genreMatches folds Cyrillic yo and e spelling variants`() {
        // Sources tag Shounen inconsistently: "Сёнэн" (canonical), "Сёнен" (э->е),
        // "Сенен" (ё->е and э->е) - all must count.
        GenreAliases.genreMatches("Сёнэн", listOf("Shounen")) shouldBe true
        GenreAliases.genreMatches("Сёнен", listOf("Shounen")) shouldBe true
        GenreAliases.genreMatches("Сенен", listOf("Shounen")) shouldBe true
        GenreAliases.genreMatches("Шонен", listOf("Shounen")) shouldBe true
    }

    @Test
    fun `genreMatches folds dark fantasy yo variants`() {
        GenreAliases.genreMatches("Тёмное фэнтези", listOf("dark fantasy")) shouldBe true
        GenreAliases.genreMatches("Темное фэнтези", listOf("dark fantasy")) shouldBe true
        GenreAliases.genreMatches("Темное фентези", listOf("dark fantasy")) shouldBe true
    }

    @Test
    fun `genreMatches tolerates case whitespace and trailing spaces`() {
        GenreAliases.genreMatches("  романтика  ", listOf("romance")) shouldBe true
        GenreAliases.genreMatches("РОМАНТИКА", listOf("romance")) shouldBe true
    }

    @Test
    fun `allGenreSearchTerms includes yo and e variants for SQL matching`() {
        val terms = GenreAliases.allGenreSearchTerms("Shounen").map { it.lowercase() }
        // Both spellings must be present so the SQL LOWER() matcher hits DB rows
        // regardless of which the source used.
        terms shouldContain "сёнэн"
        terms shouldContain "сёнен"
        terms shouldContain "сенен"
    }

    @Test
    fun `canonical lookup is case insensitive`() {
        // Rules pass mixed casing (e.g. "Romance" vs "romance"); both must resolve aliases.
        GenreAliases.genreMatches("Романтика", listOf("Romance")) shouldBe true
        GenreAliases.genreMatches("Романтика", listOf("ROMANCE")) shouldBe true
        // Mihon-style "Shonen" key (no 'u') must also resolve Russian aliases.
        GenreAliases.genreMatches("Сёнэн", listOf("Shonen")) shouldBe true
    }
}
