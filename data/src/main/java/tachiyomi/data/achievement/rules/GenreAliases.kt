package tachiyomi.data.achievement.rules

/**
 * Maps canonical (mostly English) genre/title keywords used by achievement rules
 * to the localized strings that real sources actually tag content with.
 *
 * Localization robustness (esp. Russian):
 *  - Lookups are CASE-INSENSITIVE on the canonical key, so rules may pass
 *    "Horror", "horror" or "HORROR" interchangeably.
 *  - Every search term is expanded with Cyrillic spelling variants that sources
 *    use inconsistently:
 *      * ё <-> е  ("Тёмное" vs "Темное")
 *      * э <-> е  ("фэнтези" vs "фентези", "Сёнэн" vs "Сёнен")
 *    The SQL genre matcher (getLibraryGenreCount) compares terms with LOWER()
 *    but does NOT fold ё/э, so the variants are supplied explicitly here. The
 *    in-memory matcher (genreMatches) folds them via normalize().
 */
object GenreAliases {

    private val genreAliases: Map<String, List<String>> = mapOf(
        "Harem" to listOf("Гарем"),
        "Isekai" to listOf("Исекай", "Исэкай", "Другой мир", "Иной мир"),
        "Shounen" to listOf("Сёнэн", "Шонен", "Сёнен"),
        "Shonen" to listOf("Сёнэн", "Шонен", "Сёнен"),
        "Super Power" to listOf("Суперсила", "Сверхспособности", "Суперспособности"),
        "Military" to listOf("Военное", "Военные", "Армия"),
        "Psychological" to listOf("Психологическое", "Психологический", "Психология"),
        "Tragedy" to listOf("Трагедия"),
        "Drama" to listOf("Драма"),
        "romance" to listOf("Романтика", "Романс", "Любовь"),
        "horror" to listOf("Ужасы", "Хоррор", "Ужас"),
        "slice of life" to listOf("Повседневность", "Срез жизни"),
        "dark fantasy" to listOf("Тёмное фэнтези", "Дарк фэнтези", "Тёмная фэнтези"),
        "Fantasy" to listOf("Фэнтези"),
        "Horror" to listOf("Ужасы", "Хоррор", "Ужас"),
        "Dark" to listOf("Тёмный", "Мрачный", "Тёмное", "Тёмная", "Мрачное", "Дарк"),
    )

    private val titleAliases: Map<String, List<String>> = mapOf(
        "jojo" to listOf("джоджо", "джо джо"),
    )

    // Case-insensitive lookup indexes (canonical keys folded to lowercase once).
    private val genreIndex: Map<String, List<String>> =
        genreAliases.entries.associate { (k, v) -> k.lowercase() to v }
    private val titleIndex: Map<String, List<String>> =
        titleAliases.entries.associate { (k, v) -> k.lowercase() to v }

    // Groups used by DarkFantasyRule for the combo check (Dark/Horror + Fantasy).
    val darkGroupA: List<String> = listOf("Dark", "Horror", "dark fantasy")
    val darkGroupB: List<String> = listOf("Fantasy")

    /**
     * Returns the canonical genre plus every localized alias, each further
     * expanded with ё/э spelling variants and de-duplicated (case-insensitively).
     */
    fun allGenreSearchTerms(canonicalGenre: String): List<String> {
        val base = listOf(canonicalGenre) + genreIndex[canonicalGenre.lowercase()].orEmpty()
        return expandWithVariants(base)
    }

    fun allTitleSearchTerms(canonicalPattern: String): List<String> {
        val base = listOf(canonicalPattern) + titleIndex[canonicalPattern.lowercase()].orEmpty()
        return expandWithVariants(base)
    }

    fun genreMatches(genreEntry: String, canonicalGenres: Collection<String>): Boolean {
        val normalizedEntry = normalize(genreEntry)
        return canonicalGenres.any { canonical ->
            allGenreSearchTerms(canonical).any { alias ->
                normalize(alias) == normalizedEntry
            }
        }
    }

    /**
     * Normalizes a genre/title string for tolerant comparison: trim, lowercase,
     * collapse internal whitespace, and fold Cyrillic ё->е and э->е so the
     * common interchangeable spellings compare equal.
     */
    private fun normalize(value: String): String {
        return value
            .trim()
            .lowercase()
            .replace('ё', 'е')
            .replace('э', 'е')
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Expands each term with realistic Cyrillic spelling variants (ё/е and э/е),
     * keeping the original spelling too, and de-duplicates case-insensitively so
     * SQL LIKE/equality matches DB rows written with any of these spellings.
     */
    private fun expandWithVariants(terms: List<String>): List<String> {
        val out = LinkedHashMap<String, String>()
        for (term in terms) {
            for (variant in cyrillicVariants(term)) {
                out.putIfAbsent(variant.lowercase(), variant)
            }
        }
        return out.values.toList()
    }

    private fun cyrillicVariants(term: String): Set<String> {
        val variants = linkedSetOf(term)
        variants.add(term.replace('ё', 'е').replace('Ё', 'Е'))
        variants.add(term.replace('э', 'е').replace('Э', 'Е'))
        variants.add(
            term.replace('ё', 'е').replace('Ё', 'Е').replace('э', 'е').replace('Э', 'Е'),
        )
        return variants
    }
}
