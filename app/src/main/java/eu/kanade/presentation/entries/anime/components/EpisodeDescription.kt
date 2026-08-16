package eu.kanade.presentation.entries.anime.components

internal fun String?.isLikelyEpisodeDescription(): Boolean {
    val text = this?.trim().orEmpty()
    if (text.isBlank()) return false

    val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }
    val hasSentencePunctuation = text.any { it == '.' || it == '!' || it == '?' }

    return text.length >= 48 || wordCount >= 7 || hasSentencePunctuation
}
