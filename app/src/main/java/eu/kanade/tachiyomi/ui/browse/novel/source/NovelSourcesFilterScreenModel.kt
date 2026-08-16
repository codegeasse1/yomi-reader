package eu.kanade.tachiyomi.ui.browse.novel.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.novel.interactor.GetLanguagesWithNovelSources
import eu.kanade.domain.source.novel.interactor.ToggleNovelSource
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.domain.source.novel.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.SortedMap

class NovelSourcesFilterScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val getLanguagesWithSources: GetLanguagesWithNovelSources = Injekt.get(),
    private val toggleSource: ToggleNovelSource = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
) : ScreenModel {

    val state: StateFlow<NovelSourcesFilterScreenModel.State> = combine(
        getLanguagesWithSources.subscribe(),
        preferences.enabledLanguages().changes(),
        preferences.disabledNovelSources().changes(),
    ) { a, b, c -> Triple(a, b, c) }
        .map { (languagesWithSources, enabledLanguages, disabledSources) ->
            State.Success(
                items = languagesWithSources,
                enabledLanguages = enabledLanguages,
                disabledSources = disabledSources,
            ) as State
        }
        .catch { throwable ->
            emit(State.Error(throwable = throwable))
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State.Loading)

    fun toggleSource(source: Source) {
        toggleSource.await(source.id)
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Error(
            val throwable: Throwable,
        ) : State

        @Immutable
        data class Success(
            val items: SortedMap<String, List<Source>>,
            val enabledLanguages: Set<String>,
            val disabledSources: Set<String>,
        ) : State {

            val isEmpty: Boolean
                get() = items.isEmpty()
        }
    }
}
