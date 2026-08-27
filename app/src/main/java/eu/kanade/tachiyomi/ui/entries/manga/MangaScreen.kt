package eu.kanade.tachiyomi.ui.entries.manga

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifMangaSourcesLoaded
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.AuthRequiredDialog
import eu.kanade.presentation.entries.components.DeleteItemsDialog
import eu.kanade.presentation.entries.components.DuplicateEntryDialog
import eu.kanade.presentation.entries.components.EditMetadataSheet
import eu.kanade.presentation.entries.components.SetIntervalDialog
import eu.kanade.presentation.entries.components.aurora.AuroraNoteEditorDialog
import eu.kanade.presentation.entries.manga.ChapterSettingsDialog
import eu.kanade.presentation.entries.manga.MangaScreen
import eu.kanade.presentation.entries.manga.components.MangaCoverDialog
import eu.kanade.presentation.entries.manga.components.ScanlatorFilterDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.data.export.manga.MangaCbzExportFailure
import eu.kanade.tachiyomi.data.export.manga.MangaCbzExportProgress
import eu.kanade.tachiyomi.data.export.manga.MangaCbzExportResult
import eu.kanade.tachiyomi.data.export.manga.MangaCbzExporter
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.manga.isLocalOrStub
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.MangaSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialog
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.manga.track.MangaTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.toDirectEntryScreenOrNull
import eu.kanade.tachiyomi.ui.entries.suggestions.toGlobalSearchScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle as collectPreferenceAsState

