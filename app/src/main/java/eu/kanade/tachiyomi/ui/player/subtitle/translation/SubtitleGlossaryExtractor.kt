package eu.kanade.tachiyomi.ui.player.subtitle.translation

/**
 * Lightweight heuristic glossary extraction. Finds recurring proper-noun-like
 * tokens (names, technique/place names common in anime/ranobe) so the translator
 * can be instructed to keep them consistent across the whole file. Purely
 * advisory ΓÇö no network, no model required.
 */
object SubtitleGlossaryExtractor {
    private const val MIN_OCCURRENCES = 3
    private const val MAX_TERMS = 24
    private const val MIN_LENGTH = 3

    private val tokenizer = Regex("[\\p{L}][\\p{L}\\p{M}'\\-]*")
    private val capitalized = Regex("^\\p{Lu}")

    // Common English sentence-initial words we never want as "names".
    private val stopWords = setOf(
        "The", "This", "That", "There", "These", "Those", "Then", "They", "Their", "Them",
        "What", "When", "Where", "Why", "Who", "Which", "How", "And", "But", "For", "Not",
        "You", "Your", "Yes", "Are", "Was", "Were", "Will", "Would", "Should", "Could",
        "Have", "Has", "Had", "Don", "Doesn", "Didn", "Can", "May", "Let", "Get", "Got",
        "Hey", "Okay", "Oh", "Well", "Now", "Here", "She", "Him", "Her", "His",
    )

    fun extract(cues: List<SubtitleCue>): List<String> {
        val counts = LinkedHashMap<String, Int>()
        cues.forEach { cue ->
            // Strip inline styling before scanning so tags never become "terms".
            val plain = SubtitleInlineTagMasker.mask(cue.text).text
            tokenizer.findAll(plain).forEach { match ->
                val word = match.value
                if (word.length < MIN_LENGTH) return@forEach
                if (!capitalized.containsMatchIn(word)) return@forEach
                if (word in stopWords) return@forEach
                if (word.uppercase() == word) return@forEach // skip ALL-CAPS shouting
                counts[word] = (counts[word] ?: 0) + 1
            }
        }
        return counts.entries
            .filter { it.value >= MIN_OCCURRENCES }
            .sortedByDescending { it.value }
            .take(MAX_TERMS)
            .map { it.key }
    }
}
