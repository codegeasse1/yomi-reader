package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTtsHighlightMode
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelReaderTtsUiState
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsEngineDescriptor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsVoiceDescriptor
import eu.kanade.tachiyomi.ui.reader.novel.tts.resolveNovelTtsVoiceSelection
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale
import kotlin.math.roundToInt

internal data class NovelReaderTtsControlSnapshot(
    val showControls: Boolean,
    val primaryActionIsPause: Boolean,
    val hasVoiceSettingsAccess: Boolean,
    val highlightEnabled: Boolean,
)

internal fun resolveNovelReaderTtsControlSnapshot(
    uiState: NovelReaderTtsUiState,
): NovelReaderTtsControlSnapshot {
    return NovelReaderTtsControlSnapshot(
        showControls = uiState.enabled,
        primaryActionIsPause = uiState.isPlaying,
        hasVoiceSettingsAccess = uiState.enabled,
        highlightEnabled = uiState.activeHighlightMode != NovelTtsHighlightMode.OFF,
    )
}

internal data class NovelReaderTtsOptionsSnapshot(
    val selectedEngine: NovelTtsEngineDescriptor?,
    val selectedVoice: NovelTtsVoiceDescriptor?,
    val selectedLocaleTag: String,
    val showLocaleFallback: Boolean,
)

internal data class NovelReaderTtsLanguageOptionSnapshot(
    val localeTag: String,
    val label: String,
    val voiceCount: Int,
)

internal data class NovelReaderTtsVoiceOptionSnapshot(
    val voiceId: String,
    val title: String,
    val subtitle: String?,
    val selected: Boolean,
)

internal data class NovelReaderTtsLanguagePickerSnapshot(
    val languages: List<NovelReaderTtsLanguageOptionSnapshot>,
    val recentLanguages: List<NovelReaderTtsLanguageOptionSnapshot>,
    val activeLanguageTag: String,
    val voices: List<NovelReaderTtsVoiceOptionSnapshot>,
)

internal data class NovelReaderTtsTextSnapshot(
    val unknownLanguage: String = "Unknown language",
    val defaultVoice: String = "Default voice",
    val voiceNumberPattern: String = "Voice %d",
    val networkHint: String = "Network",
    val offlineHint: String = "Offline",
    val notInstalledHint: String = "Not installed",
    val loadingVoices: String = "Loading voices...",
)

internal fun resolveNovelReaderTtsOptionsSnapshot(
    uiState: NovelReaderTtsUiState,
): NovelReaderTtsOptionsSnapshot {
    val selection = resolveNovelTtsVoiceSelection(
        availableVoices = uiState.availableVoices,
        availableLocales = uiState.availableLocales,
        capabilities = uiState.capabilities,
        preferredVoiceId = uiState.selectedVoiceId,
        preferredLocaleTag = uiState.selectedLocaleTag,
    )
    return NovelReaderTtsOptionsSnapshot(
        selectedEngine = uiState.availableEngines.firstOrNull {
            it.packageName == uiState.selectedEnginePackage
        },
        selectedVoice = selection.selectedVoice,
        selectedLocaleTag = selection.selectedLocaleTag,
        showLocaleFallback = selection.showLocaleFallback,
    )
}

internal fun formatNovelReaderTtsLocaleLabel(
    localeTag: String,
    unknownLanguageLabel: String = "Unknown language",
): String {
    val normalizedTag = localeTag.takeIf { it.isNotBlank() } ?: return unknownLanguageLabel
    val locale = runCatching { Locale.forLanguageTag(normalizedTag) }.getOrNull()
    val displayName = locale?.displayName?.takeIf { it.isNotBlank() && it != normalizedTag }
    val titleLocale = locale ?: Locale.getDefault()
    return displayName?.replaceFirstChar { character ->
        if (character.isLowerCase()) {
            character.titlecase(titleLocale)
        } else {
            character.toString()
        }
    } ?: normalizedTag
}

private fun NovelTtsVoiceDescriptor.hasTechnicalName(): Boolean {
    val normalizedName = name.trim().lowercase(Locale.ROOT)
    if (normalizedName.isBlank()) return true
    val normalizedLocale = localeTag.trim().lowercase(Locale.ROOT)
    return normalizedName == normalizedLocale ||
        normalizedName.contains("-x-") ||
        Regex("^[a-z0-9]{2,}(?:[-_][a-z0-9]{2,}){2,}$").matches(normalizedName)
}

