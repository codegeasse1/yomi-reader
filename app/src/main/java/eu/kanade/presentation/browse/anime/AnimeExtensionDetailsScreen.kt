package eu.kanade.presentation.browse.anime

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yomi.reader.R
import eu.kanade.domain.extension.anime.interactor.AnimeExtensionSourceItem
import eu.kanade.presentation.browse.anime.components.AnimeExtensionIcon
import eu.kanade.presentation.browse.components.ExtensionAuroraButton
import eu.kanade.presentation.browse.components.ExtensionBannerTone
import eu.kanade.presentation.browse.components.ExtensionDetailsGlassCard
import eu.kanade.presentation.browse.components.ExtensionStatusBanner
import eu.kanade.presentation.browse.manga.NsfwWarningDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeExtensionDetailsScreen(
    navigateUp: () -> Unit,
    state: AnimeExtensionDetailsScreenModel.State,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickEnableAll: () -> Unit,
    onClickDisableAll: () -> Unit,
    onClickClearCookies: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickUpdate: () -> Unit,
    onClickReinstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val url = remember(state.extension) {
        val regex = """https://raw.githubusercontent.com/(.+?)/(.+?)/.+""".toRegex()
        regex.find(state.extension?.repoUrl.orEmpty())
            ?.let {
                val (user, repo) = it.destructured
                "https://github.com/$user/$repo"
            }
            ?: state.extension?.repoUrl
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extension_info),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        actions = persistentListOf<AppBar.AppBarAction>().builder()
                            .apply {
                                if (url != null) {
                                    add(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_open_repo),
                                            icon = Icons.AutoMirrored.Outlined.Launch,
                                            onClick = {
                                                uriHandler.openUri(url)
                                            },
                                        ),
                                    )
                                }
                                addAll(
                                    listOf(
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_enable_all),
                                            onClick = onClickEnableAll,
                                        ),
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.action_disable_all),
                                            onClick = onClickDisableAll,
                                        ),
                                        AppBar.OverflowAction(
                                            title = stringResource(MR.strings.pref_clear_cookies),
                                            onClick = onClickClearCookies,
                                        ),
                                    ),
                                )
                            }
                            .build(),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state.extension == null) {
            EmptyScreen(
                MR.strings.empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        AnimeExtensionDetails(
            contentPadding = paddingValues,
            extension = state.extension,
            installStep = state.installStep,
            sources = state.sources,
            incognitoMode = state.isIncognito,
            onClickSourcePreferences = onClickSourcePreferences,
            onClickUninstall = onClickUninstall,
            onClickUpdate = onClickUpdate,
            onClickReinstall = onClickReinstall,
            onClickSource = onClickSource,
            onClickIncognito = onClickIncognito,
        )
    }
}

@Composable
private fun AnimeExtensionDetails(
    contentPadding: PaddingValues,
    extension: AnimeExtension.Installed,
    installStep: InstallStep,
    sources: ImmutableList<AnimeExtensionSourceItem>,
    incognitoMode: Boolean,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickUninstall: () -> Unit,
    onClickUpdate: () -> Unit,
    onClickReinstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showNsfwWarning by remember { mutableStateOf(false) }

    ScrollbarLazyColumn(
        contentPadding = contentPadding,
    ) {
        item {
            ExtensionProblemBanners(
                extension = extension,
                installStep = installStep,
                onClickUpdate = onClickUpdate,
                onClickReinstall = onClickReinstall,
            )
        }

        item {
            DetailsHeader(
                extension = extension,
                extIncognitoMode = incognitoMode,
                onClickUninstall = onClickUninstall,
                onClickAppInfo = {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", extension.pkgName, null)
                        context.startActivity(this)
                    }
                    Unit
                }.takeIf { extension.isShared },
                onClickAgeRating = {
                    showNsfwWarning = true
                },
                onExtIncognitoChange = onClickIncognito,
            )
        }

        items(
            items = sources,
            key = { it.source.id },
        ) { source ->
            SourceSwitchPreference(
                modifier = Modifier.animateItem(),
                source = source,
                onClickSourcePreferences = onClickSourcePreferences,
                onClickSource = onClickSource,
            )
        }
    }
    if (showNsfwWarning) {
        NsfwWarningDialog(
            onClickConfirm = {
                showNsfwWarning = false
            },
        )
    }
}

@Composable
private fun ExtensionProblemBanners(
    extension: AnimeExtension.Installed,
    installStep: InstallStep,
    onClickUpdate: () -> Unit,
    onClickReinstall: () -> Unit,
) {
    val busy = !installStep.isCompleted()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        if (extension.isObsolete) {
            ExtensionStatusBanner(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(MR.strings.ext_obsolete),
                message = stringResource(MR.strings.obsolete_extension_message),
                tone = ExtensionBannerTone.Error,
            )
        }
        when {
            extension.needsReinstall -> ExtensionStatusBanner(
                icon = Icons.Outlined.Warning,
                title = stringResource(MR.strings.ext_reinstall_required),
                message = stringResource(MR.strings.ext_reinstall_required_hint),
                tone = ExtensionBannerTone.Warning,
                actionLabel = stringResource(
                    if (busy) MR.strings.ext_installing else MR.strings.ext_reinstall_required,
                ),
                actionEnabled = !busy,
                onAction = onClickReinstall,
            )
            extension.hasUpdate -> ExtensionStatusBanner(
                icon = Icons.Outlined.GetApp,
                title = stringResource(MR.strings.ext_update),
                message = stringResource(MR.strings.ext_update_available_banner),
                tone = ExtensionBannerTone.Info,
                actionLabel = stringResource(
                    if (busy) MR.strings.ext_installing else MR.strings.ext_update,
                ),
                actionEnabled = !busy,
                onAction = onClickUpdate,
            )
        }
    }
}

