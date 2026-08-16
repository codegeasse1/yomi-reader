package eu.kanade.tachiyomi.ui.browse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.PagingSource
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.browse.search.SavedSearchFilterSerializer
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreenModel
import kotlinx.serialization.json.jsonArray
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.interactor.GetRemoteNovel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Which title flavour a carousel page hosts.
 *
 * An enum on purpose: the screen is a Voyager [Screen] (java.io.Serializable) and is serialized
 * into the saved navigation state whenever the activity stops. Enum constants survive Java
 * serialization natively; the previous sealed interface with `data object`s did not implement
 * Serializable, so stopping the activity with the carousel open crashed with
 * `NotSerializableException: TitleCarouselType$Novel` inside `Parcel.writeSerializable`.
 */
internal enum class TitleCarouselType {
    Manga,
    Anime,
    Novel,
}

/**
 * Swipeable carousel over the titles of a source listing.
 *
 * Replaces the plain title screen when a title is opened from a source browser: horizontal swipes
 * move to the neighbouring titles of the listing the user came from. The carousel starts with the
 * snapshot of titles the browser had in memory and keeps fetching further pages of the listing on
 * demand; swiping back only walks the already loaded titles.
 *
 * Title screen models are hosted here (not inside the pager pages) and kept in an LRU of
 * [MAX_LIVE_TITLE_MODELS], so flipping between nearby titles never reloads their chapter lists.
 */
internal class TitleCarouselScreen(
    private val type: TitleCarouselType,
    private val sourceId: Long,
    private val initialTitleIds: List<Long>,
    private val initialIndex: Int,
    private val listingQuery: String?,
    private val filtersJson: String? = null,
) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val haptic = LocalHapticFeedback.current
        val titleIds = remember {
            mutableStateListOf<Long>().apply { addAll(initialTitleIds) }
        }
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, titleIds.size.coerceAtLeast(1) - 1),
        ) { titleIds.size.coerceAtLeast(1) }
        // Access-ordered LRU: flipping away evicts the least recently used model, so a long swipe
        // session cannot pile up title screens in memory.
        val modelCache = remember { TitleCarouselModelCache() }
        val loader = remember(type, sourceId, listingQuery, filtersJson) {
            TitleCarouselListingLoader(type, sourceId, listingQuery, filtersJson)
        }
        var endReached by remember { mutableStateOf(false) }
        var loadInFlight by remember { mutableStateOf(false) }

        // Fetch the next listing page when the reader approaches the end of the loaded window.
        LaunchedEffect(pagerState.currentPage) {
            if (endReached || loadInFlight) return@LaunchedEffect
            if (pagerState.currentPage >= titleIds.size - 3) {
                loadInFlight = true
                val appended = loader.loadNextPage(titleIds)
                loadInFlight = false
                if (!appended) {
                    endReached = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }

        // Boundary feedback: reaching the first title, or the last one of a finished listing.
        LaunchedEffect(pagerState.currentPage, endReached) {
            val atStart = pagerState.currentPage == 0
            val atEnd = endReached && pagerState.currentPage == titleIds.size - 1
            if (atStart || atEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val titleId = titleIds.getOrNull(page) ?: return@HorizontalPager
            val isVisible = page == pagerState.currentPage || page == pagerState.targetPage
            if (!isVisible) return@HorizontalPager
            when (type) {
                TitleCarouselType.Manga -> {
                    val screen = remember(titleId) {
                        MangaScreen(
                            mangaId = titleId,
                            fromSource = true,
                            externalScreenModel = modelCache.getOrCreate(titleId) {
                                MangaScreenModel(context, lifecycleOwner.lifecycle, titleId, isFromSource = true)
                            } as MangaScreenModel,
                        )
                    }
                    screen.Content()
                }
                TitleCarouselType.Anime -> {
                    val screen = remember(titleId) {
                        AnimeScreen(
                            animeId = titleId,
                            fromSource = true,
                            externalScreenModel = modelCache.getOrCreate(titleId) {
                                AnimeScreenModel(context, lifecycleOwner.lifecycle, titleId, isFromSource = true)
                            } as AnimeScreenModel,
                        )
                    }
                    screen.Content()
                }
                TitleCarouselType.Novel -> {
                    val screen = remember(titleId) {
                        NovelScreen(
                            novelId = titleId,
                            fromSource = true,
                            externalScreenModel = modelCache.getOrCreate(titleId) {
                                NovelScreenModel(lifecycleOwner.lifecycle, titleId)
                            } as NovelScreenModel,
                        )
                    }
                    screen.Content()
                }
            }
        }
    }
}

/** Bounded access-ordered cache of carousel title screen models. */
private class TitleCarouselModelCache {
    private val models = object : LinkedHashMap<Long, Any>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Any>?): Boolean =
            size > MAX_LIVE_TITLE_MODELS
    }

    @Synchronized
    fun getOrCreate(titleId: Long, create: () -> Any): Any =
        models.getOrPut(titleId) { create() }

    companion object {
        const val MAX_LIVE_TITLE_MODELS = 5
    }
}

