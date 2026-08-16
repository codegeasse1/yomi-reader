package eu.kanade.presentation.more.settings.screen.data

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupInspection
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.util.system.DeviceUtil
import kotlinx.coroutines.flow.update
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.i18n.stringResource

class RestoreBackupScreen(
    private val uri: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { RestoreBackupScreenModel(context, uri) }
        val state by model.state.collectAsStateWithLifecycle()
        val uiStyle = rememberResolvedSettingsUiStyle()
        val listState = rememberLazyListState()

        SettingsScaffold(
            title = stringResource(MR.strings.pref_restore_backup),
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
            topBarCanScroll = { listState.canScroll() },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                state = listState,
                actionLabel = stringResource(MR.strings.action_restore),
                actionEnabled = state.canRestore && state.options.canRestore(),
                onClickAction = {
                    model.startRestore()
                    navigator.pop()
                },
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item {
                        WarningBanner(MR.strings.restore_miui_warning)
                    }
                }

                val inspection = state.inspection
                if (inspection != null) {
                    item {
                        BackupSection(MR.strings.backup_restore_preview_title) {
                            BackupOriginChip(
                                text = when (inspection.origin) {
                                    BackupOrigin.TADAMI,
                                    BackupOrigin.TADAMI_SISTER,
                                    ->
                                        stringResource(AYMR.strings.backup_restore_origin_tadami)
                                    BackupOrigin.LNREADER -> "LNReader"
                                    BackupOrigin.MIHON ->
                                        stringResource(MR.strings.backup_restore_origin_mihon)
                                    BackupOrigin.TACHIYOMI_SY -> "TachiyomiSY"
                                    BackupOrigin.KOMIKKU -> "Komikku"
                                    BackupOrigin.LEGACY_ANIYOMI ->
                                        stringResource(AYMR.strings.backup_restore_origin_legacy)
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            if (inspection.isEmpty) {
                                Text(
                                    text = stringResource(MR.strings.backup_restore_empty_preview),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                if (inspection.mangaCount > 0) {
                                    BackupStatRow(
                                        stringResource(AYMR.strings.label_manga),
                                        inspection.mangaCount.toString(),
                                    )
                                }
                                if (inspection.animeCount > 0) {
                                    BackupStatRow(
                                        stringResource(AYMR.strings.label_anime),
                                        inspection.animeCount.toString(),
                                    )
                                }
                                if (inspection.novelCount > 0) {
                                    BackupStatRow(
                                        stringResource(AYMR.strings.label_novel),
                                        inspection.novelCount.toString(),
                                    )
                                }
                                if (inspection.categoriesCount > 0) {
                                    BackupStatRow(
                                        stringResource(MR.strings.categories),
                                        inspection.categoriesCount.toString(),
                                    )
                                }
                                if (inspection.hasAppSettings) {
                                    BackupStatRow(stringResource(MR.strings.app_settings), "\u2713")
                                }
                                if (inspection.hasSourceSettings) {
                                    BackupStatRow(stringResource(MR.strings.source_settings), "\u2713")
                                }
                                if (inspection.hasExtensions) {
                                    BackupStatRow(stringResource(MR.strings.label_extensions), "\u2713")
                                }
                                if (inspection.hasAchievements) {
                                    BackupStatRow(stringResource(AYMR.strings.achievements), "\u2713")
                                }
                            }
                        }
                    }

                    if (inspection.origin.isMihonDerived) {
                        item {
                            BackupStatusBanner(
                                tone = BackupBannerTone.Info,
                                message = stringResource(MR.strings.backup_restore_origin_mihon_info),
                            )
                        }
                    }

                    // Some entries use a source id that also belongs to a novel or anime source.
                    // We refuse to guess, so the user is told rather than silently surprised.
                    if (inspection.ambiguousSourceIds > 0) {
                        item {
                            BackupStatusBanner(
                                tone = BackupBannerTone.Warning,
                                message = stringResource(
                                    AYMR.strings.backup_restore_ambiguous_sources,
                                    inspection.ambiguousSourceIds,
                                ),
                            )
                        }
                    }

                    // Opt-in only, and only for a markerless file that could be an old export of
                    // our own sister app. This is the single place where installed sources are
                    // allowed to influence how entries are split.
                    if (inspection.canOfferLegacySisterImport) {
                        item {
                            BackupSection {
                                LabeledCheckbox(
                                    label = stringResource(
                                        AYMR.strings.backup_restore_legacy_sister_option,
                                    ),
                                    checked = state.options.legacySisterFallback,
                                    onCheckedChange = model::setLegacySisterFallback,
                                )
                                Text(
                                    text = stringResource(
                                        AYMR.strings.backup_restore_legacy_sister_info,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                if (state.canRestore) {
                    item {
                        BackupStatusBanner(
                            tone = BackupBannerTone.Info,
                            message = stringResource(MR.strings.backup_restore_merge_info),
                        )
                    }
                }

                val error = state.error
                if (error is MissingRestoreComponents) {
                    if (error.sources.isNotEmpty()) {
                        item {
                            BackupStatusBanner(
                                tone = BackupBannerTone.Warning,
                                title = stringResource(MR.strings.backup_restore_missing_sources),
                                message = error.sources.joinToString(separator = "\n") { "\u2022 $it" },
                            )
                        }
                    }
                    if (error.trackers.isNotEmpty()) {
                        item {
                            BackupStatusBanner(
                                tone = BackupBannerTone.Warning,
                                title = stringResource(MR.strings.backup_restore_missing_trackers),
                                message = error.trackers.joinToString(separator = "\n") { "\u2022 $it" },
                            )
                        }
                    }
                    item {
                        BackupStatusBanner(
                            tone = BackupBannerTone.Info,
                            message = stringResource(MR.strings.backup_restore_content_full),
                        )
                    }
                }

                if (state.canRestore) {
                    item {
                        BackupSection(MR.strings.backup_restore_options_title) {
                            RestoreOptions.options.forEach { option ->
                                LabeledCheckbox(
                                    label = stringResource(option.label),
                                    checked = option.getter(state.options),
                                    onCheckedChange = {
                                        model.toggle(option.setter, it)
                                    },
                                    enabled = option.enabled(state.options),
                                )
                            }
                        }
                    }

                    item {
                        BackupStatusBanner(
                            tone = BackupBannerTone.Info,
                            message = stringResource(MR.strings.backup_restore_safety_info),
                        )
                    }
                }

                if (error is InvalidRestore) {
                    item {
                        BackupStatusBanner(
                            tone = BackupBannerTone.Error,
                            title = stringResource(MR.strings.invalid_backup_file),
                            message = error.message,
                        )
                    }
                    item {
                        BackupSection {
                            SelectionContainer {
                                Text(
                                    text = error.uri.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class RestoreBackupScreenModel(
    private val context: Context,
    private val uri: String,
) : StateScreenModel<RestoreBackupScreenModel.State>(State()) {

    init {
        validate(uri.toUri())
    }

    fun toggle(setter: (RestoreOptions, Boolean) -> RestoreOptions, enabled: Boolean) {
        mutableState.update {
            it.copy(
                options = setter(it.options, enabled),
            )
        }
    }

    /**
     * Re-validates, because this claim about the file changes how its entries are split and
     * therefore what the preview must show.
     */
    fun setLegacySisterFallback(enabled: Boolean) {
        mutableState.update { it.copy(options = it.options.copy(legacySisterFallback = enabled)) }
        validate(uri.toUri())
    }

    fun startRestore() {
        BackupRestoreJob.start(
            context = context,
            uri = uri.toUri(),
            options = state.value.options,
        )
    }

    private fun validate(uri: Uri) {
        val results = try {
            BackupFileValidator(context).validate(uri, state.value.options.importPolicy())
        } catch (e: Exception) {
            mutableState.update {
                it.copy(
                    error = InvalidRestore(uri, humanReadableError(e)),
                    canRestore = false,
                    inspection = null,
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                error = if (results.missingSources.isNotEmpty() || results.missingTrackers.isNotEmpty()) {
                    MissingRestoreComponents(uri, results.missingSources, results.missingTrackers)
                } else {
                    null
                },
                canRestore = true,
                inspection = results.inspection,
            )
        }
    }

    /**
     * Unwraps the cause chain (the validator wraps decode failures in an
     * [IllegalStateException]) and returns the most specific message instead
     * of a raw exception class dump.
     */
    private fun humanReadableError(e: Throwable): String {
        var current: Throwable? = e
        var message: String? = null
        while (current != null) {
            val m = current.message
            if (!m.isNullOrBlank()) {
                message = m
            }
            current = current.cause
        }
        return message ?: e.javaClass.simpleName
    }

    @Immutable
    data class State(
        val error: Any? = null,
        val canRestore: Boolean = false,
        val options: RestoreOptions = RestoreOptions(),
        val inspection: BackupInspection? = null,
    )
}

private data class MissingRestoreComponents(
    val uri: Uri,
    val sources: List<String>,
    val trackers: List<String>,
)

private data class InvalidRestore(
    val uri: Uri? = null,
    val message: String,
)
