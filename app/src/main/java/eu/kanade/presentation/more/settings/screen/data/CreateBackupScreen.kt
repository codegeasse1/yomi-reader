package eu.kanade.presentation.more.settings.screen.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CreateBackupScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CreateBackupScreenModel() }
        val state by model.state.collectAsStateWithLifecycle()
        val lastAutoBackup by model.lastAutoBackup.collectAsState()
        val uiStyle = rememberResolvedSettingsUiStyle()
        val listState = rememberLazyListState()

        val chooseBackupDir = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/*"),
        ) {
            if (it != null) {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                model.createBackup(context, it)
                navigator.pop()
            }
        }

        SettingsScaffold(
            title = stringResource(MR.strings.pref_create_backup),
            uiStyle = uiStyle,
            onBackPressed = navigator::pop,
            topBarCanScroll = { listState.canScroll() },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                state = listState,
                actionLabel = stringResource(MR.strings.action_create),
                actionEnabled = state.options.canCreate(),
                onClickAction = {
                    if (!BackupCreateJob.isManualJobRunning(context)) {
                        try {
                            chooseBackupDir.launch(BackupCreator.getFilename())
                        } catch (e: ActivityNotFoundException) {
                            context.toast(MR.strings.file_picker_error)
                        }
                    } else {
                        context.toast(MR.strings.backup_in_progress)
                    }
                },
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item {
                        WarningBanner(MR.strings.restore_miui_warning)
                    }
                }

                item {
                    BackupStatusBanner(
                        tone = BackupBannerTone.Info,
                        message = stringResource(MR.strings.backup_info),
                    )
                }

                item {
                    BackupStatusBanner(
                        tone = BackupBannerTone.Info,
                        message = stringResource(AYMR.strings.backup_not_included_info),
                    )
                }

                item {
                    BackupStepHeader(MR.strings.backup_step_content)
                }

                item {
                    BackupSection(MR.strings.label_library) {
                        Options(BackupOptions.libraryOptions, state, model)
                    }
                }

                item {
                    BackupSection(MR.strings.label_settings) {
                        Options(BackupOptions.settingsOptions, state, model)
                    }
                }

                item {
                    BackupSection(MR.strings.label_extensions) {
                        Options(BackupOptions.extensionOptions, state, model)
                    }
                }

                item {
                    BackupSection(AYMR.strings.achievements) {
                        Options(BackupOptions.achievementsOptions, state, model)
                    }
                }

                item {
                    BackupStepHeader(MR.strings.backup_step_compatibility)
                }

                item {
                    BackupSection(MR.strings.label_backup) {
                        Options(BackupOptions.compatOptions, state, model)
                        if (!state.options.sisterAppCompatible) {
                            SectionText(stringResource(MR.strings.pref_backup_sister_app_compat_summary))
                        }
                    }
                }

                if (state.options.sisterAppCompatible) {
                    item {
                        BackupStatusBanner(
                            tone = BackupBannerTone.Warning,
                            message = stringResource(AYMR.strings.backup_compat_drops_warning),
                        )
                    }
                }

                item {
                    BackupStepHeader(MR.strings.backup_step_destination)
                }

                item {
                    BackupSection {
                        SectionText(stringResource(MR.strings.backup_destination_info))
                        if (lastAutoBackup > 0L) {
                            Spacer(Modifier.height(4.dp))
                            SectionText(
                                stringResource(
                                    MR.strings.last_auto_backup_info,
                                    relativeTimeSpanString(lastAutoBackup),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionText(text: String) {
        val isAurora = LocalSettingsUiStyle.current == SettingsUiStyle.Aurora
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isAurora) {
                AuroraTheme.colors.textSecondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    @Composable
    private fun Options(
        options: ImmutableList<BackupOptions.Entry>,
        state: CreateBackupScreenModel.State,
        model: CreateBackupScreenModel,
    ) {
        options.forEach { option ->
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

private class CreateBackupScreenModel(
    backupPreferences: BackupPreferences = Injekt.get(),
) : StateScreenModel<CreateBackupScreenModel.State>(State()) {

    val lastAutoBackup = backupPreferences.lastAutoBackupTimestamp()

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        mutableState.update {
            it.copy(
                options = setter(it.options, enabled),
            )
        }
    }

    fun createBackup(context: Context, uri: Uri) {
        BackupCreateJob.startNow(context, uri, state.value.options)
    }

    @Immutable
    data class State(
        val options: BackupOptions = BackupOptions(),
    )
}
