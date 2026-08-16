package mihon.feature.upcoming.novel

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
import mihon.domain.upcoming.novel.interactor.GetUpcomingNovel
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.YearMonth

class UpcomingNovelScreenModel(
    private val getUpcomingNovel: GetUpcomingNovel = Injekt.get(),
) : ScreenModel {

    private val selectedYearMonthState = MutableStateFlow(YearMonth.now())

    val state: StateFlow<State> = combine(
        getUpcomingNovel.subscribe(),
        selectedYearMonthState,
    ) { it, selectedYearMonth ->
        val upcomingItems = it.toUpcomingNovelUIModels()
        State(
            selectedYearMonth = selectedYearMonth,
            items = upcomingItems,
            events = upcomingItems.toEvents(),
            headerIndexes = upcomingItems.getHeaderIndexes(),
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

    private fun List<Novel>.toUpcomingNovelUIModels(): ImmutableList<UpcomingNovelUIModel> {
        var novelCount = 0
        return fastMap { UpcomingNovelUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) novelCount++

                val beforeDate = before?.novel?.expectedNextUpdate?.toLocalDate()
                val afterDate = after?.novel?.expectedNextUpdate?.toLocalDate()

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingNovelUIModel.Header(afterDate, novelCount).also { novelCount = 0 }
                } else {
                    null
                }
            }
            .toImmutableList()
    }

    private fun List<UpcomingNovelUIModel>.toEvents(): ImmutableMap<LocalDate, Int> {
        return filterIsInstance<UpcomingNovelUIModel.Header>()
            .associate { it.date to it.novelCount }
            .toImmutableMap()
    }

    private fun List<UpcomingNovelUIModel>.getHeaderIndexes(): ImmutableMap<LocalDate, Int> {
        return fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingNovelUIModel.Header) {
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
        val items: ImmutableList<UpcomingNovelUIModel> = persistentListOf(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val headerIndexes: ImmutableMap<LocalDate, Int> = persistentMapOf(),
    )
}
