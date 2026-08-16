package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.tachiyomi.ui.reader.novel.ProviderApiTestStatus
import eu.kanade.tachiyomi.ui.reader.novel.setting.GeminiPromptMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationStylePreset
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPrivateBridge
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiPromptModifiers
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelTranslationStylePresets
import eu.kanade.tachiyomi.ui.reader.novel.translation.OLLAMA_CLOUD_FREE_MODELS
import eu.kanade.tachiyomi.ui.reader.novel.translation.resolveTranslationReasoningOptions
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs

internal enum class TranslationKind {
    Gemini,
    Google,
}

internal data class TranslationSwitchRequest(
    val from: TranslationKind,
    val to: TranslationKind,
)

@Composable
internal fun GeminiTranslationDialog(
    readerSettings: NovelReaderSettings,
    isTranslating: Boolean,
    translationProgress: Int,
    isVisible: Boolean,
    hasCache: Boolean,
    logs: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleVisibility: () -> Unit,
    onClear: () -> Unit,
    onClearAllCache: () -> Unit,
    onAddLog: (String) -> Unit,
    onClearLogs: () -> Unit,
    onSetGeminiApiKey: (String) -> Unit,
    onSetGeminiModel: (String) -> Unit,
    onSetGeminiBatchSize: (Int) -> Unit,
    onSetGeminiConcurrency: (Int) -> Unit,
    onSetGeminiRelaxedMode: (Boolean) -> Unit,
    onSetGeminiDisableCache: (Boolean) -> Unit,
    onSetGeminiReasoningEffort: (String) -> Unit,
    onSetGeminiBudgetTokens: (Int) -> Unit,
    onSetGeminiTemperature: (Float) -> Unit,
    onSetGeminiTopP: (Float) -> Unit,
    onSetGeminiTopK: (Int) -> Unit,
    onSetGeminiPromptMode: (GeminiPromptMode) -> Unit,
    onSetGeminiSourceLang: (String) -> Unit,
    onSetGeminiTargetLang: (String) -> Unit,
    onSetGeminiStylePreset: (NovelTranslationStylePreset) -> Unit,
    onSetGeminiEnabledPromptModifiers: (List<String>) -> Unit,
    onSetGeminiCustomPromptModifier: (String) -> Unit,
    onSetGeminiAutoTranslateEnglishSource: (Boolean) -> Unit,
    onSetGeminiPrefetchNextChapterTranslation: (Boolean) -> Unit,
    onSetGeminiPrivateUnlocked: (Boolean) -> Unit,
    onSetGeminiPrivatePythonLikeMode: (Boolean) -> Unit,
    onSetTranslationProvider: (NovelTranslationProvider) -> Unit,
    onSetOpenRouterBaseUrl: (String) -> Unit,
    onSetOpenRouterApiKey: (String) -> Unit,
    onSetOpenRouterModel: (String) -> Unit,
    onRefreshOpenRouterModels: () -> Unit,
    onTestOpenRouterConnection: () -> Unit,
    onSetDeepSeekBaseUrl: (String) -> Unit,
    onSetDeepSeekApiKey: (String) -> Unit,
    onSetDeepSeekModel: (String) -> Unit,
    onRefreshDeepSeekModels: () -> Unit,
    onTestDeepSeekConnection: () -> Unit,
    onSetMistralBaseUrl: (String) -> Unit,
    onSetMistralApiKey: (String) -> Unit,
    onSetMistralModel: (String) -> Unit,
    onRefreshMistralModels: () -> Unit,
    onTestMistralConnection: () -> Unit,
    onSetNvidiaBaseUrl: (String) -> Unit,
    onSetNvidiaApiKey: (String) -> Unit,
    onSetNvidiaModel: (String) -> Unit,
    onRefreshNvidiaModels: () -> Unit,
    onTestNvidiaConnection: () -> Unit,
    onSetOllamaCloudBaseUrl: (String) -> Unit,
    onSetOllamaCloudApiKey: (String) -> Unit,
    onSetOllamaCloudModel: (String) -> Unit,
    onRefreshOllamaCloudModels: () -> Unit,
    onTestOllamaCloudConnection: () -> Unit,
    openRouterModels: List<String>,
    isOpenRouterModelsLoading: Boolean,
    isTestingOpenRouterConnection: Boolean,
    openRouterApiTestStatus: ProviderApiTestStatus,
    openRouterApiTestMessage: String?,
    deepSeekModels: List<String>,
    isDeepSeekModelsLoading: Boolean,
    isTestingDeepSeekConnection: Boolean,
    deepSeekApiTestStatus: ProviderApiTestStatus,
    deepSeekApiTestMessage: String?,
    mistralModels: List<String>,
    isMistralModelsLoading: Boolean,
    isTestingMistralConnection: Boolean,
    mistralApiTestStatus: ProviderApiTestStatus,
    mistralApiTestMessage: String?,
    nvidiaModels: List<String>,
    isNvidiaModelsLoading: Boolean,
    isTestingNvidiaConnection: Boolean,
    nvidiaApiTestStatus: ProviderApiTestStatus,
    nvidiaApiTestMessage: String?,
    ollamaCloudModels: List<String>,
    isOllamaCloudModelsLoading: Boolean,
    isTestingOllamaCloudConnection: Boolean,
    ollamaCloudApiTestStatus: ProviderApiTestStatus,
    ollamaCloudApiTestMessage: String?,
    onDismiss: () -> Unit,
) {
    val modelEntries = remember {
        listOf(
            "gemini-3-flash-preview" to "Gemini 3 Flash",
            "gemini-3-pro-preview" to "Gemini 3 Pro",
            "gemini-3.1-flash-lite-preview" to "Gemini 3.1 Flash Lite",
        )
    }
    val modelMap = remember(modelEntries) { modelEntries.toMap() }
    val speedPresets = remember {
        listOf(
            "100-1" to (100 to 1),
            "40-2" to (40 to 2),
            "50-2" to (50 to 2),
            "30-3" to (30 to 3),
        )
    }
    val openRouterAllModelEntries = remember(openRouterModels) {
        openRouterModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.endsWith(":free", ignoreCase = true) }
            .distinct()
            .sorted()
            .associateWith { it }
    }
    val deepSeekAllModelEntries = remember(deepSeekModels) {
        deepSeekModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .associateWith { it }
    }
    val mistralAllModelEntries = remember(mistralModels) {
        mistralModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .associateWith { it }
    }
    val nvidiaAllModelEntries = remember(nvidiaModels) {
        nvidiaModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .associateWith { it }
    }
    val ollamaCloudAllModelEntries = remember(ollamaCloudModels) {
        ollamaCloudModels
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .associateWith { name ->
                if (name in OLLAMA_CLOUD_FREE_MODELS) "$name (Free)" else name
            }
    }

    var tempKey by remember(readerSettings.geminiApiKey) { mutableStateOf(readerSettings.geminiApiKey) }
    var tempModel by remember(readerSettings.geminiModel) {
        mutableStateOf(
            when (readerSettings.geminiModel) {
                "gemini-3-flash" -> "gemini-3-flash-preview"
                "gemini-2.5-flash" -> "gemini-3.1-flash-lite-preview"
                else -> readerSettings.geminiModel
            },
        )
    }
    var tempBatch by remember(readerSettings.geminiBatchSize) {
        mutableStateOf(readerSettings.geminiBatchSize.toString())
    }
    var tempConcurrency by remember(readerSettings.geminiConcurrency) {
        mutableStateOf(readerSettings.geminiConcurrency.toString())
    }
    var tempRelaxed by remember(readerSettings.geminiRelaxedMode) { mutableStateOf(readerSettings.geminiRelaxedMode) }
    var tempDisableCache by remember(readerSettings.geminiDisableCache) {
        mutableStateOf(readerSettings.geminiDisableCache)
    }
    var tempReasoning by remember(readerSettings.geminiReasoningEffort) {
        mutableStateOf(readerSettings.geminiReasoningEffort)
    }
    var tempBudget by remember(readerSettings.geminiBudgetTokens) { mutableStateOf(readerSettings.geminiBudgetTokens) }
    var tempTemperature by remember(readerSettings.geminiTemperature) {
        mutableStateOf(readerSettings.geminiTemperature.toString())
    }
    var tempTopP by remember(readerSettings.geminiTopP) { mutableStateOf(readerSettings.geminiTopP.toString()) }
    var tempTopK by remember(readerSettings.geminiTopK) { mutableStateOf(readerSettings.geminiTopK.toString()) }
    var tempPromptMode by remember(readerSettings.geminiPromptMode) { mutableStateOf(readerSettings.geminiPromptMode) }
    var tempSourceLang by remember(readerSettings.geminiSourceLang) { mutableStateOf(readerSettings.geminiSourceLang) }
    var tempTargetLang by remember(readerSettings.geminiTargetLang) { mutableStateOf(readerSettings.geminiTargetLang) }
    var tempStylePreset by remember(readerSettings.geminiStylePreset) {
        mutableStateOf(readerSettings.geminiStylePreset)
    }
    var tempEnabledModifiers by remember(readerSettings.geminiEnabledPromptModifiers) {
        mutableStateOf(readerSettings.geminiEnabledPromptModifiers.toSet())
    }
    var tempCustomModifier by remember(readerSettings.geminiCustomPromptModifier) {
        mutableStateOf(readerSettings.geminiCustomPromptModifier)
    }
    var tempAutoTranslateEnglish by remember(readerSettings.geminiAutoTranslateEnglishSource) {
        mutableStateOf(readerSettings.geminiAutoTranslateEnglishSource)
    }
    var tempPrefetchNextChapterTranslation by remember(readerSettings.geminiPrefetchNextChapterTranslation) {
        mutableStateOf(readerSettings.geminiPrefetchNextChapterTranslation)
    }
    var tempProvider by remember(readerSettings.translationProvider) {
        mutableStateOf(readerSettings.translationProvider)
    }
    var tempPrivatePythonLikeMode by remember(readerSettings.geminiPrivatePythonLikeMode) {
        mutableStateOf(readerSettings.geminiPrivatePythonLikeMode)
    }
    val isPrivateProviderInstalled = remember { GeminiPrivateBridge.isInstalled() }
    val privateProviderFallbackLabel = stringResource(
        AYMR.strings.novel_reader_translation_provider_gemini_private,
    )
    val privateProviderLabel = remember(isPrivateProviderInstalled, privateProviderFallbackLabel) {
        if (isPrivateProviderInstalled) GeminiPrivateBridge.providerLabel() else privateProviderFallbackLabel
    }

    val visibilityOnLabel = stringResource(AYMR.strings.novel_reader_gemini_visibility_on)
    val visibilityOffLabel = stringResource(AYMR.strings.novel_reader_gemini_visibility_off)
    val reasoningMinimalLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_minimal)
    val reasoningLowLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_low)
    val reasoningMediumLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_medium)
    val reasoningHighLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_high)
    val providerLabel = stringResource(AYMR.strings.novel_reader_translation_provider)
    val geminiModelLabel = stringResource(AYMR.strings.novel_reader_gemini_model)
    val openRouterModelLabel = stringResource(AYMR.strings.novel_reader_openrouter_model)
    val deepSeekModelLabel = stringResource(AYMR.strings.novel_reader_deepseek_model)
    val mistralModelLabel = stringResource(AYMR.strings.novel_reader_mistral_model)
    val nvidiaModelLabel = stringResource(AYMR.strings.novel_reader_nvidia_model)
    val ollamaCloudModelLabel = stringResource(AYMR.strings.novel_reader_ollama_cloud_model)
    val promptModeLabel = stringResource(AYMR.strings.novel_reader_gemini_prompt_mode)
    val styleLabel = stringResource(AYMR.strings.novel_reader_ai_translator_style_title)
    val speedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_speed_batch_parallelism)
    val reasoningLabel = stringResource(AYMR.strings.novel_reader_gemini_reasoning_effort)
    val autoEnglishLabel = stringResource(AYMR.strings.novel_reader_translation_auto_english_title)
    val prefetchNextLabel = stringResource(AYMR.strings.novel_reader_translation_prefetch_next_title)
    val privatePythonLikeLabel = stringResource(AYMR.strings.novel_reader_gemini_private_python_like_mode)
    val geminiProviderLabel = stringResource(AYMR.strings.novel_reader_translation_provider_gemini)
    val generationLabel = stringResource(AYMR.strings.novel_reader_ai_translator_generation_title)
    val temperatureLabel = stringResource(AYMR.strings.novel_reader_gemini_temperature)
    val topPLabel = stringResource(AYMR.strings.novel_reader_gemini_top_p)
    val topKLabel = stringResource(AYMR.strings.novel_reader_gemini_top_k)
    val relaxedStateLabel = stringResource(AYMR.strings.novel_reader_ai_translator_relaxed_state)
    val cacheStateLabel = stringResource(AYMR.strings.novel_reader_ai_translator_cache_state)
    val bridgeLockedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_bridge_locked)
    val bridgeEnterPasswordLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_bridge_enter_password)
    val bridgeUnlockedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_bridge_unlocked)
    val bridgeDebugLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_bridge_debug)
    val invalidBridgePasswordLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_invalid_bridge_password)
    val cacheClearedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_cache_cleared)
    val customPromptUpdatedLabel = stringResource(AYMR.strings.novel_reader_ai_translator_log_custom_prompt_updated)

    fun visibilityStateLabel(enabled: Boolean): String = if (enabled) {
        visibilityOnLabel
    } else {
        visibilityOffLabel
    }

    fun reasoningDisplayLabel(option: String): String = when (option) {
        "none" -> "OFF"
        "max" -> "MAX"
        "minimal" -> reasoningMinimalLabel
        "low" -> reasoningLowLabel
        "medium" -> reasoningMediumLabel
        "high" -> reasoningHighLabel
        else -> option.uppercase()
    }

    fun logPair(prefix: String, value: String) {
        onAddLog("$prefix: $value")
    }

    fun logState(prefix: String, enabled: Boolean) {
        onAddLog("$prefix: ${visibilityStateLabel(enabled)}")
    }

    fun logTemplate(template: String, vararg args: Any?) {
        onAddLog(template.format(*args))
    }

    var tempPrivatePassword by remember { mutableStateOf("") }
    var isPrivateProviderUnlocked by remember(isPrivateProviderInstalled, readerSettings.geminiPrivateUnlocked) {
        mutableStateOf(
            isPrivateProviderInstalled &&
                (readerSettings.geminiPrivateUnlocked || GeminiPrivateBridge.isUnlocked()),
        )
    }
    var tempOpenRouterBaseUrl by remember(readerSettings.openRouterBaseUrl) {
        mutableStateOf(readerSettings.openRouterBaseUrl)
    }
    var tempOpenRouterModel by remember(readerSettings.openRouterModel) {
        mutableStateOf(readerSettings.openRouterModel)
    }
    var tempDeepSeekBaseUrl by remember(readerSettings.deepSeekBaseUrl) {
        mutableStateOf(readerSettings.deepSeekBaseUrl)
    }
    var tempDeepSeekModel by remember(readerSettings.deepSeekModel) {
        mutableStateOf(readerSettings.deepSeekModel)
    }
    var tempMistralBaseUrl by remember(readerSettings.mistralBaseUrl) {
        mutableStateOf(readerSettings.mistralBaseUrl)
    }
    var tempNvidiaBaseUrl by remember(readerSettings.nvidiaBaseUrl) {
        mutableStateOf(readerSettings.nvidiaBaseUrl)
    }
    var tempMistralModel by remember(readerSettings.mistralModel) {
        mutableStateOf(readerSettings.mistralModel)
    }
    var tempNvidiaModel by remember(readerSettings.nvidiaModel) {
        mutableStateOf(readerSettings.nvidiaModel)
    }
    var tempOllamaCloudBaseUrl by remember(readerSettings.ollamaCloudBaseUrl) {
        mutableStateOf(readerSettings.ollamaCloudBaseUrl)
    }
    var tempOllamaCloudModel by remember(readerSettings.ollamaCloudModel) {
        mutableStateOf(readerSettings.ollamaCloudModel)
    }
    var showGenerationConfig by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showCustomPromptDialog by remember { mutableStateOf(false) }

    data class GenerationPreset(
        val id: String,
        val title: String,
        val temperature: Float,
        val topP: Float,
        val topK: Int?,
        val scenario: String,
        val advantage: String,
    )

    val defaultGenerationPresets = listOf(
        GenerationPreset(
            id = "anchor_plus",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_anchor_plus_title),
            temperature = 0.62f,
            topP = 0.9f,
            topK = 36,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_anchor_plus_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_anchor_plus_advantage),
        ),
        GenerationPreset(
            id = "authorial",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_authorial_title),
            temperature = 0.76f,
            topP = 0.93f,
            topK = 48,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_authorial_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_authorial_advantage),
        ),
        GenerationPreset(
            id = "dialogue_plus",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_dialogue_plus_title),
            temperature = 0.88f,
            topP = 0.95f,
            topK = 56,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_dialogue_plus_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_dialogue_plus_advantage),
        ),
        GenerationPreset(
            id = "private_pulse",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_private_pulse_title),
            temperature = 0.98f,
            topP = 0.97f,
            topK = 72,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_private_pulse_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_private_pulse_advantage),
        ),
        GenerationPreset(
            id = "unbound",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_unbound_title),
            temperature = 1.08f,
            topP = 0.985f,
            topK = 96,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_unbound_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_unbound_advantage),
        ),
    )
    val deepSeekGenerationPresets = listOf(
        GenerationPreset(
            id = "deepseek_balanced",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_balanced_title),
            temperature = 1.3f,
            topP = 0.9f,
            topK = null,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_balanced_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_balanced_advantage),
        ),
        GenerationPreset(
            id = "deepseek_expressive",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_expressive_title),
            temperature = 1.4f,
            topP = 0.93f,
            topK = null,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_expressive_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_expressive_advantage),
        ),
        GenerationPreset(
            id = "deepseek_creative",
            title = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_creative_title),
            temperature = 1.5f,
            topP = 0.95f,
            topK = null,
            scenario = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_creative_scenario),
            advantage = stringResource(AYMR.strings.novel_reader_gemini_generation_deepseek_creative_advantage),
        ),
    )
    val stylePresets = remember { NovelTranslationStylePresets.all }
    fun resolveSelectedGenerationPresetId(
        provider: NovelTranslationProvider,
        temperature: Float,
        topP: Float,
        topK: Int,
    ): String {
        val presets = if (provider == NovelTranslationProvider.DEEPSEEK) {
            deepSeekGenerationPresets
        } else {
            defaultGenerationPresets
        }
        if (presets.isEmpty()) return ""
        val epsilon = 0.0001f
        presets.firstOrNull { preset ->
            val tempMatch = abs(preset.temperature - temperature) <= epsilon
            val topPMatch = abs(preset.topP - topP) <= epsilon
            val topKMatch = when {
                provider == NovelTranslationProvider.DEEPSEEK -> true
                preset.topK == null -> true
                else -> preset.topK == topK
            }
            tempMatch && topPMatch && topKMatch
        }?.let { return it.id }

        return presets.minByOrNull { preset ->
            val topKDistance = when {
                provider == NovelTranslationProvider.DEEPSEEK -> 0f
                preset.topK == null -> 0f
                else -> abs((topK - preset.topK).toFloat()) / 100f
            }
            abs(preset.temperature - temperature) + abs(preset.topP - topP) + topKDistance
        }?.id ?: presets.first().id
    }
    var selectedGenerationPresetId by remember(
        tempProvider,
        readerSettings.geminiTemperature,
        readerSettings.geminiTopP,
        readerSettings.geminiTopK,
    ) {
        mutableStateOf(
            resolveSelectedGenerationPresetId(
                provider = tempProvider,
                temperature = readerSettings.geminiTemperature,
                topP = readerSettings.geminiTopP,
                topK = readerSettings.geminiTopK,
            ),
        )
    }

    fun applyBatchAndConcurrency() {
        tempBatch.toIntOrNull()?.let {
            onSetGeminiBatchSize(it.coerceIn(1, 100))
        }
        val maxConcurrency = if (tempProvider == NovelTranslationProvider.DEEPSEEK) 32 else 8
        tempConcurrency.toIntOrNull()?.let {
            onSetGeminiConcurrency(it.coerceIn(1, maxConcurrency))
        }
    }

    val progressValue = translationProgress.coerceIn(0, 100) / 100f
    val uiState = resolveGeminiTranslationUiState(
        isTranslating = isTranslating,
        hasCache = hasCache,
        isVisible = isVisible,
        translationProgress = translationProgress,
    )
    val status = geminiStatusPresentation(uiState)
    val hasTranslationResult = hasCache || translationProgress >= 100
    val isGeminiSelected = tempProvider == NovelTranslationProvider.GEMINI
    val isGeminiPrivateSelected = tempProvider == NovelTranslationProvider.GEMINI_PRIVATE
    val isGeminiFamilySelected = isGeminiSelected || isGeminiPrivateSelected
    val isPrivateSingleRequestMode =
        isGeminiPrivateSelected &&
            isPrivateProviderInstalled &&
            GeminiPrivateBridge.forceSingleChapterRequest()
    val privateBridgeInstalled = isGeminiPrivateSelected && isPrivateProviderInstalled
    val privateBridgeRequiresUnlock = privateBridgeInstalled
    val privateBridgeUnlocked = !privateBridgeRequiresUnlock || isPrivateProviderUnlocked
    val isOpenRouterSelected = tempProvider == NovelTranslationProvider.OPENROUTER
    val isDeepSeekSelected = tempProvider == NovelTranslationProvider.DEEPSEEK
    val isMistralSelected = tempProvider == NovelTranslationProvider.MISTRAL
    val isNvidiaSelected = tempProvider == NovelTranslationProvider.NVIDIA
    val isOllamaCloudSelected = tempProvider == NovelTranslationProvider.OLLAMA_CLOUD
    val activeReasoningModel = when (tempProvider) {
        NovelTranslationProvider.GEMINI,
        NovelTranslationProvider.GEMINI_PRIVATE,
        -> tempModel
        NovelTranslationProvider.OPENROUTER -> tempOpenRouterModel
        NovelTranslationProvider.MISTRAL -> tempMistralModel
        NovelTranslationProvider.DEEPSEEK -> tempDeepSeekModel
        NovelTranslationProvider.NVIDIA -> tempNvidiaModel
        NovelTranslationProvider.OLLAMA_CLOUD -> tempOllamaCloudModel
    }
    val reasoningOptions = remember(tempProvider, activeReasoningModel) {
        resolveTranslationReasoningOptions(tempProvider, activeReasoningModel)
    }
    val activeGenerationPresets = if (isDeepSeekSelected) {
        deepSeekGenerationPresets
    } else {
        defaultGenerationPresets
    }
    val tabTitles = persistentListOf(
        stringResource(MR.strings.ai_translator_tab_basics),
        stringResource(MR.strings.ai_translator_tab_prompt),
        stringResource(MR.strings.ai_translator_tab_more),
    )

    LaunchedEffect(isPrivateProviderInstalled, readerSettings.geminiPrivateUnlocked) {
        isPrivateProviderUnlocked = isPrivateProviderInstalled &&
            (readerSettings.geminiPrivateUnlocked || GeminiPrivateBridge.isUnlocked())
    }

    LaunchedEffect(isOpenRouterSelected, openRouterModels.size) {
        if (isOpenRouterSelected && openRouterModels.isEmpty()) {
            onRefreshOpenRouterModels()
        }
    }

    LaunchedEffect(isDeepSeekSelected, deepSeekModels.size) {
        if (isDeepSeekSelected && deepSeekModels.isEmpty()) {
            onRefreshDeepSeekModels()
        }
    }

    LaunchedEffect(isMistralSelected, mistralModels.size) {
        if (isMistralSelected && mistralModels.isEmpty()) {
            onRefreshMistralModels()
        }
    }

    LaunchedEffect(isNvidiaSelected, nvidiaModels.size) {
        if (isNvidiaSelected && nvidiaModels.isEmpty()) {
            onRefreshNvidiaModels()
        }
    }

    LaunchedEffect(isOllamaCloudSelected, ollamaCloudModels.size) {
        if (isOllamaCloudSelected && ollamaCloudModels.isEmpty()) {
            onRefreshOllamaCloudModels()
        }
    }

    TabbedDialog(
        onDismissRequest = onDismiss,
        tabTitles = tabTitles,
        enableSwipeDismiss = false,
    ) { page ->
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(AYMR.strings.novel_reader_ai_translator_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (page == 0) {
                GeminiSettingsBlock(
                    title = stringResource(AYMR.strings.novel_reader_ai_translator_status_title),
                    subtitle = stringResource(AYMR.strings.novel_reader_ai_translator_status_summary),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(status.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "$translationProgress%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = stringResource(status.subtitleRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = buildString {
                                    append(stringResource(AYMR.strings.novel_reader_translation_provider))
                                    append(": ")
                                    append(getAiTranslatorProviderLabel(tempProvider))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${tempSourceLang.ifBlank { "?" }} → ${tempTargetLang.ifBlank { "?" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (isTranslating) {
                                    onStop()
                                } else {
                                    if (privateBridgeUnlocked) {
                                        onStart()
                                    } else {
                                        logTemplate(
                                            bridgeLockedLabel,
                                            privateProviderLabel,
                                        )
                                    }
                                }
                            },
                            enabled = isTranslating || privateBridgeUnlocked,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (isTranslating) {
                                    stringResource(AYMR.strings.novel_reader_ai_translator_action_stop)
                                } else {
                                    stringResource(AYMR.strings.novel_reader_gemini_action_start)
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = onToggleVisibility,
                            enabled = hasTranslationResult,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (isVisible) {
                                    stringResource(AYMR.strings.novel_reader_gemini_show_original)
                                } else {
                                    stringResource(AYMR.strings.novel_reader_gemini_show_translation)
                                },
                            )
                        }
                    }

                    if (hasTranslationResult) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onClear) {
                                Text(
                                    stringResource(
                                        AYMR.strings.novel_reader_ai_translator_clear_chapter_cache,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (page == 0 || page == 1) {
                GeminiSettingsBlock(
                    title = stringResource(AYMR.strings.novel_reader_ai_translator_core_title),
                    subtitle = stringResource(AYMR.strings.novel_reader_ai_translator_core_summary),
                ) {
                    if (page == 0) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_translation_languages),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_translation_languages_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempSourceLang,
                                onValueChange = {
                                    tempSourceLang = it
                                    onSetGeminiSourceLang(it)
                                },
                                label = {
                                    Text(stringResource(AYMR.strings.novel_reader_gemini_source_lang))
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = tempTargetLang,
                                onValueChange = {
                                    tempTargetLang = it
                                    onSetGeminiTargetLang(it)
                                },
                                label = {
                                    Text(stringResource(AYMR.strings.novel_reader_gemini_target_lang))
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                        Text(
                            stringResource(AYMR.strings.novel_reader_translation_provider),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        AiTranslatorSupportText(
                            stringResource(AYMR.strings.novel_reader_ai_translator_provider_summary),
                        )
                        val providerCards = listOf(
                            NovelTranslationProvider.GEMINI to stringResource(
                                AYMR.strings.novel_reader_translation_provider_gemini,
                            ),
                            NovelTranslationProvider.GEMINI_PRIVATE to privateProviderLabel,
                            NovelTranslationProvider.OPENROUTER to stringResource(
                                AYMR.strings.novel_reader_translation_provider_openrouter,
                            ),
                            NovelTranslationProvider.DEEPSEEK to stringResource(
                                AYMR.strings.novel_reader_translation_provider_deepseek,
                            ),
                            NovelTranslationProvider.MISTRAL to stringResource(
                                AYMR.strings.novel_reader_translation_provider_mistral,
                            ),
                            NovelTranslationProvider.NVIDIA to stringResource(
                                AYMR.strings.novel_reader_translation_provider_nvidia,
                            ),
                            NovelTranslationProvider.OLLAMA_CLOUD to stringResource(
                                AYMR.strings.novel_reader_translation_provider_ollama_cloud,
                            ),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            providerCards.chunked(2).forEach { rowProviders ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    rowProviders.forEach { option ->
                                        val selected = tempProvider == option.first
                                        val apiConfigured = when (option.first) {
                                            NovelTranslationProvider.GEMINI -> tempKey.isNotBlank()
                                            NovelTranslationProvider.GEMINI_PRIVATE ->
                                                tempKey.isNotBlank() && isPrivateProviderUnlocked
                                            NovelTranslationProvider.OPENROUTER ->
                                                tempOpenRouterBaseUrl.isNotBlank() &&
                                                    readerSettings.openRouterApiKey.isNotBlank() &&
                                                    tempOpenRouterModel.isNotBlank()
                                            NovelTranslationProvider.DEEPSEEK ->
                                                tempDeepSeekBaseUrl.isNotBlank() &&
                                                    readerSettings.deepSeekApiKey.isNotBlank() &&
                                                    tempDeepSeekModel.isNotBlank()
                                            NovelTranslationProvider.MISTRAL ->
                                                tempMistralBaseUrl.isNotBlank() &&
                                                    readerSettings.mistralApiKey.isNotBlank() &&
                                                    tempMistralModel.isNotBlank()
                                            NovelTranslationProvider.NVIDIA ->
                                                tempNvidiaBaseUrl.isNotBlank() &&
                                                    readerSettings.nvidiaApiKey.isNotBlank() &&
                                                    tempNvidiaModel.isNotBlank()
                                            NovelTranslationProvider.OLLAMA_CLOUD ->
                                                tempOllamaCloudBaseUrl.isNotBlank() &&
                                                    readerSettings.ollamaCloudApiKey.isNotBlank() &&
                                                    tempOllamaCloudModel.isNotBlank()
                                        }
                                        AiTranslatorProviderCard(
                                            title = option.second,
                                            apiConfigured = apiConfigured,
                                            selected = selected,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                tempProvider = option.first
                                                onSetTranslationProvider(option.first)
                                                logPair(providerLabel, option.second)
                                                when (option.first) {
                                                    NovelTranslationProvider.GEMINI -> Unit
                                                    NovelTranslationProvider.GEMINI_PRIVATE -> Unit
                                                    NovelTranslationProvider.OPENROUTER -> onRefreshOpenRouterModels()
                                                    NovelTranslationProvider.DEEPSEEK -> onRefreshDeepSeekModels()
                                                    NovelTranslationProvider.MISTRAL -> onRefreshMistralModels()
                                                    NovelTranslationProvider.NVIDIA -> onRefreshNvidiaModels()
                                                    NovelTranslationProvider.OLLAMA_CLOUD ->
                                                        onRefreshOllamaCloudModels()
                                                }
                                            },
                                        )
                                    }
                                    if (rowProviders.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    if (page == 0) {
                        if (privateBridgeInstalled) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    text = if (isPrivateProviderUnlocked) {
                                        stringResource(
                                            AYMR.strings.novel_reader_gemini_private_bridge_connected_unlocked,
                                        ).format(privateProviderLabel)
                                    } else {
                                        stringResource(
                                            AYMR.strings.novel_reader_gemini_private_bridge_connected_unlock_required,
                                        ).format(privateProviderLabel)
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        if (privateBridgeRequiresUnlock && !isPrivateProviderUnlocked) {
                            OutlinedTextField(
                                value = tempPrivatePassword,
                                onValueChange = { tempPrivatePassword = it },
                                label = {
                                    Text(
                                        stringResource(
                                            AYMR.strings.novel_reader_gemini_private_bridge_password_label,
                                        ).format(privateProviderLabel),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val password = tempPrivatePassword.trim()
                                        if (password.isBlank()) {
                                            logTemplate(
                                                bridgeEnterPasswordLabel,
                                                privateProviderLabel,
                                            )
                                        } else {
                                            val unlocked = GeminiPrivateBridge.unlock(password)
                                            if (unlocked) {
                                                isPrivateProviderUnlocked = true
                                                onSetGeminiPrivateUnlocked(true)
                                                tempPrivatePassword = ""
                                                logTemplate(
                                                    bridgeUnlockedLabel,
                                                    privateProviderLabel,
                                                )
                                            } else {
                                                logTemplate(
                                                    bridgeDebugLabel,
                                                    privateProviderLabel,
                                                    GeminiPrivateBridge.debugInfo(),
                                                )
                                                onAddLog(invalidBridgePasswordLabel)
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(AYMR.strings.novel_reader_gemini_action_unlock))
                                }
                                OutlinedButton(
                                    onClick = {
                                        tempPrivatePassword = ""
                                    },
                                ) {
                                    Text(stringResource(AYMR.strings.novel_reader_gemini_action_clear))
                                }
                            }
                        }

                        when (tempProvider) {
                            NovelTranslationProvider.GEMINI,
                            NovelTranslationProvider.GEMINI_PRIVATE,
                            -> {
                                AiTranslatorMiniSection(
                                    title = stringResource(AYMR.strings.novel_reader_gemini_model),
                                    subtitle = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_model_summary,
                                    ),
                                )
                                eu.kanade.presentation.more.settings.widget.ListPreferenceWidget(
                                    value = tempModel,
                                    title = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_current_model,
                                    ),
                                    subtitle = modelMap[tempModel] ?: tempModel,
                                    icon = null,
                                    entries = modelMap,
                                    onValueChange = { selected ->
                                        tempModel = selected
                                        onSetGeminiModel(selected)
                                        logPair(geminiModelLabel, modelMap[selected] ?: selected)
                                    },
                                )
                            }
                            NovelTranslationProvider.OPENROUTER -> {
                                AiTranslatorMiniSection(
                                    title = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_openrouter_models_title,
                                    ),
                                    subtitle = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_model_summary,
                                    ),
                                )
                                if (openRouterAllModelEntries.isNotEmpty()) {
                                    eu.kanade.presentation.more.settings.widget.ListPreferenceWidget(
                                        value = tempOpenRouterModel,
                                        title = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_openrouter_models_count,
                                        ).format(openRouterAllModelEntries.size),
                                        subtitle = tempOpenRouterModel.ifBlank {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_choose_free_model,
                                            )
                                        },
                                        icon = null,
                                        entries = openRouterAllModelEntries,
                                        onValueChange = { selected ->
                                            tempOpenRouterModel = selected
                                            onSetOpenRouterModel(selected)
                                            logPair(openRouterModelLabel, selected)
                                        },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onRefreshOpenRouterModels) {
                                        Text(
                                            if (isOpenRouterModelsLoading) {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_loading_models,
                                                )
                                            } else {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                )
                                            },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = tempOpenRouterModel,
                                    onValueChange = {
                                        tempOpenRouterModel = it
                                        onSetOpenRouterModel(it)
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_model_id_free_only,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            NovelTranslationProvider.DEEPSEEK -> {
                                AiTranslatorMiniSection(
                                    title = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_deepseek_models_title,
                                    ),
                                    subtitle = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_model_summary,
                                    ),
                                )
                                if (deepSeekAllModelEntries.isNotEmpty()) {
                                    eu.kanade.presentation.more.settings.widget.ListPreferenceWidget(
                                        value = tempDeepSeekModel,
                                        title = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_models_count,
                                        ).format(deepSeekAllModelEntries.size),
                                        subtitle = tempDeepSeekModel.ifBlank {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_choose_model,
                                            )
                                        },
                                        icon = null,
                                        entries = deepSeekAllModelEntries,
                                        onValueChange = { selected ->
                                            tempDeepSeekModel = selected
                                            onSetDeepSeekModel(selected)
                                            logPair(deepSeekModelLabel, selected)
                                        },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onRefreshDeepSeekModels) {
                                        Text(
                                            if (isDeepSeekModelsLoading) {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_loading_models,
                                                )
                                            } else {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                )
                                            },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = tempDeepSeekModel,
                                    onValueChange = {
                                        tempDeepSeekModel = it
                                        onSetDeepSeekModel(it)
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_model_id,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            NovelTranslationProvider.MISTRAL -> {
                                AiTranslatorMiniSection(
                                    title = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_mistral_models_title,
                                    ),
                                    subtitle = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_model_summary,
                                    ),
                                )
                                if (mistralAllModelEntries.isNotEmpty()) {
                                    eu.kanade.presentation.more.settings.widget.ListPreferenceWidget(
                                        value = tempMistralModel,
                                        title = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_models_count,
                                        ).format(mistralAllModelEntries.size),
                                        subtitle = tempMistralModel.ifBlank {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_choose_model,
                                            )
                                        },
                                        icon = null,
                                        entries = mistralAllModelEntries,
                                        onValueChange = { selected ->
                                            tempMistralModel = selected
                                            onSetMistralModel(selected)
                                            logPair(mistralModelLabel, selected)
                                        },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onRefreshMistralModels) {
                                        Text(
                                            if (isMistralModelsLoading) {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_loading_models,
                                                )
                                            } else {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                )
                                            },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = tempMistralModel,
                                    onValueChange = {
                                        tempMistralModel = it
                                        onSetMistralModel(it)
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_model_id,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            NovelTranslationProvider.NVIDIA -> {
                                AiTranslatorMiniSection(
                                    title = stringResource(
                                        AYMR.strings.novel_reader_nvidia_section_title,
                                    ),
                                    subtitle = stringResource(
                                        AYMR.strings.novel_reader_nvidia_section_summary,
                                    ),
                                )
                                AiTranslatorSupportText(
                                    stringResource(AYMR.strings.novel_reader_ai_translator_model_summary),
                                )
                                if (nvidiaAllModelEntries.isNotEmpty()) {
                                    eu.kanade.presentation.more.settings.widget.ListPreferenceWidget(
                                        value = tempNvidiaModel,
                                        title = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_models_count,
                                        ).format(nvidiaAllModelEntries.size),
                                        subtitle = tempNvidiaModel.ifBlank {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_choose_model,
                                            )
                                        },
                                        icon = null,
                                        entries = nvidiaAllModelEntries,
                                        onValueChange = { selected ->
                                            tempNvidiaModel = selected
                                            onSetNvidiaModel(selected)
                                            logPair(nvidiaModelLabel, selected)
                                        },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onRefreshNvidiaModels) {
                                        Text(
                                            if (isNvidiaModelsLoading) {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_loading_models,
                                                )
                                            } else {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                )
                                            },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = tempNvidiaModel,
                                    onValueChange = {
                                        tempNvidiaModel = it
                                        onSetNvidiaModel(it)
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                AYMR.strings.novel_reader_nvidia_model,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            NovelTranslationProvider.OLLAMA_CLOUD -> {
                                AiTranslatorMiniSection(
                                    title = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_ollama_cloud_models_title,
                                    ),
                                    subtitle = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_model_summary,
                                    ),
                                )
                                if (ollamaCloudAllModelEntries.isNotEmpty()) {
                                    eu.kanade.presentation.more.settings.widget.ListPreferenceWidget(
                                        value = tempOllamaCloudModel,
                                        title = stringResource(
                                            AYMR.strings.novel_reader_ai_translator_models_count,
                                        ).format(ollamaCloudAllModelEntries.size),
                                        subtitle = when {
                                            tempOllamaCloudModel in OLLAMA_CLOUD_FREE_MODELS ->
                                                "$tempOllamaCloudModel (Free)"
                                            tempOllamaCloudModel.isNotBlank() -> tempOllamaCloudModel
                                            else -> stringResource(
                                                AYMR.strings.novel_reader_ai_translator_choose_model,
                                            )
                                        },
                                        icon = null,
                                        entries = ollamaCloudAllModelEntries,
                                        onValueChange = { selected ->
                                            tempOllamaCloudModel = selected
                                            onSetOllamaCloudModel(selected)
                                            logPair(ollamaCloudModelLabel, selected)
                                        },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onRefreshOllamaCloudModels) {
                                        Text(
                                            if (isOllamaCloudModelsLoading) {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_loading_models,
                                                )
                                            } else {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_refresh_list,
                                                )
                                            },
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = tempOllamaCloudModel,
                                    onValueChange = {
                                        tempOllamaCloudModel = it
                                        onSetOllamaCloudModel(it)
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                AYMR.strings.novel_reader_ollama_cloud_model,
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    if (page == 1) {
                        if (!isPrivateSingleRequestMode) {
                            Text(
                                stringResource(AYMR.strings.novel_reader_gemini_prompt_mode),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            AiTranslatorSupportText(
                                stringResource(AYMR.strings.novel_reader_ai_translator_prompt_mode_summary),
                            )
                            val promptModeClassicLabel = stringResource(
                                AYMR.strings.novel_reader_gemini_prompt_mode_classic,
                            )
                            val promptModeAdultLabel = stringResource(
                                AYMR.strings.novel_reader_gemini_prompt_mode_adult_short,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(
                                    listOf(
                                        GeminiPromptMode.CLASSIC to promptModeClassicLabel,
                                        GeminiPromptMode.ADULT_18 to promptModeAdultLabel,
                                    ),
                                ) { option ->
                                    val selected = tempPromptMode == option.first
                                    AiTranslatorChoiceChip(
                                        text = option.second,
                                        selected = selected,
                                        onClick = {
                                            tempPromptMode = option.first
                                            onSetGeminiPromptMode(option.first)
                                            logPair(promptModeLabel, option.second)
                                        },
                                    )
                                }
                            }

                            Text(
                                stringResource(AYMR.strings.novel_reader_ai_translator_style_title),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            AiTranslatorSupportText(
                                stringResource(AYMR.strings.novel_reader_ai_translator_style_summary),
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(stylePresets) { preset ->
                                    val selected = tempStylePreset == preset.id
                                    val presetTitle = stringResource(preset.titleRes)
                                    AiTranslatorChoiceChip(
                                        text = presetTitle,
                                        selected = selected,
                                        onClick = {
                                            tempStylePreset = preset.id
                                            onSetGeminiStylePreset(preset.id)
                                            logPair(styleLabel, presetTitle)
                                        },
                                    )
                                }
                            }
                            val selectedStylePreset = stylePresets.firstOrNull { it.id == tempStylePreset }
                            val selectedStylePresetTitle = stringResource(
                                selectedStylePreset?.titleRes ?: stylePresets.first().titleRes,
                            )
                            if (selectedStylePreset != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = selectedStylePresetTitle,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_reader_ai_translator_style_scenario_prefix,
                                            ).format(stringResource(selectedStylePreset.scenarioRes)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = stringResource(
                                                AYMR.strings.novel_reader_ai_translator_style_advantage_prefix,
                                            ).format(stringResource(selectedStylePreset.advantageRes)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            Text(
                                stringResource(AYMR.strings.novel_reader_gemini_prompt_modifiers),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            AiTranslatorSupportText(
                                stringResource(AYMR.strings.novel_reader_gemini_prompt_modifiers_hint),
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(GeminiPromptModifiers.all) { modifier ->
                                    val selected = tempEnabledModifiers.contains(modifier.id)
                                    val modifierLabel = stringResource(modifier.labelRes)
                                    Surface(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.clickable {
                                            tempEnabledModifiers = if (selected) {
                                                tempEnabledModifiers - modifier.id
                                            } else {
                                                tempEnabledModifiers + modifier.id
                                            }
                                            onSetGeminiEnabledPromptModifiers(
                                                tempEnabledModifiers.toList(),
                                            )
                                        },
                                    ) {
                                        Text(
                                            text = modifierLabel,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                                item {
                                    Surface(
                                        color = if (tempCustomModifier.isNotBlank()) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.clickable { showCustomPromptDialog = true },
                                    ) {
                                        Text(
                                            text = if (tempCustomModifier.isBlank()) {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_custom_modifier_add,
                                                )
                                            } else {
                                                stringResource(
                                                    AYMR.strings.novel_reader_ai_translator_custom_modifier_active,
                                                )
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        AYMR.strings.novel_reader_gemini_private_bridge_auto_rules,
                                    ).format(privateProviderLabel),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    if (page == 0 && !isPrivateSingleRequestMode) {
                        Text(
                            stringResource(AYMR.strings.novel_reader_ai_translator_speed_batch_parallelism),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(speedPresets) { preset ->
                                val label = preset.first
                                val batch = preset.second.first
                                val concurrency = preset.second.second
                                val selected = tempBatch == batch.toString() &&
                                    tempConcurrency == concurrency.toString()
                                AiTranslatorChoiceChip(
                                    text = label,
                                    selected = selected,
                                    onClick = {
                                        tempBatch = batch.toString()
                                        tempConcurrency = concurrency.toString()
                                        onSetGeminiBatchSize(batch)
                                        onSetGeminiConcurrency(concurrency)
                                        logPair(speedLabel, label)
                                    },
                                )
                            }
                        }
                    }

                    if (page == 0 && isPrivateSingleRequestMode) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text =
                                "$privateProviderLabel: отправка идёт " +
                                    "одним запросом на главу. При ошибке " +
                                    "включается fallback (batch=40, " +
                                    "concurrency=1).",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    if (page == 0 && reasoningOptions.isNotEmpty()) {
                        Text(
                            text = reasoningLabel,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (isDeepSeekSelected && tempReasoning != "none") {
                            AiTranslatorSupportText(
                                stringResource(
                                    AYMR.strings.novel_reader_ai_translator_deepseek_reasoning_hint,
                                ),
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(reasoningOptions) { option ->
                                OutlinedButton(
                                    onClick = {
                                        tempReasoning = option
                                        onSetGeminiReasoningEffort(option)
                                        logPair(reasoningLabel, reasoningDisplayLabel(option))
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (tempReasoning == option) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    ),
                                ) {
                                    Text(
                                        reasoningDisplayLabel(option),
                                        fontWeight = if (tempReasoning == option) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (page == 2) {
                val apiTestStatus = when (tempProvider) {
                    NovelTranslationProvider.OPENROUTER -> openRouterApiTestStatus
                    NovelTranslationProvider.DEEPSEEK -> deepSeekApiTestStatus
                    NovelTranslationProvider.MISTRAL -> mistralApiTestStatus
                    NovelTranslationProvider.NVIDIA -> nvidiaApiTestStatus
                    NovelTranslationProvider.OLLAMA_CLOUD -> ollamaCloudApiTestStatus
                    NovelTranslationProvider.GEMINI,
                    NovelTranslationProvider.GEMINI_PRIVATE,
                    -> ProviderApiTestStatus.Idle
                }
                val apiTestMessage = when (tempProvider) {
                    NovelTranslationProvider.OPENROUTER -> openRouterApiTestMessage
                    NovelTranslationProvider.DEEPSEEK -> deepSeekApiTestMessage
                    NovelTranslationProvider.MISTRAL -> mistralApiTestMessage
                    NovelTranslationProvider.NVIDIA -> nvidiaApiTestMessage
                    NovelTranslationProvider.OLLAMA_CLOUD -> ollamaCloudApiTestMessage
                    NovelTranslationProvider.GEMINI,
                    NovelTranslationProvider.GEMINI_PRIVATE,
                    -> null
                }
                GeminiSettingsBlock(
                    title = stringResource(AYMR.strings.novel_reader_ai_translator_system_title),
                    subtitle = stringResource(AYMR.strings.novel_reader_ai_translator_system_summary),
                ) {
                    AiTranslatorPanelCard(
                        title = stringResource(AYMR.strings.novel_reader_ai_translator_more_automation_title),
                        subtitle = stringResource(
                            AYMR.strings.novel_reader_ai_translator_system_automation_summary,
                        ),
                    ) {
                        AiTranslatorToggleRow(
                            title = stringResource(AYMR.strings.novel_reader_translation_auto_english_title),
                            subtitle = stringResource(
                                AYMR.strings.novel_reader_ai_translator_more_auto_english_summary,
                            ),
                            checked = tempAutoTranslateEnglish,
                            onCheckedChange = { enabled ->
                                tempAutoTranslateEnglish = enabled
                                onSetGeminiAutoTranslateEnglishSource(enabled)
                                logState(autoEnglishLabel, enabled)
                            },
                        )
                        AiTranslatorToggleRow(
                            title = stringResource(AYMR.strings.novel_reader_translation_prefetch_next_title),
                            subtitle = stringResource(
                                AYMR.strings.novel_reader_ai_translator_more_prefetch_summary,
                            ),
                            checked = tempPrefetchNextChapterTranslation,
                            onCheckedChange = { enabled ->
                                tempPrefetchNextChapterTranslation = enabled
                                onSetGeminiPrefetchNextChapterTranslation(enabled)
                                logState(prefetchNextLabel, enabled)
                            },
                        )
                        AiTranslatorToggleRow(
                            title = stringResource(AYMR.strings.novel_reader_ai_translator_more_cache_title),
                            subtitle = stringResource(
                                AYMR.strings.novel_reader_ai_translator_more_cache_summary,
                            ),
                            checked = !tempDisableCache,
                            onCheckedChange = { enabled ->
                                tempDisableCache = !enabled
                                onSetGeminiDisableCache(tempDisableCache)
                                onAddLog(cacheStateLabel.format(visibilityStateLabel(!tempDisableCache)))
                            },
                        )
                        if (isGeminiPrivateSelected) {
                            AiTranslatorToggleRow(
                                title = stringResource(AYMR.strings.novel_reader_gemini_private_python_like_mode),
                                subtitle = stringResource(
                                    AYMR.strings.novel_reader_ai_translator_more_private_python_summary,
                                ),
                                checked = tempPrivatePythonLikeMode,
                                onCheckedChange = { enabled ->
                                    tempPrivatePythonLikeMode = enabled
                                    onSetGeminiPrivatePythonLikeMode(enabled)
                                    logState(privatePythonLikeLabel, enabled)
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onClearAllCache()
                                onAddLog(cacheClearedLabel)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(AYMR.strings.novel_reader_ai_translator_clear_all_cache))
                        }
                    }

                    AiTranslatorPanelCard(
                        title = stringResource(AYMR.strings.novel_reader_ai_translator_more_connection_title),
                        subtitle = stringResource(
                            AYMR.strings.novel_reader_ai_translator_system_connection_summary,
                        ),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        ) {
                            Text(
                                text = stringResource(
                                    AYMR.strings.novel_reader_ai_translator_more_active_provider,
                                ).format(getAiTranslatorProviderLabel(tempProvider)),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (isOpenRouterSelected ||
                            isDeepSeekSelected ||
                            isMistralSelected ||
                            isNvidiaSelected ||
                            isOllamaCloudSelected
                        ) {
                            AiTranslatorSupportText(
                                stringResource(AYMR.strings.novel_reader_ai_translator_more_base_url_summary),
                            )
                            OutlinedTextField(
                                value = when {
                                    isOpenRouterSelected -> tempOpenRouterBaseUrl
                                    isDeepSeekSelected -> tempDeepSeekBaseUrl
                                    isMistralSelected -> tempMistralBaseUrl
                                    isNvidiaSelected -> tempNvidiaBaseUrl
                                    else -> tempOllamaCloudBaseUrl
                                },
                                onValueChange = {
                                    if (isOpenRouterSelected) {
                                        tempOpenRouterBaseUrl = it
                                        onSetOpenRouterBaseUrl(it)
                                    } else if (isDeepSeekSelected) {
                                        tempDeepSeekBaseUrl = it
                                        onSetDeepSeekBaseUrl(it)
                                    } else if (isMistralSelected) {
                                        tempMistralBaseUrl = it
                                        onSetMistralBaseUrl(it)
                                    } else if (isNvidiaSelected) {
                                        tempNvidiaBaseUrl = it
                                        onSetNvidiaBaseUrl(it)
                                    } else {
                                        tempOllamaCloudBaseUrl = it
                                        onSetOllamaCloudBaseUrl(it)
                                    }
                                },
                                label = {
                                    Text(
                                        when {
                                            isOpenRouterSelected -> stringResource(
                                                AYMR.strings.novel_reader_openrouter_base_url,
                                            )
                                            isDeepSeekSelected -> stringResource(
                                                AYMR.strings.novel_reader_deepseek_base_url,
                                            )
                                            isMistralSelected -> stringResource(
                                                AYMR.strings.novel_reader_mistral_base_url,
                                            )
                                            isNvidiaSelected -> stringResource(
                                                AYMR.strings.novel_reader_nvidia_base_url,
                                            )
                                            else -> stringResource(
                                                AYMR.strings.novel_reader_ollama_cloud_base_url,
                                            )
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                        AiTranslatorSupportText(
                            stringResource(AYMR.strings.novel_reader_ai_translator_more_api_key_summary),
                        )
                        val apiKeyUrl = getApiKeyUrl(tempProvider)
                        if (apiKeyUrl != null) {
                            val uriHandler = LocalUriHandler.current
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { uriHandler.openUri(apiKeyUrl) }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(stringResource(AYMR.strings.novel_reader_ai_translator_get_api_key))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = when {
                                isOpenRouterSelected -> readerSettings.openRouterApiKey
                                isDeepSeekSelected -> readerSettings.deepSeekApiKey
                                isMistralSelected -> readerSettings.mistralApiKey
                                isNvidiaSelected -> readerSettings.nvidiaApiKey
                                isOllamaCloudSelected -> readerSettings.ollamaCloudApiKey
                                else -> tempKey
                            },
                            onValueChange = {
                                if (isOpenRouterSelected) {
                                    onSetOpenRouterApiKey(it)
                                } else if (isDeepSeekSelected) {
                                    onSetDeepSeekApiKey(it)
                                } else if (isMistralSelected) {
                                    onSetMistralApiKey(it)
                                } else if (isNvidiaSelected) {
                                    onSetNvidiaApiKey(it)
                                } else if (isOllamaCloudSelected) {
                                    onSetOllamaCloudApiKey(it)
                                } else {
                                    tempKey = it
                                    onSetGeminiApiKey(it)
                                }
                            },
                            label = {
                                Text(
                                    when {
                                        isOpenRouterSelected -> stringResource(
                                            AYMR.strings.novel_reader_openrouter_api_key,
                                        )
                                        isDeepSeekSelected -> stringResource(AYMR.strings.novel_reader_deepseek_api_key)
                                        isMistralSelected -> stringResource(AYMR.strings.novel_reader_mistral_api_key)
                                        isNvidiaSelected -> stringResource(AYMR.strings.novel_reader_nvidia_api_key)
                                        isOllamaCloudSelected -> stringResource(
                                            AYMR.strings.novel_reader_ollama_cloud_api_key,
                                        )
                                        else -> stringResource(AYMR.strings.novel_reader_gemini_api_key)
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        AiTranslatorToggleRow(
                            title = stringResource(AYMR.strings.novel_reader_ai_translator_more_relaxed_title),
                            subtitle = stringResource(
                                AYMR.strings.novel_reader_ai_translator_more_relaxed_summary,
                            ),
                            checked = tempRelaxed,
                            onCheckedChange = { enabled ->
                                tempRelaxed = enabled
                                onSetGeminiRelaxedMode(enabled)
                                onAddLog(relaxedStateLabel.format(visibilityStateLabel(enabled)))
                            },
                        )
                        if (isOpenRouterSelected || isDeepSeekSelected || isMistralSelected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AiTranslatorApiTestButton(
                                    status = apiTestStatus,
                                    onClick = when {
                                        isOpenRouterSelected -> onTestOpenRouterConnection
                                        isDeepSeekSelected -> onTestDeepSeekConnection
                                        else -> onTestMistralConnection
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = when {
                                        isOpenRouterSelected -> onRefreshOpenRouterModels
                                        isDeepSeekSelected -> onRefreshDeepSeekModels
                                        else -> onRefreshMistralModels
                                    },
                                    enabled = when {
                                        isOpenRouterSelected -> !isOpenRouterModelsLoading
                                        isDeepSeekSelected -> !isDeepSeekModelsLoading
                                        else -> !isMistralModelsLoading
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    val isLoading = when {
                                        isOpenRouterSelected -> isOpenRouterModelsLoading
                                        isDeepSeekSelected -> isDeepSeekModelsLoading
                                        else -> isMistralModelsLoading
                                    }
                                    Text(
                                        if (isLoading) {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_loading_models,
                                            )
                                        } else {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_refresh_models,
                                            )
                                        },
                                    )
                                }
                            }
                            if (!apiTestMessage.isNullOrBlank() &&
                                apiTestStatus == ProviderApiTestStatus.Error
                            ) {
                                AiTranslatorSupportText(apiTestMessage)
                            }
                        }
                        if (isNvidiaSelected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AiTranslatorApiTestButton(
                                    status = apiTestStatus,
                                    onClick = onTestNvidiaConnection,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = onRefreshNvidiaModels,
                                    enabled = !isNvidiaModelsLoading,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        if (isNvidiaModelsLoading) {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_loading_models,
                                            )
                                        } else {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_refresh_models,
                                            )
                                        },
                                    )
                                }
                            }
                            if (!apiTestMessage.isNullOrBlank() &&
                                apiTestStatus == ProviderApiTestStatus.Error
                            ) {
                                AiTranslatorSupportText(apiTestMessage)
                            }
                        }
                        if (isOllamaCloudSelected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AiTranslatorApiTestButton(
                                    status = apiTestStatus,
                                    onClick = onTestOllamaCloudConnection,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = onRefreshOllamaCloudModels,
                                    enabled = !isOllamaCloudModelsLoading,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        if (isOllamaCloudModelsLoading) {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_loading_models,
                                            )
                                        } else {
                                            stringResource(
                                                AYMR.strings.novel_reader_ai_translator_refresh_models,
                                            )
                                        },
                                    )
                                }
                            }
                            if (!apiTestMessage.isNullOrBlank() &&
                                apiTestStatus == ProviderApiTestStatus.Error
                            ) {
                                AiTranslatorSupportText(apiTestMessage)
                            }
                        }
                    }
                }
            }

            if (page == 1) {
                GeminiSettingsBlock(
                    title = stringResource(AYMR.strings.novel_reader_ai_translator_generation_title),
                    subtitle = stringResource(AYMR.strings.novel_reader_ai_translator_generation_summary),
                ) {
                    TextButton(onClick = { showGenerationConfig = !showGenerationConfig }) {
                        Text(
                            if (showGenerationConfig) {
                                stringResource(AYMR.strings.novel_reader_ai_translator_generation_hide)
                            } else {
                                stringResource(AYMR.strings.novel_reader_ai_translator_generation_show)
                            },
                        )
                    }
                    if (showGenerationConfig) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(activeGenerationPresets) { preset ->
                                val isSelected = preset.id == selectedGenerationPresetId
                                AiTranslatorChoiceChip(
                                    text = preset.title,
                                    selected = isSelected,
                                    onClick = {
                                        selectedGenerationPresetId = preset.id
                                        val name = preset.title
                                        val t = preset.temperature
                                        val p = preset.topP
                                        tempTemperature = t.toString()
                                        tempTopP = p.toString()
                                        onSetGeminiTemperature(t)
                                        onSetGeminiTopP(p)
                                        val k = preset.topK
                                        if (k != null) {
                                            tempTopK = k.toString()
                                            onSetGeminiTopK(k)
                                            onAddLog(
                                                "$generationLabel: $name (T:$t P:$p K:$k)",
                                            )
                                        } else {
                                            onAddLog(
                                                "$generationLabel: $name (T:$t P:$p)",
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        val selectedPreset = activeGenerationPresets.firstOrNull { it.id == selectedGenerationPresetId }
                            ?: activeGenerationPresets.first()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = selectedPreset.title,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_generation_scenario_prefix,
                                    ).format(selectedPreset.scenario),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(
                                        AYMR.strings.novel_reader_ai_translator_generation_advantage_prefix,
                                    ).format(selectedPreset.advantage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        AiTranslatorMiniSection(
                            title = stringResource(
                                AYMR.strings.novel_reader_ai_translator_speed_batch_parallelism,
                            ),
                            subtitle = stringResource(
                                AYMR.strings.novel_reader_ai_translator_generation_speed_summary,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempTemperature,
                                onValueChange = {
                                    tempTemperature = it
                                    it.toFloatOrNull()?.let { value ->
                                        val normalized = if (isDeepSeekSelected) {
                                            value.coerceIn(1.3f, 1.5f)
                                        } else {
                                            value
                                        }
                                        onSetGeminiTemperature(normalized)
                                        logPair(temperatureLabel, normalized.toString())
                                    }
                                },
                                label = { Text(stringResource(AYMR.strings.novel_reader_gemini_temperature)) },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = tempTopP,
                                onValueChange = {
                                    tempTopP = it
                                    it.toFloatOrNull()?.let { value ->
                                        val normalized = if (isDeepSeekSelected) {
                                            value.coerceIn(0.9f, 0.95f)
                                        } else {
                                            value
                                        }
                                        onSetGeminiTopP(normalized)
                                        logPair(topPLabel, normalized.toString())
                                    }
                                },
                                label = { Text(stringResource(AYMR.strings.novel_reader_gemini_top_p)) },
                                modifier = Modifier.weight(1f),
                            )
                            if (!isDeepSeekSelected) {
                                OutlinedTextField(
                                    value = tempTopK,
                                    onValueChange = {
                                        tempTopK = it
                                        it.toIntOrNull()?.let { value ->
                                            onSetGeminiTopK(value)
                                            logPair(topKLabel, value.toString())
                                        }
                                    },
                                    label = { Text(stringResource(AYMR.strings.novel_reader_gemini_top_k)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempBatch,
                                onValueChange = {
                                    tempBatch = it
                                    applyBatchAndConcurrency()
                                },
                                label = { Text(stringResource(AYMR.strings.novel_reader_gemini_batch_size)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = tempConcurrency,
                                onValueChange = {
                                    tempConcurrency = it
                                    applyBatchAndConcurrency()
                                },
                                label = { Text(stringResource(AYMR.strings.novel_reader_gemini_concurrency)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                        if (isDeepSeekSelected) {
                            Text(
                                text = stringResource(AYMR.strings.novel_reader_ai_translator_deepseek_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = stringResource(
                                AYMR.strings.novel_reader_ai_translator_max_batch_size_hint,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (page == 2) {
                GeminiSettingsBlock(
                    title = stringResource(AYMR.strings.novel_reader_ai_translator_logs_title),
                    subtitle = stringResource(AYMR.strings.novel_reader_ai_translator_logs_summary),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(AYMR.strings.novel_reader_ai_translator_logs_count).format(logs.size),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showLogs = !showLogs }) {
                                Text(
                                    if (showLogs) {
                                        stringResource(AYMR.strings.novel_reader_ai_translator_toggle_hide)
                                    } else {
                                        stringResource(AYMR.strings.novel_reader_ai_translator_toggle_show)
                                    },
                                )
                            }
                            TextButton(onClick = onClearLogs) {
                                Text(stringResource(AYMR.strings.novel_reader_gemini_action_clear))
                            }
                        }
                    }
                    if (showLogs) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (logs.isEmpty()) {
                                Text(
                                    stringResource(AYMR.strings.novel_reader_ai_translator_logs_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                logs.forEach { log ->
                                    Text(log, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomPromptDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPromptDialog = false },
            title = { Text(stringResource(AYMR.strings.novel_reader_ai_translator_custom_modifier_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tempCustomModifier,
                        onValueChange = { tempCustomModifier = it },
                        label = {
                            Text(stringResource(AYMR.strings.novel_reader_ai_translator_custom_instructions))
                        },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(AYMR.strings.novel_reader_ai_translator_custom_modifier_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetGeminiCustomPromptModifier(tempCustomModifier)
                    onAddLog(customPromptUpdatedLabel)
                    showCustomPromptDialog = false
                }) {
                    Text(stringResource(AYMR.strings.novel_reader_ai_translator_save))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        tempCustomModifier = ""
                        onSetGeminiCustomPromptModifier("")
                        showCustomPromptDialog = false
                    }) { Text(stringResource(AYMR.strings.novel_reader_gemini_action_clear)) }
                    TextButton(onClick = { showCustomPromptDialog = false }) {
                        Text(stringResource(AYMR.strings.novel_reader_ai_translator_cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun AiTranslatorChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            },
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AiTranslatorSupportText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AiTranslatorMiniSection(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
        )
        AiTranslatorSupportText(subtitle)
    }
}

@Composable
private fun AiTranslatorPanelCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AiTranslatorMiniSection(
                title = title,
                subtitle = subtitle,
            )
            content()
        }
    }
}

@Composable
private fun AiTranslatorProviderCard(
    title: String,
    apiConfigured: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (apiConfigured) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (apiConfigured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
            AiTranslatorSupportText(
                text = stringResource(
                    if (apiConfigured) {
                        AYMR.strings.novel_reader_ai_translator_provider_api_ready
                    } else {
                        AYMR.strings.novel_reader_ai_translator_provider_api_missing
                    },
                ),
            )
        }
    }
}

@Composable
private fun AiTranslatorToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            AiTranslatorSupportText(subtitle)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun AiTranslatorApiTestButton(
    status: ProviderApiTestStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelRes = when (status) {
        ProviderApiTestStatus.Idle -> AYMR.strings.novel_reader_ai_translator_api_test_idle
        ProviderApiTestStatus.Loading -> AYMR.strings.novel_reader_ai_translator_api_test_loading
        ProviderApiTestStatus.Success -> AYMR.strings.novel_reader_ai_translator_api_test_success
        ProviderApiTestStatus.Error -> AYMR.strings.novel_reader_ai_translator_api_test_error
    }
    val (containerColor, contentColor) = when (status) {
        ProviderApiTestStatus.Idle ->
            MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
        ProviderApiTestStatus.Loading ->
            MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer
        ProviderApiTestStatus.Success ->
            MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
        ProviderApiTestStatus.Error ->
            MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
    }
    Button(
        onClick = onClick,
        enabled = status != ProviderApiTestStatus.Loading,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        when (status) {
            ProviderApiTestStatus.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            ProviderApiTestStatus.Success -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            ProviderApiTestStatus.Error -> {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            ProviderApiTestStatus.Idle -> {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
        Text(stringResource(labelRes))
    }
}

@Composable
private fun getApiKeyUrl(provider: NovelTranslationProvider): String? {
    return when (provider) {
        NovelTranslationProvider.GEMINI ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_gemini)
        NovelTranslationProvider.GEMINI_PRIVATE -> null
        NovelTranslationProvider.OPENROUTER ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_openrouter)
        NovelTranslationProvider.DEEPSEEK ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_deepseek)
        NovelTranslationProvider.MISTRAL ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_mistral)
        NovelTranslationProvider.NVIDIA ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_nvidia)
        NovelTranslationProvider.OLLAMA_CLOUD ->
            stringResource(AYMR.strings.novel_reader_ai_translator_api_url_ollama_cloud)
    }
}

@Composable
private fun getAiTranslatorProviderLabel(provider: NovelTranslationProvider): String {
    return when (provider) {
        NovelTranslationProvider.GEMINI ->
            stringResource(AYMR.strings.novel_reader_translation_provider_gemini)
        NovelTranslationProvider.GEMINI_PRIVATE ->
            if (GeminiPrivateBridge.isInstalled()) {
                GeminiPrivateBridge.providerLabel()
            } else {
                stringResource(AYMR.strings.novel_reader_translation_provider_gemini_private)
            }
        NovelTranslationProvider.OPENROUTER ->
            stringResource(AYMR.strings.novel_reader_translation_provider_openrouter)
        NovelTranslationProvider.DEEPSEEK ->
            stringResource(AYMR.strings.novel_reader_translation_provider_deepseek)
        NovelTranslationProvider.MISTRAL ->
            stringResource(AYMR.strings.novel_reader_translation_provider_mistral)
        NovelTranslationProvider.NVIDIA ->
            stringResource(AYMR.strings.novel_reader_translation_provider_nvidia)
        NovelTranslationProvider.OLLAMA_CLOUD ->
            stringResource(AYMR.strings.novel_reader_translation_provider_ollama_cloud)
    }
}
