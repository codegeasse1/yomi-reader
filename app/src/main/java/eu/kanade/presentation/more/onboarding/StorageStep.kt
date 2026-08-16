package eu.kanade.presentation.more.onboarding

import android.content.ActivityNotFoundException
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.resolveAuroraMoreCardBorderColor
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.resolveAuroraElevation
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.tachiyomi.util.system.isTvBox
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class StorageStep : OnboardingStep {

    private val storagePref = Injekt.get<StoragePreferences>().baseStorageDirectory()
    private val folderProvider = Injekt.get<AndroidStorageFolderProvider>()

    private var _isComplete by mutableStateOf(false)

    override val isComplete: Boolean
        get() = _isComplete

    private fun isRiskyStoragePath(pathUri: String): Boolean {
        if (pathUri.isEmpty()) return true
        val decoded = android.net.Uri.decode(pathUri) ?: ""
        return decoded == "content://com.android.externalstorage.documents/document/primary:" ||
            decoded == "/storage/emulated/0" ||
            decoded.contains("/Android", ignoreCase = true) ||
            decoded.contains("primary:Android", ignoreCase = true) ||
            decoded.endsWith(":") // generally points to a drive root
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val handler = LocalUriHandler.current
        val appHaptics = LocalAppHaptics.current

        val isTvBox = isTvBox(LocalContext.current)
        val colors = AuroraTheme.colors

        val pickStorageLocation = SettingsDataScreen.storageLocationPicker(storagePref)

        // Reactive Compose State collection to fix real-time folder selection updates!
        val currentPath by storagePref.collectAsState()
        val isRisky = isRiskyStoragePath(currentPath)
        val isFolderSelected = currentPath.isNotEmpty()

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(MR.strings.onboarding_storage_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            // Dynamic warning card for Scoped Storage SAF limits
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.warning.copy(alpha = 0.08f))
                    .border(1.dp, colors.warning.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(28.dp),
                    )

                    Column {
                        Text(
                            text = stringResource(MR.strings.onboarding_storage_warning_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(MR.strings.onboarding_storage_warning_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            // Folder details block redesigned with premium selected/floating cards
            val cardBgColor = when {
                colors.isEInk -> if (isFolderSelected) {
                    resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Strong)
                } else {
                    resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
                }
                colors.isDark -> if (isFolderSelected) {
                    colors.accent.copy(alpha = 0.12f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                }
                else -> if (isFolderSelected) {
                    colors.accent.copy(alpha = 0.06f)
                } else {
                    Color.White
                }
            }

            val cardBorderColor = when {
                colors.isEInk -> if (isFolderSelected) {
                    colors.accent
                } else {
                    resolveAuroraMoreCardBorderColor(colors)
                }
                colors.isDark -> if (isFolderSelected) {
                    colors.accent.copy(alpha = 0.4f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                }
                else -> if (isFolderSelected) {
                    colors.accent.copy(alpha = 0.35f)
                } else {
                    Color.Transparent
                }
            }

            val cardElevation = if (!colors.isDark && !colors.isEInk && !isFolderSelected) {
                resolveAuroraElevation(colors, AuroraSurfaceLevel.Glass)
            } else {
                0.dp
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardBgColor,
                ),
                border = BorderStroke(
                    width = if (isFolderSelected && !colors.isEInk) 1.5.dp else 1.dp,
                    color = cardBorderColor,
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = cardElevation,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = if (currentPath.isEmpty()) {
                                stringResource(MR.strings.onboarding_storage_no_folder)
                            } else {
                                stringResource(
                                    MR.strings.onboarding_storage_selected_folder,
                                    SettingsDataScreen.storageLocationText(storagePref),
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }

                    if (isTvBox) {
                        if (!storagePref.isSet()) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    appHaptics.tap()
                                    val storage = folderProvider.directory()
                                    if (!storage.exists()) {
                                        storage.mkdirs()
                                    }
                                    storagePref.set(storagePref.get())
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.textOnAccent,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(AYMR.strings.onboarding_storage_action_create_folder))
                            }
                        }
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                appHaptics.tap()
                                try {
                                    pickStorageLocation.launch(null)
                                } catch (e: ActivityNotFoundException) {
                                    context.toast(MR.strings.file_picker_error)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.textOnAccent,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(MR.strings.onboarding_storage_action_select))
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = colors.divider,
            )

            Text(
                text = stringResource(MR.strings.onboarding_storage_help_info, stringResource(MR.strings.app_name)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    appHaptics.tap()
                    handler.openUri(SettingsDataScreen.HELP_URL)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.textPrimary.copy(alpha = 0.05f),
                    contentColor = colors.textPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(MR.strings.onboarding_storage_help_action))
            }
        }

        LaunchedEffect(Unit) {
            storagePref.changes()
                .collectLatest {
                    _isComplete = storagePref.isSet()
                }
        }
    }
}
