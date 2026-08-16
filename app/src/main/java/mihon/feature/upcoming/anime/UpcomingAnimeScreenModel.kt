package mihon.feature.upcoming.anime

import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparatorsReversed
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.upcoming.anime.interactor.GetUpcomingAnime
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.YearMonth

class UpcomingAnimeScreenModel(
    private val getUpcomingAnime: GetUpcomingAnime = Injekt.get(),
) : ScreenModel {

    private val selectedYearMonthState = MutableStateFlow(YearMonth.now())

    val state: StateFlow<State> = combine(
        getUpcomingAnime.subscribe(),
        selectedYearMonthState,
    ) { it, selectedYearMonth ->
        val upcomingItems = it.toUpcomingAnimeUIModels()
        State(
            selectedYearMonth = selectedYearMonth,
            items = upcomingItems,
            events = upcomingItems.toEvents(),
            headerIndexes = upcomingItems.getHeaderIndexes(),
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

    private fun List<Anime>.toUpcomingAnimeUIModels(): ImmutableList<UpcomingAnimeUIModel> {
        var animeCount = 0
        return fastMap { UpcomingAnimeUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) animeCount++

                val beforeDate = before?.anime?.expectedNextUpdate?.toLocalDate()
                val afterDate = after?.anime?.expectedNextUpdate?.toLocalDate()

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingAnimeUIModel.Header(afterDate, animeCount).also { animeCount = 0 }
                } else {
                    null
                }
            }
            .toImmutableList()
    }

    private fun List<UpcomingAnimeUIModel>.toEvents(): ImmutableMap<LocalDate, Int> {
        return filterIsInstance<UpcomingAnimeUIModel.Header>()
            .associate { it.date to it.animeCount }
            .toImmutableMap()
    }

    private fun List<UpcomingAnimeUIModel>.getHeaderIndexes(): ImmutableMap<LocalDate, Int> {
        return fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingAnimeUIModel.Header) {
                upcomingUIModel.date to index
            } else {
                null
            }
        }
            .toMap()
            .toImmutableMap()
    }

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        selectedYearMonthState.update { yearMonth }
    }

    data class State(
        val selectedYearMonth: YearMonth = YearMonth.now(),
        val items: ImmutableList<UpcomingAnimeUIModel> = persistentListOf(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val headerIndexes: ImmutableMap<LocalDate, Int> = persistentMapOf(),
    )
}