class MangaScreen(
    private val mangaId: Long,
    val fromSource: Boolean = false,
    private val externalScreenModel: MangaScreenModel? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifMangaSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val updateManga = remember { Injekt.get<UpdateManga>() }
        val screenModel =
            externalScreenModel
                ?: rememberScreenModel { MangaScreenModel(context, lifecycleOwner.lifecycle, mangaId, fromSource) }

        val state by screenModel.state.collectAsStateWithLifecycle()
        val showMangaScanlatorBranches by uiPreferences.showMangaScanlatorBranches().collectPreferenceAsState()

        if (state is MangaScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as MangaScreenModel.State.Success
        val isHttpSource = remember { successState.source is HttpSource }
        var showNotesDialog by remember { mutableStateOf(false) }
        var showEditMetadataSheet by remember { mutableStateOf(false) }
        var showCbzExportSheet by remember { mutableStateOf(false) }
        var cbzExportProgress by remember { mutableStateOf<MangaCbzExportProgress?>(null) }
        val cbzExportSavedMessage = stringResource(MR.strings.manga_export_saved_to_folder)
        var showDownloadAllSheet by remember { mutableStateOf(false) }
        var downloadAllCancelled by remember { mutableStateOf(false) }
        var downloadAllCopied by remember { mutableStateOf(0) }
        var downloadAllTotal by remember { mutableStateOf(0) }
        var downloadAllFinished by remember { mutableStateOf(false) }
        var downloadAllFolderLabel by remember { mutableStateOf("") }

        val downloadAllFolderPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Some devices do not provide persistable grants; the URI still works for this session.
            }
            downloadAllCancelled = false
            downloadAllFinished = false
            downloadAllCopied = 0
            downloadAllTotal = successState.chapters.size
            downloadAllFolderLabel = resolveMangaExportTreeDisplayName(context, uri.toString())
            showDownloadAllSheet = true
            scope.launch {
                val report = screenModel.downloadAllToFolder(
                    destinationTreeUri = uri.toString(),
                    shouldStop = { downloadAllCancelled },
                    onProgress = { copied, total ->
                        downloadAllCopied = copied
                        downloadAllTotal = total
                    },
                )
                downloadAllFinished = true
                downloadAllTotal = report.totalChapters
                downloadAllCopied = report.copiedChapters
                if (!downloadAllCancelled) {
                    context.toast(
                        when {
                            !report.success -> context.stringResource(
                                MR.strings.download_all_failed,
                                downloadAllFolderLabel,
                            )
                            report.failedChapters.isEmpty() -> context.stringResource(
                                MR.strings.download_all_done,
                                report.copiedChapters,
                                downloadAllFolderLabel,
                            )
                            else -> context.stringResource(
                                MR.strings.download_all_partial,
                                report.copiedChapters,
                                downloadAllFolderLabel,
                                report.failedChapters.size,
                            )
                        },
                    )
                }
            }
        }

        val showScanlatorSelector = successState.showScanlatorSelector &&
            shouldShowMangaScanlatorSelector(
                isPreferenceEnabled = showMangaScanlatorBranches,
                sourceBaseUrl = (successState.source as? HttpSource)?.baseUrl,
            )

        LaunchedEffect(successState.manga, screenModel.source) {
            if (isHttpSource) {
                try {
                    withIOContext {
                        assistUrl = getMangaUrl(screenModel.manga, screenModel.source)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to get manga URL" }
                }
            }
        }

        MangaScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.manga.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            chapterSwipeStartAction = screenModel.chapterSwipeStartAction,
            chapterSwipeEndAction = screenModel.chapterSwipeEndAction,
            navigateUp = navigator::pop,
            onChapterClicked = { chapter ->
                scope.launch {
                    val real = screenModel.resolveChapterForOpen(chapter)
                    openChapter(context, real)
                }
            },
            onDownloadChapter = screenModel::runChapterDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onWebViewClicked = {
                openMangaInWebView(
                    navigator,
                    screenModel.manga,
                    screenModel.source,
                )
            }.takeIf { isHttpSource },
            onWebViewLongClicked = {
                copyMangaUrl(
                    context,
                    screenModel.manga,
                    screenModel.source,
                )
            }.takeIf { isHttpSource },
            onTrackingClicked = {
                if (!successState.hasLoggedInTrackers) {
                    navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                } else {
                    screenModel.showTrackDialog()
                }
            },
            onTagSearch = { scope.launch { performGenreSearch(navigator, it, screenModel.source!!) } },
            onGenreClick = { genre -> scope.launch { performGenreSearch(navigator, genre, screenModel.source!!) } },
            onGenreLongClick = null, // handled internally in AuroraImpl as state toggle
            onGenresSearch = { genres ->
                scope.launch { performGenresSearch(navigator, genres, screenModel.source!!) }
            },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            showScanlatorSelector = showScanlatorSelector,
            scanlatorChapterCounts = successState.scanlatorChapterCounts,
            selectedScanlator = successState.selectedScanlator,
            onScanlatorSelected = screenModel::selectScanlator,
            onRefresh = screenModel::fetchAllFromSource,
            onContinueReading = {
                scope.launch {
                    screenModel.getContinueChapter()?.let { chapter ->
                        continueReading(context, chapter)
                    }
                }
            },
            onSearch = { query, global -> scope.launch { performSearch(navigator, query, global) } },
            onSuggestionClick = { item ->
                scope.launch {
                    navigator.push(item.toDirectEntryScreenOrNull() ?: item.toGlobalSearchScreen())
                }
            },
            onCoverClicked = screenModel::showCoverDialog,
            onShareClicked = { shareManga(context, screenModel.manga, screenModel.source) }.takeIf { isHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onExportAsCbzClicked = {
                cbzExportProgress = null
                showCbzExportSheet = true
            }.takeIf { !successState.source.isLocalOrStub() },
            onDownloadAllToFolderClicked = {
                downloadAllFolderPicker.launch(null)
            }.takeIf { !successState.source.isLocalOrStub() },
            onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf { successState.manga.favorite },
            onEditFetchIntervalClicked = screenModel::showSetMangaFetchIntervalDialog.takeIf {
                successState.manga.favorite
            },
            onEditNotesClicked = {
                showNotesDialog = true
            },
            onClickEditInfo = {
                showEditMetadataSheet = true
            },
            onMigrateClicked = {
                navigator.push(MigrateMangaSearchScreen(successState.manga.id))
            }.takeIf { shouldExposeMangaMigrationAction(successState.manga.id) },
            onMultiBookmarkClicked = screenModel::bookmarkChapters,
            onMultiMarkAsReadClicked = screenModel::markChaptersRead,
            onMarkPreviousAsReadClicked = screenModel::markPreviousChapterRead,
            onMultiDeleteClicked = screenModel::showDeleteChapterDialog,
            onChapterSwipe = screenModel::chapterSwipe,
            onChapterSelected = screenModel::toggleSelection,
            onAllChapterSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
            onRetrySuggestions = screenModel::retrySuggestions,
            onOpenSuggestions = {
                val seed = screenModel.getSuggestionSeed()
                    ?: eu.kanade.tachiyomi.data.suggestions.SuggestionSeed(
                        mediaType = eu.kanade.tachiyomi.data.suggestions.sources.SuggestionMediaType.MANGA,
                        primaryTitle = successState.manga.title,
                        candidateTitles = emptyList(),
                        description = successState.manga.description,
                        author = successState.manga.author,
                        genres = successState.manga.genre,
                    )
                navigator.push(
                    eu.kanade.tachiyomi.ui.entries.suggestions.EntrySuggestionsScreen(
                        seed = seed,
                        sourceId = successState.source.id,
                        entryUrl = successState.manga.url,
                    ),
                )
            },
        )

        var showScanlatorsDialog by remember { mutableStateOf(false) }

        val onDismissRequest = {
            screenModel.dismissDialog()
            if (screenModel.autoOpenTrack && screenModel.isFromChangeCategory) {
                screenModel.isFromChangeCategory = false
                screenModel.showTrackDialog()
            }
        }
        when (val dialog = successState.dialog) {
            null -> {}
            is MangaScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        navigator.push(CategoriesTab)
                        CategoriesTab.showMangaCategory()
                    },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is MangaScreenModel.Dialog.DeleteChapters -> {
                DeleteItemsDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteChapters(dialog.chapters)
                    },
                    isManga = true,
                )
            }

            is MangaScreenModel.Dialog.DuplicateManga -> {
                DuplicateEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenEntry = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        screenModel.showMigrateDialog(dialog.duplicate)
                    },
                    openEntryLabel = stringResource(AYMR.strings.action_show_manga),
                )
            }

            is MangaScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    oldManga = dialog.oldManga,
                    newManga = dialog.newManga,
                    screenModel = MigrateMangaDialogScreenModel(),
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(MangaScreen(dialog.oldManga.id)) },
                    onPopScreen = { navigator.replace(MangaScreen(dialog.newManga.id)) },
                )
            }
            is MangaScreenModel.Dialog.AuthRequiredDialog -> {
                AuthRequiredDialog(
                    onDismissRequest = onDismissRequest,
                    onSettingsClicked = {
                        navigator.push(MangaSourcePreferencesScreen(dialog.sourceId))
                    }.takeIf { dialog.isConfigurable },
                    errorMessage = dialog.errorMessage,
                    sourceName = dialog.sourceName,
                    isConfigurable = dialog.isConfigurable,
                )
            }
            MangaScreenModel.Dialog.SettingsSheet -> ChapterSettingsDialog(
                onDismissRequest = onDismissRequest,
                manga = successState.manga,
                downloadedOnly = successState.downloadedOnly,
                onDownloadFilterChanged = screenModel::setDownloadedFilter,
                onUnreadFilterChanged = screenModel::setUnreadFilter,
                onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                onSortModeChanged = screenModel::setSorting,
                onDisplayModeChanged = screenModel::setDisplayMode,
                onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
                onResetToDefault = screenModel::resetToDefaultSettings,
                scanlatorFilterActive = successState.scanlatorFilterActive,
                onScanlatorFilterClicked = { showScanlatorsDialog = true },
            )
            MangaScreenModel.Dialog.TrackSheet -> {
                NavigatorAdaptiveSheet(
                    screen = MangaTrackInfoDialogHomeScreen(
                        mangaId = successState.manga.id,
                        mangaTitle = successState.manga.title,
                        sourceId = successState.source.id,
                    ),
                    enableSwipeDismiss = { it.lastItem is MangaTrackInfoDialogHomeScreen },
                    onDismissRequest = onDismissRequest,
                )
            }
            MangaScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { MangaCoverScreenModel(successState.manga.id) }
                val manga by sm.state.collectAsStateWithLifecycle()
                if (manga != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    MangaCoverDialog(
                        manga = manga!!,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(manga) { manga!!.hasCustomCover(sm.coverCache) },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is MangaScreenModel.Dialog.SetMangaFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.manga.fetchInterval,
                    nextUpdate = dialog.manga.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    isManga = true,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.manga, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
        }

        if (showScanlatorsDialog) {
            ScanlatorFilterDialog(
                availableScanlators = successState.availableScanlators,
                excludedScanlators = successState.excludedScanlators,
                onDismissRequest = { showScanlatorsDialog = false },
                onConfirm = screenModel::setExcludedScanlators,
            )
        }

        if (showNotesDialog) {
            AuroraNoteEditorDialog(
                initialText = successState.manga.notes,
                onDismissRequest = { showNotesDialog = false },
                onSave = { notes ->
                    scope.launchIO {
                        updateManga.await(
                            MangaUpdate(
                                id = successState.manga.id,
                                notes = notes,
                            ),
                        )
                    }
                },
            )
        }

        if (showEditMetadataSheet) {
            EditMetadataSheet(
                onDismissRequest = { showEditMetadataSheet = false },
                currentTitle = successState.manga.displayTitle,
                currentAuthor = successState.manga.displayAuthor,
                currentArtist = successState.manga.displayArtist,
                currentDescription = successState.manga.displayDescription,
                currentGenre = successState.manga.displayGenre,
                currentStatus = successState.manga.displayStatus,
                hasArtist = true,
                onSave = { title, author, artist, description, tags, status ->
                    screenModel.updateMangaMetadata(title, author, artist, description, tags, status)
                },
                onReset = {
                    screenModel.resetMangaMetadata()
                },
                canFetchFromTracker = successState.trackingCount > 0,
                onFetchFromTracker = { trackerId ->
                    screenModel.fetchMetadataFromTracker(trackerId)
                },
            )
        }

        if (showCbzExportSheet) {
            MangaCbzExportSheet(
                totalChapters = successState.chapters.size,
                progress = cbzExportProgress,
                onDismissRequest = {
                    if (cbzExportProgress == null) {
                        showCbzExportSheet = false
                    }
                },
                onExportClicked = { startChapter, endChapter, destinationTreeUri ->
                    scope.launch {
                        cbzExportProgress = MangaCbzExportProgress.Preparing(successState.chapters.size)
                        val result = try {
                            screenModel.exportAsCbz(
                                startChapter = startChapter,
                                endChapter = endChapter,
                                destinationTreeUri = destinationTreeUri,
                                onProgress = { cbzExportProgress = it },
                            )
                        } finally {
                            cbzExportProgress = null
                        }
                        when (result) {
                            is MangaCbzExportResult.Failure -> {
                                context.toast(resolveMangaCbzFailureMessage(context, result))
                                return@launch
                            }
                            is MangaCbzExportResult.Success -> {
                                showCbzExportSheet = false
                                if (destinationTreeUri.isNotBlank()) {
                                    context.toast(cbzExportSavedMessage)
                                    return@launch
                                }
                                shareMangaCbz(context, result.cacheFile)
                            }
                        }
                    }
                },
            )
        }

        if (showDownloadAllSheet) {
            DownloadAllProgressSheet(
                folderLabel = downloadAllFolderLabel,
                copied = downloadAllCopied,
                total = downloadAllTotal,
                finished = downloadAllFinished,
                onCancel = { downloadAllCancelled = true },
                onClose = { showDownloadAllSheet = false },
            )
        }
    }

    private fun continueReading(context: Context, unreadChapter: Chapter?) {
        if (unreadChapter != null) openChapter(context, unreadChapter)
    }

    private fun openChapter(context: Context, chapter: Chapter) {
        context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
    }

    private fun getMangaUrl(manga_: Manga?, source_: MangaSource?): String? {
        val manga = manga_ ?: return null
        val source = source_ as? HttpSource ?: return null

        return try {
            normalizeMangaWebUrl(source.getMangaUrl(manga.toSManga()))
        } catch (e: Exception) {
            null
        }
    }

    private fun openMangaInWebView(navigator: Navigator, manga_: Manga?, source_: MangaSource?) {
        getMangaUrl(manga_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = manga_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    private fun shareManga(context: Context, manga_: Manga?, source_: MangaSource?) {
        try {
            getMangaUrl(manga_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        context.stringResource(MR.strings.action_share),
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    /**
     * Perform a search using the provided query.
     *
     * @param query the search query to the parent controller
     */
    private suspend fun performSearch(navigator: Navigator, query: String, global: Boolean) {
        if (global) {
            navigator.push(GlobalMangaSearchScreen(query))
            return
        }

        if (navigator.size < 2) {
            return
        }

        when (val previousController = navigator.items[navigator.size - 2]) {
            is HomeScreen -> {
                navigator.pop()
                MangaLibraryTab.search(query)
            }
            is BrowseMangaSourceScreen -> {
                navigator.pop()
                previousController.search(query)
            }
        }
    }

    /**
     * Performs a genre search using the provided genre name.
     * Always targets the specific source of this title.
     */
    private suspend fun performGenreSearch(
        navigator: Navigator,
        genreName: String,
        source: MangaSource,
    ) {
        val sourceId = source.id
        val existing = navigator.items.firstOrNull { screen ->
            screen is BrowseMangaSourceScreen && screen.sourceId == sourceId
        } as? BrowseMangaSourceScreen

        if (existing != null) {
            navigator.popUntil { it == existing }
            existing.searchGenre(genreName)
            return
        }

        navigator.push(BrowseMangaSourceScreen(sourceId, genreName))
    }

    private suspend fun performGenresSearch(
        navigator: Navigator,
        genres: List<String>,
        source: MangaSource,
    ) {
        if (genres.isEmpty()) return
        val sourceId = source.id
        val existing = navigator.items.firstOrNull { screen ->
            screen is BrowseMangaSourceScreen && screen.sourceId == sourceId
        } as? BrowseMangaSourceScreen
        if (existing != null) {
            navigator.popUntil { it == existing }
            existing.searchGenres(genres)
            return
        }

        val newScreen = BrowseMangaSourceScreen(sourceId, null)
        navigator.push(newScreen)
        newScreen.searchGenres(genres)
    }

    /**
     * Copy Manga URL to Clipboard
     */
    private fun copyMangaUrl(context: Context, manga_: Manga?, source_: MangaSource?) {
        getMangaUrl(manga_, source_)?.let { url ->
            context.copyToClipboard(url, url)
        }
    }
}

internal fun shouldExposeMangaMigrationAction(mangaId: Long): Boolean {
    return mangaId > 0L
}

internal fun normalizeMangaWebUrl(url: String): String {
    val parsedUrl = url.toHttpUrlOrNull() ?: return url
    val isInkStoryHost = parsedUrl.host.equals("inkstory.net", ignoreCase = true) ||
        parsedUrl.host.equals("api.inkstory.net", ignoreCase = true)
    if (!isInkStoryHost) return url

    val pathSegments = parsedUrl.pathSegments.filter { it.isNotBlank() }
    if (pathSegments.size < 3) return url
    if (pathSegments[0] != "v2" || pathSegments[1] != "books") return url

    val slug = pathSegments[2].takeIf(String::isNotBlank) ?: return url
    return "https://inkstory.net/content/$slug"
}

internal fun shouldShowMangaScanlatorSelector(
    isPreferenceEnabled: Boolean,
    sourceBaseUrl: String?,
): Boolean {
    return isPreferenceEnabled || isInkStoryBaseUrl(sourceBaseUrl)
}

internal fun isInkStoryBaseUrl(sourceBaseUrl: String?): Boolean {
    val host = sourceBaseUrl
        ?.toHttpUrlOrNull()
        ?.host
        ?: return false
    return host.equals("inkstory.net", ignoreCase = true) ||
        host.equals("api.inkstory.net", ignoreCase = true)
}

private fun shareMangaCbz(context: Context, file: java.io.File) {
    runCatching {
        val uri = file.getUriCompat(context)
        context.startActivity(uri.toShareIntent(context, type = MangaCbzExporter.CBZ_MIME_TYPE))
    }.onFailure {
        context.toast(it.message)
    }
}

private data class MangaCbzRangeSelection(
    val isValid: Boolean,
    val startChapter: Int?,
    val endChapter: Int?,
)

private fun resolveMangaCbzRangeSelection(
    exportAll: Boolean,
    startChapterText: String,
    endChapterText: String,
    chapterCount: Int,
): MangaCbzRangeSelection {
    if (exportAll) {
        return MangaCbzRangeSelection(isValid = true, startChapter = null, endChapter = null)
    }

    val startChapter = startChapterText.toIntOrNull()?.takeIf { it > 0 }
    val endChapter = endChapterText.toIntOrNull()?.takeIf { it > 0 }

    if (startChapter == null || endChapter == null) {
        return MangaCbzRangeSelection(isValid = false, startChapter = null, endChapter = null)
    }

    if (startChapter > chapterCount || endChapter > chapterCount || startChapter > endChapter) {
        return MangaCbzRangeSelection(isValid = false, startChapter = null, endChapter = null)
    }

    return MangaCbzRangeSelection(isValid = true, startChapter = startChapter, endChapter = endChapter)
}

private fun resolveMangaExportTreeDisplayName(context: Context, treeUri: String): String {
    if (treeUri.isBlank()) return ""
    val parsed = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return treeUri
    val displayName = runCatching { DocumentFile.fromTreeUri(context, parsed)?.name }.getOrNull()
    if (!displayName.isNullOrBlank()) return displayName
    val segment = parsed.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    return segment ?: treeUri
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaCbzExportSheet(
    totalChapters: Int,
    progress: MangaCbzExportProgress?,
    onDismissRequest: () -> Unit,
    onExportClicked: (startChapter: Int?, endChapter: Int?, destinationTreeUri: String) -> Unit,
) {
    val context = LocalContext.current
    var exportAll by rememberSaveable { mutableStateOf(true) }
    var startChapterText by rememberSaveable { mutableStateOf("") }
    var endChapterText by rememberSaveable { mutableStateOf("") }
    var destinationTreeUri by rememberSaveable { mutableStateOf("") }
    val isExporting = progress != null
    val destinationLabel = remember(destinationTreeUri) {
        resolveMangaExportTreeDisplayName(context, destinationTreeUri)
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Some devices do not provide persistable grants; the URI still works for this session.
            }
            destinationTreeUri = uri.toString()
        }
    }

    val rangeSelection = resolveMangaCbzRangeSelection(
        exportAll = exportAll,
        startChapterText = startChapterText,
        endChapterText = endChapterText,
        chapterCount = totalChapters,
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(MR.strings.manga_export_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            LabeledCheckbox(
                label = stringResource(MR.strings.manga_export_all_chapters),
                checked = exportAll,
                onCheckedChange = { exportAll = it },
                enabled = !isExporting,
            )

            if (!exportAll) {
                OutlinedTextField(
                    value = startChapterText,
                    onValueChange = { startChapterText = it },
                    label = { Text(stringResource(MR.strings.manga_export_start_chapter)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExporting,
                )
                OutlinedTextField(
                    value = endChapterText,
                    onValueChange = { endChapterText = it },
                    label = { Text(stringResource(MR.strings.manga_export_end_chapter)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExporting,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (destinationLabel.isNotBlank()) {
                        destinationLabel
                    } else {
                        stringResource(MR.strings.manga_export_destination_folder)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { folderPicker.launch(null) },
                    enabled = !isExporting,
                ) {
                    Text(stringResource(MR.strings.manga_export_select_folder))
                }
            }

            if (isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = when (val p = progress) {
                        is MangaCbzExportProgress.Preparing -> "0/${p.totalChapters}"
                        is MangaCbzExportProgress.ChapterProcessed -> "${p.current}/${p.total}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = {
                    onExportClicked(
                        rangeSelection.startChapter,
                        rangeSelection.endChapter,
                        destinationTreeUri,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting && rangeSelection.isValid,
            ) {
                Text(stringResource(MR.strings.manga_export_confirm))
            }
        }
    }
}

private fun resolveMangaCbzFailureMessage(
    context: Context,
    result: MangaCbzExportResult.Failure,
): String = when (result.reason) {
    MangaCbzExportFailure.NO_CHAPTERS_SELECTED -> context.stringResource(MR.strings.manga_export_no_chapters)
    MangaCbzExportFailure.NO_DOWNLOADED_CHAPTERS -> context.stringResource(MR.strings.manga_export_no_downloaded)
    MangaCbzExportFailure.DESTINATION_PERMISSION_DENIED -> context.stringResource(
        MR.strings.manga_export_destination_error,
    )
    MangaCbzExportFailure.UNKNOWN -> context.stringResource(MR.strings.manga_export_failed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadAllProgressSheet(
    folderLabel: String,
    copied: Int,
    total: Int,
    finished: Boolean,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(finished) {
        if (finished) {
            delay(1_200)
            onClose()
        }
    }
    ModalBottomSheet(
        onDismissRequest = {
            if (finished) onClose() else onCancel()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(MR.strings.download_all_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(MR.strings.download_all_to, folderLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!finished) {
                LinearProgressIndicator(
                    progress = if (total > 0) copied.toFloat() / total else 0f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "$copied/$total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(MR.strings.download_all_cancel))
                }
            } else {
                Text(
                    text = stringResource(MR.strings.download_all_finished),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
