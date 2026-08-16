package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode

class NovelTtsHighlightEstimator {

    fun estimateWordRange(
        utterance: NovelTtsUtterance,
        elapsedMs: Long,
        durationMs: Long,
        mode: NovelTtsHighlightMode,
        startWordIndex: Int = 0,
    ): NovelTtsHighlightSelection? {
        if (mode == NovelTtsHighlightMode.OFF) return null
        val wordRanges = utterance.wordRanges
        if (wordRanges.isEmpty()) return null
        val startIndex = startWordIndex.coerceIn(0, wordRanges.lastIndex)
        val activeWordRanges = wordRanges.drop(startIndex)
        if (activeWordRanges.isEmpty()) return null
        if (durationMs <= 0L) {
            val lastWord = activeWordRanges.last()
            return NovelTtsHighlightSelection(
                wordIndex = lastWord.wordIndex,
                wordRange = lastWord,
            )
        }

        val clampedElapsed = elapsedMs.coerceIn(0L, durationMs)
        val weights = activeWordRanges.map { wordRange ->
            wordWeight(
                utteranceText = utterance.text,
                wordRange = wordRange,
            )
        }
        val totalWeight = weights.sum().takeIf { it > 0.0 } ?: return null
        val targetWeight = (clampedElapsed.toDouble() / durationMs.toDouble()) * totalWeight
        var traversedWeight = 0.0

        activeWordRanges.forEachIndexed { index, wordRange ->
            traversedWeight += weights[index]
            if (targetWeight <= traversedWeight || index == activeWordRanges.lastIndex) {
                return NovelTtsHighlightSelection(
                    wordIndex = wordRange.wordIndex,
                    wordRange = wordRange,
                )
            }
        }

        val lastWord = activeWordRanges.last()
        return NovelTtsHighlightSelection(
            wordIndex = lastWord.wordIndex,
            wordRange = lastWord,
        )
    }

    private fun wordWeight(
        utteranceText: String,
        wordRange: NovelTtsWordRange,
    ): Double {
        val wordLength = wordRange.text.length
        var weight = BASE_WORD_WEIGHT + wordLength.coerceAtMost(WEIGHTED_CHAR_CAP) * WEIGHT_PER_CHAR
        if (wordLength >= LONG_WORD_THRESHOLD) {
            weight += LONG_WORD_BONUS
        }

        val trailingText = utteranceText
            .substring(wordRange.endChar, utteranceText.length)
            .takeWhile { !it.isLetterOrDigit() && !it.isWhitespace() }

        weight += trailingText.sumOf { punctuationWeight(it) }
        return weight
    }

    private fun punctuationWeight(char: Char): Double {
        return when (char) {
            ',', ';', ':' -> CLAUSE_PAUSE_WEIGHT
            '.', '!', '?', '…' -> SENTENCE_PAUSE_WEIGHT
            '-', '–', '—' -> DASH_PAUSE_WEIGHT
            else -> OTHER_PUNCTUATION_WEIGHT
        }
    }

    private companion object {
        const val BASE_WORD_WEIGHT = 1.0
        const val WEIGHT_PER_CHAR = 0.08
        const val WEIGHTED_CHAR_CAP = 16
        const val LONG_WORD_THRESHOLD = 10
        const val LONG_WORD_BONUS = 0.4
        const val CLAUSE_PAUSE_WEIGHT = 0.7
        const val SENTENCE_PAUSE_WEIGHT = 1.1
        const val DASH_PAUSE_WEIGHT = 0.35
        const val OTHER_PUNCTUATION_WEIGHT = 0.15
    }
}