@Composable
private fun DetailsHeader(
    extension: AnimeExtension,
    extIncognitoMode: Boolean,
    onClickAgeRating: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickAppInfo: (() -> Unit)?,
    onExtIncognitoChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium)
            .padding(
                top = MaterialTheme.padding.small,
                bottom = MaterialTheme.padding.small,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        ExtensionDetailsGlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val extDebugInfo = buildString {
                            append(
                                """
                                Extension name: ${extension.name} (lang: ${extension.lang}; package: ${extension.pkgName})
                                Extension version: ${extension.versionName} (lib: ${extension.libVersion}; version code: ${extension.versionCode})
                                NSFW: ${extension.isNsfw}
                                """.trimIndent(),
                            )

                            if (extension is AnimeExtension.Installed) {
                                append("\n\n")
                                append(
                                    """
                                    Update available: ${extension.hasUpdate}
                                    Orphaned: ${extension.isObsolete}
                                    Shared: ${extension.isShared}
                                    Store: ${extension.repoUrl}
                                    """.trimIndent(),
                                )
                            }
                        }
                        context.copyToClipboard("Extension Debug information", extDebugInfo)
                    }
                    .padding(MaterialTheme.padding.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimeExtensionIcon(
                    modifier = Modifier
                        .size(96.dp),
                    extension = extension,
                    density = DisplayMetrics.DENSITY_XXXHIGH,
                )

                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )

                val strippedPkgName = extension.pkgName.substringAfter(
                    "eu.kanade.tachiyomi.animeextension.",
                )

                Text(
                    text = strippedPkgName,
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraTheme.colors.textSecondary,
                )
            }

            HorizontalDivider(color = AuroraTheme.colors.textPrimary.copy(alpha = 0.08f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.extraLarge,
                        vertical = MaterialTheme.padding.small,
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoText(
                    modifier = Modifier.weight(1f),
                    primaryText = extension.versionName,
                    secondaryText = stringResource(MR.strings.ext_info_version),
                )

                InfoDivider()

                InfoText(
                    modifier = Modifier.weight(if (extension.isNsfw) 1.5f else 1f),
                    primaryText = LocaleHelper.getSourceDisplayName(extension.lang, context),
                    secondaryText = stringResource(MR.strings.ext_info_language),
                )

                if (extension.isNsfw) {
                    InfoDivider()

                    InfoText(
                        modifier = Modifier.weight(1f),
                        primaryText = stringResource(MR.strings.ext_nsfw_short),
                        primaryTextStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        ),
                        secondaryText = stringResource(MR.strings.ext_info_age_rating),
                        onClick = onClickAgeRating,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            ExtensionAuroraButton(
                text = stringResource(MR.strings.ext_uninstall),
                onClick = onClickUninstall,
                accent = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )

            if (onClickAppInfo != null) {
                ExtensionAuroraButton(
                    text = stringResource(MR.strings.ext_app_info),
                    onClick = onClickAppInfo,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ExtensionDetailsGlassCard(modifier = Modifier.fillMaxWidth()) {
            TextPreferenceWidget(
                title = stringResource(MR.strings.pref_incognito_mode),
                subtitle = stringResource(MR.strings.pref_incognito_mode_extension_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                widget = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = extIncognitoMode,
                            onCheckedChange = onExtIncognitoChange,
                            modifier = Modifier.padding(start = TrailingWidgetBuffer),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun InfoText(
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    primaryTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = null, indication = null, onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier.then(clickableModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = primaryText,
            textAlign = TextAlign.Center,
            style = primaryTextStyle,
        )

        Text(
            text = secondaryText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) {
                AuroraTheme.colors.accent
            } else {
                AuroraTheme.colors.textSecondary
            },
        )
    }
}

@Composable
private fun InfoDivider() {
    VerticalDivider(
        modifier = Modifier.height(20.dp),
        color = AuroraTheme.colors.textPrimary.copy(alpha = 0.12f),
    )
}

@Composable
private fun SourceSwitchPreference(
    source: AnimeExtensionSourceItem,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    ExtensionDetailsGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = 4.dp),
    ) {
        TextPreferenceWidget(
            title = if (source.labelAsName) {
                source.source.toString()
            } else {
                LocaleHelper.getSourceDisplayName(source.source.lang, context)
            },
            widget = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (source.source is ConfigurableAnimeSource) {
                        IconButton(onClick = { onClickSourcePreferences(source.source.id) }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(MR.strings.label_settings),
                                tint = AuroraTheme.colors.textSecondary,
                            )
                        }
                    }

                    Switch(
                        checked = source.enabled,
                        onCheckedChange = null,
                        modifier = Modifier.padding(start = TrailingWidgetBuffer),
                    )
                }
            },
            onPreferenceClick = { onClickSource(source.source.id) },
        )
    }
}