private fun resolveNovelReaderTtsVoiceTitle(
    voice: NovelTtsVoiceDescriptor,
    indexInLanguage: Int,
    totalVoicesInLanguage: Int,
    textSnapshot: NovelReaderTtsTextSnapshot,
): String {
    val preferredName = voice.name.trim().takeIf { it.isNotBlank() && !voice.hasTechnicalName() }
    return preferredName ?: if (totalVoicesInLanguage == 1) {
        textSnapshot.defaultVoice
    } else {
        textSnapshot.voiceNumberPattern.format(Locale.getDefault(), indexInLanguage + 1)
    }
}

private fun resolveNovelReaderTtsVoiceSubtitle(
    voice: NovelTtsVoiceDescriptor,
    textSnapshot: NovelReaderTtsTextSnapshot,
): String? {
    val hints = buildList {
        if (voice.requiresNetwork) {
            add(textSnapshot.networkHint)
        } else if (voice.hasTechnicalName()) {
            add(textSnapshot.offlineHint)
        }
        if (!voice.isInstalled) {
            add(textSnapshot.notInstalledHint)
        }
    }
    return hints.takeIf { it.isNotEmpty() }?.joinToString(" - ")
}

internal fun resolveNovelReaderTtsLanguagePickerSnapshot(
    uiState: NovelReaderTtsUiState,
    browsingLanguageTag: String? = null,
    textSnapshot: NovelReaderTtsTextSnapshot = NovelReaderTtsTextSnapshot(),
): NovelReaderTtsLanguagePickerSnapshot {
    val optionsSnapshot = resolveNovelReaderTtsOptionsSnapshot(uiState)
    if (optionsSnapshot.showLocaleFallback) {
        val languages = uiState.availableLocales
            .distinct()
            .sortedBy { localeTag -> formatNovelReaderTtsLocaleLabel(localeTag, textSnapshot.unknownLanguage) }
            .map { localeTag ->
                NovelReaderTtsLanguageOptionSnapshot(
                    localeTag = localeTag,
                    label = formatNovelReaderTtsLocaleLabel(localeTag, textSnapshot.unknownLanguage),
                    voiceCount = 0,
                )
            }
        val recentLanguages = uiState.recentLanguageTags.mapNotNull { recentTag ->
            languages.firstOrNull { it.localeTag == recentTag }
        }
        val activeLanguageTag = browsingLanguageTag
            ?.takeIf { candidate -> languages.any { it.localeTag == candidate } }
            ?: optionsSnapshot.selectedLocaleTag.takeIf { selected ->
                languages.any { it.localeTag == selected }
            }
            ?: languages.firstOrNull()?.localeTag.orEmpty()
        return NovelReaderTtsLanguagePickerSnapshot(
            languages = languages,
            recentLanguages = recentLanguages,
            activeLanguageTag = activeLanguageTag,
            voices = emptyList(),
        )
    }

    val voicesByLanguage = uiState.availableVoices
        .groupBy { voice -> voice.localeTag.takeIf { it.isNotBlank() } ?: uiState.selectedLocaleTag }
    val languages = voicesByLanguage
        .keys
        .sortedBy { localeTag -> formatNovelReaderTtsLocaleLabel(localeTag, textSnapshot.unknownLanguage) }
        .map { localeTag ->
            NovelReaderTtsLanguageOptionSnapshot(
                localeTag = localeTag,
                label = formatNovelReaderTtsLocaleLabel(localeTag, textSnapshot.unknownLanguage),
                voiceCount = voicesByLanguage[localeTag].orEmpty().size,
            )
        }
    val recentLanguages = uiState.recentLanguageTags.mapNotNull { recentTag ->
        languages.firstOrNull { it.localeTag == recentTag }
    }
    val activeLanguageTag = browsingLanguageTag
        ?.takeIf { candidate -> languages.any { it.localeTag == candidate } }
        ?: optionsSnapshot.selectedVoice?.localeTag?.takeIf { selected ->
            languages.any { it.localeTag == selected }
        }
        ?: optionsSnapshot.selectedLocaleTag.takeIf { selected ->
            languages.any { it.localeTag == selected }
        }
        ?: languages.firstOrNull()?.localeTag.orEmpty()
    val voicesForLanguage = voicesByLanguage[activeLanguageTag].orEmpty()
    return NovelReaderTtsLanguagePickerSnapshot(
        languages = languages,
        recentLanguages = recentLanguages,
        activeLanguageTag = activeLanguageTag,
        voices = voicesForLanguage.mapIndexed { index, voice ->
            NovelReaderTtsVoiceOptionSnapshot(
                voiceId = voice.id,
                title = resolveNovelReaderTtsVoiceTitle(
                    voice = voice,
                    indexInLanguage = index,
                    totalVoicesInLanguage = voicesForLanguage.size,
                    textSnapshot = textSnapshot,
                ),
                subtitle = resolveNovelReaderTtsVoiceSubtitle(voice, textSnapshot),
                selected = voice.id == optionsSnapshot.selectedVoice?.id,
            )
        },
    )
}

