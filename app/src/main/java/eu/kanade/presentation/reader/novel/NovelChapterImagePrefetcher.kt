package eu.kanade.presentation.reader.novel

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

internal object NovelChapterImagePrefetcher {
    private val limitedDispatcher by lazy { Dispatchers.IO.limitedParallelism(2) }

    suspend fun prefetch(
        context: Context,
        imageModels: List<Any>,
        activeImageIndex: Int = 0,
    ) {
        if (imageModels.isEmpty()) return
        val prioritized = prioritizeChapterImageModels(imageModels, activeImageIndex)

        withContext(limitedDispatcher) {
            prioritized.map { model ->
                async {
                    prefetchSingleModel(context, model)
                }
            }.awaitAll()
        }
    }

    private suspend fun prefetchSingleModel(
        context: Context,
        model: Any,
    ) {
        runCatching {
            when (model) {
                is NovelPluginImage -> {
                    NovelPluginImageResolver.resolve(model.url)
                }
                is NovelReaderRefererImage -> {
                    val imageLoader = SingletonImageLoader.get(context)
                    val request = ImageRequest.Builder(context)
                        .data(model)
                        .build()
                    imageLoader.enqueue(request)
                }
                is String -> {
                    if (NovelPluginImage.isSupported(model)) {
                        NovelPluginImageResolver.resolve(model)
                    } else {
                        val imageLoader = SingletonImageLoader.get(context)
                        val request = ImageRequest.Builder(context)
                            .data(model)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            }
        }
    }
}
