package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock

internal fun resolveChapterImageModel(
    imageUrl: String,
    referer: String?,
): Any? {
    val trimmed = imageUrl.trim()
    if (trimmed.isBlank()) return null
    return if (NovelPluginImage.isSupported(trimmed)) {
        NovelPluginImage(trimmed)
    } else if (!referer.isNullOrBlank()) {
        NovelReaderRefererImage(
            url = trimmed,
            referer = referer,
        )
    } else {
        trimmed
    }
}

internal fun extractChapterImageModels(
    blocks: List<Any>,
    referer: String?,
): List<Any> {
    val models = mutableListOf<Any>()
    for (block in blocks) {
        val imageUrl = when (block) {
            is NovelRichContentBlock.Image -> block.url
            is NovelReaderScreenModel.ContentBlock.Image -> block.url
            else -> null
        } ?: continue
        val model = resolveChapterImageModel(imageUrl, referer) ?: continue
        models.add(model)
    }
    return models.distinct()
}

internal fun prioritizeChapterImageModels(
    imageModels: List<Any>,
    activeImageIndex: Int = 0,
): List<Any> {
    if (imageModels.size <= 1) return imageModels
    val normalizedIndex = activeImageIndex.coerceIn(0, imageModels.lastIndex)
    val result = mutableListOf<Any>()
    result.add(imageModels[normalizedIndex])

    for (i in (normalizedIndex + 1) until imageModels.size) {
        result.add(imageModels[i])
    }
    for (i in 0 until normalizedIndex) {
        result.add(imageModels[i])
    }
    return result
}