internal fun filterNovelReaderTtsLanguages(
    languages: List<NovelReaderTtsLanguageOptionSnapshot>,
    query: String,
): List<NovelReaderTtsLanguageOptionSnapshot> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isBlank()) return languages
    return languages.filter { language ->
        language.label.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            language.localeTag.lowercase(Locale.ROOT).contains(normalizedQuery)
    }
}

internal fun resolveNovelReaderTtsSummaryLine(
    uiState: NovelReaderTtsUiState,
    snapshot: NovelReaderTtsOptionsSnapshot,
    textSnapshot: NovelReaderTtsTextSnapshot = NovelReaderTtsTextSnapshot(),
): String? {
    if (!uiState.enabled) return null
    if (uiState.isLoadingVoices) return textSnapshot.loadingVoices
    val selectedVoiceTitle = snapshot.selectedVoice?.let { selectedVoice ->
        val voicesInLanguage = uiState.availableVoices.filter { it.localeTag == selectedVoice.localeTag }
        val indexInLanguage = voicesInLanguage.indexOfFirst { it.id == selectedVoice.id }
            .takeIf { it >= 0 }
            ?: 0
        resolveNovelReaderTtsVoiceTitle(
            voice = selectedVoice,
            indexInLanguage = indexInLanguage,
            totalVoicesInLanguage = voicesInLanguage.size.coerceAtLeast(1),
            textSnapshot = textSnapshot,
        )
    }
    val parts = buildList {
        snapshot.selectedEngine?.label?.takeIf { it.isNotBlank() }?.let(::add)
        selectedVoiceTitle?.takeIf { it.isNotBlank() }?.let(::add)
        snapshot.selectedLocaleTag.takeIf { it.isNotBlank() }?.let {
            add(formatNovelReaderTtsLocaleLabel(it, textSnapshot.unknownLanguage))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" - ")
}

@Composable
private fun rememberNovelReaderTtsTextSnapshot(): NovelReaderTtsTextSnapshot {
    return NovelReaderTtsTextSnapshot(
        unknownLanguage = stringResource(AYMR.strings.novel_reader_tts_unknown_language),
        defaultVoice = stringResource(AYMR.strings.novel_reader_tts_default_voice),
        voiceNumberPattern = stringResource(AYMR.strings.novel_reader_tts_voice_number),
        networkHint = stringResource(AYMR.strings.novel_reader_tts_voice_hint_network),
        offlineHint = stringResource(AYMR.strings.novel_reader_tts_voice_hint_offline),
        notInstalledHint = stringResource(AYMR.strings.novel_reader_tts_voice_hint_not_installed),
        loadingVoices = stringResource(AYMR.strings.novel_reader_tts_loading_voices),
    )
}

@Composable
internal fun NovelReaderTtsControls(
    uiState: NovelReaderTtsUiState,
    onTogglePlayback: () -> Unit,
    onStop: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSetEnginePackage: (String) -> Unit,
    onSetVoiceId: (String) -> Unit,
    onSetLocaleTag: (String) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onDisableTts: () -> Unit,
    onPreviewVoice: (String) -> Unit = {},
    onStopVoicePreview: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snapshot = resolveNovelReaderTtsControlSnapshot(uiState)
    val optionsSnapshot = resolveNovelReaderTtsOptionsSnapshot(uiState)
    val textSnapshot = rememberNovelReaderTtsTextSnapshot()
    if (!snapshot.showControls) return

    var showOptions by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            uiState.activeUtteranceText?.takeIf { it.isNotBlank() }?.let { utteranceText ->
                Text(
                    text = utteranceText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            resolveNovelReaderTtsSummaryLine(uiState, optionsSnapshot, textSnapshot)?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val glassColors = AuroraTheme.colors
        val pillShape = RoundedCornerShape(999.dp)
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(
                    if (glassColors.isDark) {
                        Color.White.copy(alpha = 0.06f)
                    } else {
                        Color.Black.copy(alpha = 0.04f)
                    },
                )
                .border(width = 1.dp, color = auroraRimColor(), shape = pillShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onStop) {
                Icon(
                    Icons.Outlined.Stop,
                    contentDescription = stringResource(AYMR.strings.novel_reader_tts_action_stop),
                    tint = glassColors.textSecondary,
                )
            }

            IconButton(onClick = onSkipPrevious) {
                Icon(
                    Icons.Outlined.SkipPrevious,
                    contentDescription = stringResource(AYMR.strings.novel_reader_tts_action_previous),
                    tint = glassColors.textPrimary,
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        glassColors.accent.copy(alpha = if (glassColors.isDark) 0.18f else 0.14f),
                    )
                    .border(
                        width = 1.dp,
                        color = glassColors.accent.copy(alpha = 0.55f),
                        shape = CircleShape,
                    )
                    .clickable(onClick = onTogglePlayback),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Icon(
                    imageVector = if (snapshot.primaryActionIsPause) {
                        Icons.Outlined.Pause
                    } else {
                        Icons.Outlined.PlayArrow
                    },
                    contentDescription = if (snapshot.primaryActionIsPause) {
                        stringResource(AYMR.strings.novel_reader_tts_action_pause)
                    } else {
                        stringResource(AYMR.strings.novel_reader_tts_action_play)
                    },
                    modifier = Modifier.size(30.dp),
                    tint = glassColors.accent,
                )
            }

            IconButton(onClick = onSkipNext) {
                Icon(
                    Icons.Outlined.SkipNext,
                    contentDescription = stringResource(AYMR.strings.novel_reader_tts_action_next),
                    tint = glassColors.textPrimary,
                )
            }

            IconButton(onClick = { showOptions = true }) {
                Icon(
                    imageVector = Icons.Outlined.SettingsVoice,
                    contentDescription = stringResource(AYMR.strings.novel_reader_tts_voice_settings),
                    tint = glassColors.textSecondary,
                )
            }
        }

        uiState.errorMessage?.takeIf { it.isNotBlank() }?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (showOptions) {
        NovelReaderTtsOptionsDialog(
            uiState = uiState,
            onDismiss = {
                onStopVoicePreview()
                showOptions = false
            },
            onSetEnginePackage = onSetEnginePackage,
            onSetVoiceId = onSetVoiceId,
            onSetLocaleTag = onSetLocaleTag,
            onSetSpeechRate = onSetSpeechRate,
            onSetPitch = onSetPitch,
            onDisableTts = {
                onStopVoicePreview()
                onDisableTts()
                showOptions = false
            },
            onPreviewVoice = onPreviewVoice,
            onStopVoicePreview = onStopVoicePreview,
        )
    }
}

@Composable
private fun NovelReaderTtsOptionsDialog(
    uiState: NovelReaderTtsUiState,
    onDismiss: () -> Unit,
    onSetEnginePackage: (String) -> Unit,
    onSetVoiceId: (String) -> Unit,
    onSetLocaleTag: (String) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onDisableTts: () -> Unit,
    onPreviewVoice: (String) -> Unit,
    onStopVoicePreview: () -> Unit,
) {
    val aurora = AuroraTheme.colors
    val snapshot = resolveNovelReaderTtsOptionsSnapshot(uiState)
    val textSnapshot = rememberNovelReaderTtsTextSnapshot()
    var browsingLanguageTag by remember(
        uiState.selectedEnginePackage,
        uiState.availableVoices,
        uiState.availableLocales,
        uiState.isLoadingVoices,
        snapshot.showLocaleFallback,
    ) {
        mutableStateOf(
            resolveNovelReaderTtsLanguagePickerSnapshot(uiState).activeLanguageTag,
        )
    }
    var languageSearchQuery by remember(
        uiState.selectedEnginePackage,
        snapshot.showLocaleFallback,
    ) {
        mutableStateOf("")
    }
    var activePicker by remember { mutableStateOf<NovelReaderTtsPicker?>(null) }
    val languagePicker = resolveNovelReaderTtsLanguagePickerSnapshot(
        uiState = uiState,
        browsingLanguageTag = browsingLanguageTag,
        textSnapshot = textSnapshot,
    )
    val filteredLanguages = filterNovelReaderTtsLanguages(
        languages = languagePicker.languages,
        query = languageSearchQuery,
    )
    val activeLanguageLabel = languagePicker.languages
        .firstOrNull { it.localeTag == languagePicker.activeLanguageTag }
        ?.label
        ?: formatNovelReaderTtsLocaleLabel(
            localeTag = languagePicker.activeLanguageTag,
            unknownLanguageLabel = textSnapshot.unknownLanguage,
        )
    val selectedVoiceTitle = if (uiState.selectedVoiceId.isBlank()) {
        textSnapshot.defaultVoice
    } else {
        languagePicker.voices.firstOrNull { it.selected }?.title ?: textSnapshot.defaultVoice
    }
    val onSelectLanguage: (String) -> Unit = { localeTag ->
        onStopVoicePreview()
        browsingLanguageTag = localeTag
        languageSearchQuery = ""
        onSetLocaleTag(localeTag)
        activePicker = if (snapshot.showLocaleFallback) null else NovelReaderTtsPicker.VOICE
    }
    val previewSample = if (
        languagePicker.activeLanguageTag.substringBefore('-').substringBefore('_')
            .equals("ru", ignoreCase = true)
    ) {
        stringResource(AYMR.strings.novel_reader_tts_preview_sample_ru)
    } else {
        stringResource(AYMR.strings.novel_reader_tts_preview_sample)
    }
    val sheetContainer = when {
        aurora.isEInk -> MaterialTheme.colorScheme.surfaceContainerHigh
        aurora.isDark -> Color.Black.copy(alpha = 0.72f)
        else -> Color.White.copy(alpha = 0.90f)
    }
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize,
    )
    val pageMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp

    AdaptiveSheet(
        onDismissRequest = {
            onStopVoicePreview()
            onDismiss()
        },
        modifier = Modifier.border(width = 1.dp, color = auroraRimColor(), shape = sheetShape),
        containerColor = sheetContainer,
        applyStatusBarsPadding = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = pageMaxHeight)
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(aurora.textSecondary.copy(alpha = 0.35f)),
                )
            }

            if (activePicker == null) {
                Text(
                    text = stringResource(AYMR.strings.novel_reader_tts_section),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = aurora.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                NovelReaderTtsPickerTitle(
                    text = stringResource(
                        when (activePicker) {
                            NovelReaderTtsPicker.ENGINE -> AYMR.strings.novel_reader_tts_engine
                            NovelReaderTtsPicker.LANGUAGE -> AYMR.strings.novel_reader_tts_language
                            NovelReaderTtsPicker.VOICE -> AYMR.strings.novel_reader_tts_voice
                            null -> AYMR.strings.novel_reader_tts_section
                        },
                    ),
                    onBack = {
                        onStopVoicePreview()
                        activePicker = null
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (activePicker) {
                    null -> {
                        NovelReaderTtsNavRow(
                            label = stringResource(AYMR.strings.novel_reader_tts_engine),
                            value = snapshot.selectedEngine?.label
                                ?: stringResource(AYMR.strings.novel_reader_tts_no_engines_found),
                            enabled = uiState.availableEngines.isNotEmpty(),
                            onClick = {
                                onStopVoicePreview()
                                activePicker = NovelReaderTtsPicker.ENGINE
                            },
                        )
                        if (uiState.isLoadingVoices) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_tts_loading_voices),
                                style = MaterialTheme.typography.bodyMedium,
                                color = aurora.textSecondary,
                            )
                        } else {
                            NovelReaderTtsNavRow(
                                label = stringResource(AYMR.strings.novel_reader_tts_language),
                                value = activeLanguageLabel,
                                onClick = {
                                    onStopVoicePreview()
                                    activePicker = NovelReaderTtsPicker.LANGUAGE
                                },
                            )
                            if (snapshot.showLocaleFallback) {
                                Text(
                                    text = stringResource(AYMR.strings.novel_reader_tts_locale_fallback_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = aurora.textSecondary,
                                )
                            } else {
                                NovelReaderTtsNavRow(
                                    label = stringResource(AYMR.strings.novel_reader_tts_voice),
                                    value = selectedVoiceTitle,
                                    onClick = {
                                        onStopVoicePreview()
                                        activePicker = NovelReaderTtsPicker.VOICE
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = stringResource(
                                AYMR.strings.novel_reader_tts_speech_rate_value,
                                (uiState.speechRate * 100).roundToInt(),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = aurora.accent,
                        )
                        Slider(
                            value = uiState.speechRate.coerceIn(0.5f, 2f),
                            onValueChange = onSetSpeechRate,
                            valueRange = 0.5f..2f,
                        )
                        Text(
                            text = stringResource(
                                AYMR.strings.novel_reader_tts_pitch_value,
                                (uiState.pitch * 100).roundToInt(),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = aurora.accent,
                        )
                        Slider(
                            value = uiState.pitch.coerceIn(0.5f, 2f),
                            onValueChange = onSetPitch,
                            valueRange = 0.5f..2f,
                        )
                    }
                    NovelReaderTtsPicker.ENGINE -> {
                        if (uiState.availableEngines.isEmpty()) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_tts_no_engines_found),
                                style = MaterialTheme.typography.bodySmall,
                                color = aurora.textSecondary,
                            )
                        } else {
                            uiState.availableEngines.forEach { engine ->
                                NovelReaderTtsSelectableCard(
                                    title = engine.label,
                                    subtitle = if (engine.isSystemDefault) {
                                        stringResource(AYMR.strings.novel_reader_tts_system_default_engine)
                                    } else {
                                        null
                                    },
                                    selected = engine.packageName == snapshot.selectedEngine?.packageName,
                                    onClick = {
                                        onStopVoicePreview()
                                        onSetEnginePackage(engine.packageName)
                                        activePicker = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    NovelReaderTtsPicker.LANGUAGE -> {
                        OutlinedTextField(
                            value = languageSearchQuery,
                            onValueChange = { languageSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(AYMR.strings.novel_reader_tts_find_language)) },
                        )
                        if (snapshot.showLocaleFallback) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_tts_locale_fallback_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = aurora.textSecondary,
                            )
                        }
                        if (languageSearchQuery.isBlank() && languagePicker.recentLanguages.isNotEmpty()) {
                            NovelReaderTtsSectionLabel(
                                text = stringResource(AYMR.strings.novel_reader_tts_recent_languages),
                            )
                            languagePicker.recentLanguages.forEach { language ->
                                NovelReaderTtsLanguageCard(
                                    language = language,
                                    activeLanguageTag = languagePicker.activeLanguageTag,
                                    showVoiceCount = !snapshot.showLocaleFallback,
                                    onSelect = onSelectLanguage,
                                )
                            }
                            NovelReaderTtsSectionLabel(
                                text = stringResource(AYMR.strings.novel_reader_tts_language),
                            )
                        }
                        if (filteredLanguages.isEmpty()) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_tts_no_languages_available),
                                style = MaterialTheme.typography.bodySmall,
                                color = aurora.textSecondary,
                            )
                        } else {
                            filteredLanguages.forEach { language ->
                                NovelReaderTtsLanguageCard(
                                    language = language,
                                    activeLanguageTag = languagePicker.activeLanguageTag,
                                    showVoiceCount = !snapshot.showLocaleFallback,
                                    onSelect = onSelectLanguage,
                                )
                            }
                        }
                    }
                    NovelReaderTtsPicker.VOICE -> {
                        NovelReaderTtsPreviewSampleStrip(
                            sampleText = previewSample,
                            isPreviewingSelected = uiState.previewingVoiceId != null &&
                                uiState.previewingVoiceId == uiState.selectedVoiceId,
                            onPreviewSelected = {
                                if (uiState.previewingVoiceId != null) {
                                    onStopVoicePreview()
                                } else {
                                    onPreviewVoice(uiState.selectedVoiceId)
                                }
                            },
                        )
                        NovelReaderTtsVoiceRow(
                            title = stringResource(AYMR.strings.novel_reader_tts_default_voice),
                            subtitle = stringResource(AYMR.strings.novel_reader_tts_system_default_voice_summary),
                            selected = uiState.selectedVoiceId.isBlank(),
                            isPreviewing = uiState.previewingVoiceId == "",
                            onSelect = {
                                onStopVoicePreview()
                                onSetVoiceId("")
                                activePicker = null
                            },
                            onPreview = {
                                if (uiState.previewingVoiceId == "") {
                                    onStopVoicePreview()
                                } else {
                                    onPreviewVoice("")
                                }
                            },
                        )
                        if (languagePicker.voices.isEmpty()) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_tts_no_voices_for_selected_language),
                                style = MaterialTheme.typography.bodySmall,
                                color = aurora.textSecondary,
                            )
                        } else {
                            languagePicker.voices.forEach { voice ->
                                NovelReaderTtsVoiceRow(
                                    title = voice.title,
                                    subtitle = voice.subtitle,
                                    selected = voice.selected && uiState.selectedVoiceId.isNotBlank(),
                                    isPreviewing = uiState.previewingVoiceId == voice.voiceId,
                                    onSelect = {
                                        onStopVoicePreview()
                                        onSetVoiceId(voice.voiceId)
                                        activePicker = null
                                    },
                                    onPreview = {
                                        if (uiState.previewingVoiceId == voice.voiceId) {
                                            onStopVoicePreview()
                                        } else {
                                            onPreviewVoice(voice.voiceId)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (activePicker == null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDisableTts) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_tts_disable),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(
                        onClick = {
                            onStopVoicePreview()
                            onDismiss()
                        },
                    ) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_tts_done),
                            color = aurora.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private enum class NovelReaderTtsPicker {
    ENGINE,
    LANGUAGE,
    VOICE,
}

@Composable
private fun NovelReaderTtsPickerTitle(
    text: String,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = AuroraTheme.colors.textPrimary,
            )
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NovelReaderTtsNavRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (colors.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f),
            )
            .border(width = 1.dp, color = auroraRimColor(), shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
        )
    }
}

@Composable
private fun NovelReaderTtsLanguageCard(
    language: NovelReaderTtsLanguageOptionSnapshot,
    activeLanguageTag: String,
    showVoiceCount: Boolean,
    onSelect: (String) -> Unit,
) {
    NovelReaderTtsSelectableCard(
        title = language.label,
        subtitle = if (showVoiceCount) {
            stringResource(AYMR.strings.novel_reader_tts_voice_count, language.voiceCount)
        } else {
            null
        },
        selected = language.localeTag == activeLanguageTag,
        onClick = { onSelect(language.localeTag) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NovelReaderTtsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = AuroraTheme.colors.accent,
    )
}

@Composable
private fun NovelReaderTtsPreviewSampleStrip(
    sampleText: String,
    isPreviewingSelected: Boolean,
    onPreviewSelected: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (colors.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f),
            )
            .border(width = 1.dp, color = auroraRimColor(), shape = shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = sampleText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 3,
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = if (colors.isDark) 0.20f else 0.14f))
                .border(width = 1.dp, color = colors.accent.copy(alpha = 0.55f), shape = CircleShape)
                .clickable(onClick = onPreviewSelected),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPreviewingSelected) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = stringResource(AYMR.strings.novel_reader_tts_preview_action),
                tint = colors.accent,
            )
        }
    }
}

@Composable
private fun NovelReaderTtsVoiceRow(
    title: String,
    selected: Boolean,
    isPreviewing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    subtitle: String? = null,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    selected -> colors.accent.copy(alpha = if (colors.isDark) 0.16f else 0.12f)
                    colors.isDark -> Color.White.copy(alpha = 0.07f)
                    else -> Color.Black.copy(alpha = 0.05f)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.55f) else auroraRimColor(),
                shape = shape,
            )
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) colors.accent else colors.textPrimary,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        IconButton(onClick = onPreview) {
            Icon(
                imageVector = if (isPreviewing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = stringResource(AYMR.strings.novel_reader_tts_preview_action),
                tint = colors.accent,
            )
        }
    }
}

@Composable
private fun NovelReaderTtsSelectableCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    selected -> colors.accent.copy(alpha = if (colors.isDark) 0.16f else 0.12f)
                    colors.isDark -> Color.White.copy(alpha = 0.07f)
                    else -> Color.Black.copy(alpha = 0.05f)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) colors.accent.copy(alpha = 0.55f) else auroraRimColor(),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.accent else colors.textPrimary,
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