/**
 * Page-by-page loader over the same source listing the browser showed.
 *
 * Reuses the paging sources of the browse screen models ([GetRemoteNovel]/[GetRemoteManga]/
 * [GetRemoteAnime]) so the carousel continuation matches the listing (popular/latest/search with
 * the same filters) the user was looking at.
 */
private class TitleCarouselListingLoader(
    private val type: TitleCarouselType,
    private val sourceId: Long,
    private val listingQuery: String?,
    private val filtersJson: String?,
) {
    private var nextPageKey: Long = 1L
    private var exhausted = false

    suspend fun loadNextPage(knownIds: MutableList<Long>): Boolean {
        if (exhausted) return false
        val result = runCatching {
            pagingSource().load(
                PagingSource.LoadParams.Append(
                    key = nextPageKey,
                    loadSize = PAGE_SIZE,
                    placeholdersEnabled = false,
                ),
            )
        }.getOrNull()
        val page = result as? PagingSource.LoadResult.Page<*, *> ?: run {
            exhausted = true
            return false
        }
        nextPageKey = (page.nextKey as? Long) ?: 0L
        if (nextPageKey <= 0L) exhausted = true
        val ids = pageIds(page.data)
        if (ids.isEmpty()) {
            exhausted = true
            return false
        }
        val known = knownIds.toHashSet()
        var appended = false
        for (id in ids) {
            if (known.add(id)) {
                knownIds.add(id)
                appended = true
            }
        }
        return appended
    }

    private fun pageIds(data: List<*>): List<Long> = when (type) {
        TitleCarouselType.Novel -> data.filterIsInstance<Novel>().map { it.id }
        TitleCarouselType.Manga -> data.filterIsInstance<Manga>().map { it.id }
        TitleCarouselType.Anime -> data.filterIsInstance<Anime>().map { it.id }
    }

    private suspend fun pagingSource() = when (type) {
        TitleCarouselType.Novel -> Injekt.get<GetRemoteNovel>().subscribe(
            sourceId,
            listingQuery.orEmpty(),
            novelFilters(),
        )
        TitleCarouselType.Manga -> Injekt.get<GetRemoteManga>().subscribe(
            sourceId,
            listingQuery.orEmpty(),
            mangaFilters(),
        )
        TitleCarouselType.Anime -> Injekt.get<GetRemoteAnime>().subscribe(
            sourceId,
            listingQuery.orEmpty(),
            animeFilters(),
        )
    }

    private fun novelFilters(): NovelFilterList {
        val json = filtersJson ?: return NovelFilterList()
        return NovelFilterList().also { filters ->
            runCatching { SavedSearchFilterSerializer.deserialize(json, filters) }
        }
    }

    private fun animeFilters(): AnimeFilterList {
        val json = filtersJson ?: return AnimeFilterList()
        return AnimeFilterList().also { filters ->
            runCatching { SavedSearchFilterSerializer.deserialize(json, filters) }
        }
    }

    private suspend fun mangaFilters(): FilterList {
        val json = filtersJson ?: return FilterList()
        val source = runCatching {
            Injekt.get<MangaSourceManager>().getOrStub(sourceId) as? CatalogueSource
        }.getOrNull() ?: return FilterList()
        val base = runCatching { source.getFilterList() }.getOrElse { FilterList() }
        return runCatching {
            val serializer = Injekt.get<xyz.nulldev.ts.api.http.serializer.FilterSerializer>()
            serializer.deserialize(
                base,
                kotlinx.serialization.json.Json.parseToJsonElement(json).jsonArray,
            )
            base
        }.getOrElse { base }
    }

    private companion object {
        const val PAGE_SIZE = 25
    }
}
