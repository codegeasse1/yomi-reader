package eu.kanade.tachiyomi.ui.category.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.CreateNovelCategoryWithName
import tachiyomi.domain.category.novel.interactor.DeleteNovelCategory
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.GetVisibleNovelCategories
import tachiyomi.domain.category.novel.interactor.HideNovelCategory
import tachiyomi.domain.category.novel.interactor.RenameNovelCategory
import tachiyomi.domain.category.novel.interactor.ReorderNovelCategory
import tachiyomi.domain.category.novel.interactor.UpdateNovelCategory
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.category.novel.model.NovelCategoryUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCategoryScreenModel(
    private val getAllCategories: GetNovelCategories = Injekt.get(),
    private val getVisibleCategories: GetVisibleNovelCategories = Injekt.get(),
    private val createCategoryWithName: CreateNovelCategoryWithName = Injekt.get(),
    private val hideCategory: HideNovelCategory = Injekt.get(),
    private val deleteCategory: DeleteNovelCategory = Injekt.get(),
    private val reorderCategory: ReorderNovelCategory = Injekt.get(),
    private val renameCategory: RenameNovelCategory = Injekt.get(),
    private val updateCategory: UpdateNovelCategory = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : ScreenModel {

    private val _events: Channel<NovelCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    private val dialogState = MutableStateFlow<NovelCategoryDialog?>(null)

    private val categoriesFlow: Flow<ImmutableList<Category>> = (
        if (libraryPreferences.hideHiddenCategoriesSettings().get()) {
            getVisibleCategories.subscribe()
        } else {
            getAllCategories.subscribe()
        }
        )
        .map { categories ->
            categories
                .map(NovelCategory::toCategory)
                .filterNot(Category::isSystemCategory)
                .toImmutableList()
        }

    val state: StateFlow<NovelCategoryScreenState> = combine(
        categoriesFlow,
        dialogState,
    ) { categories, dialog ->
        NovelCategoryScreenState.Success(
            categories = categories,
            dialog = dialog,
        )
    }.stateIn(
        screenModelScope,
        SharingStarted.WhileSubscribed(5000),
        NovelCategoryScreenState.Loading,
    )

    fun createCategory(name: String) {
        screenModelScope.launch {
            val order = getAllCategories.await().size.toLong()
            val result = createCategoryWithName.await(name, order = order, flags = 0L)
            if (result == null) {
                _events.send(NovelCategoryEvent.InternalError)
            } else {
                runCatching {
                    val manager = Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>()
                    if (!manager.state.value.unlocked) {
                        manager.offer(eu.kanade.domain.easteregg.aurora.AuroraChannels.named("категория", name))
                    }
                }
            }
        }
    }

    fun hideCategory(category: Category) {
        screenModelScope.launch {
            runCatching { hideCategory.await(category.id, !category.hidden) }
                .onFailure { _events.send(NovelCategoryEvent.InternalError) }
        }
    }

    fun toggleHomeHubCategory(category: Category) {
        screenModelScope.launch {
            runCatching {
                updateCategory.await(
                    NovelCategoryUpdate(
                        id = category.id,
                        hiddenFromHomeHub = !category.hiddenFromHomeHub,
                    ),
                )
            }.onFailure { _events.send(NovelCategoryEvent.InternalError) }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteNovelCategory.Result.InternalError -> _events.send(
                    NovelCategoryEvent.InternalError,
                )
                DeleteNovelCategory.Result.Success -> {}
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        screenModelScope.launch {
            when (reorderCategory.await(category.id, newIndex)) {
                is ReorderNovelCategory.Result.InternalError -> _events.send(
                    NovelCategoryEvent.InternalError,
                )
                ReorderNovelCategory.Result.Success,
                ReorderNovelCategory.Result.Unchanged,
                -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        screenModelScope.launch {
            runCatching { renameCategory.await(category.id, name) }
                .onSuccess {
                    runCatching {
                        val manager = Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>()
                        if (!manager.state.value.unlocked) {
                            manager.offer(eu.kanade.domain.easteregg.aurora.AuroraChannels.named("категория", name))
                        }
                    }
                }
                .onFailure { _events.send(NovelCategoryEvent.InternalError) }
        }
    }

    fun showDialog(dialog: NovelCategoryDialog) {
        dialogState.update { dialog }
    }

    fun dismissDialog() {
        dialogState.update { null }
    }
}

sealed interface NovelCategoryDialog {
    data object Create : NovelCategoryDialog
    data class Rename(val category: Category) : NovelCategoryDialog
    data class Delete(val category: Category) : NovelCategoryDialog
}

sealed interface NovelCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : NovelCategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface NovelCategoryScreenState {

    @Immutable
    data object Loading : NovelCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val dialog: NovelCategoryDialog? = null,
    ) : NovelCategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}

private fun NovelCategory.toCategory(): Category {
    return Category(
        id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = hidden,
        hiddenFromHomeHub = hiddenFromHomeHub,
    )
}
