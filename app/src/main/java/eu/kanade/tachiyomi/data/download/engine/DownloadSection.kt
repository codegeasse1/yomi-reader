package eu.kanade.tachiyomi.data.download.engine

/**
 * Shared download section identifiers for the consolidated engine.
 * Each content type maps to a visual section with its own accent color.
 */
enum class DownloadSection(val label: String, val accentHex: String) {
    ANIME("Anime", "#FF5C62"),
    MANGA("Manga", "#4A90D9"),
    NOVEL("Novels", "#7C5CFC"),
}
