package eu.kanade.presentation.reader.novel

import androidx.compose.ui.graphics.Color
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderAppearanceMode
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundSource
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import java.io.File

@Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
internal fun resolveNovelReaderBackdropColor(
    settings: NovelReaderSettings,
    isSystemDark: Boolean,
): Color {
    val theme = safeEnum(settings.theme, NovelReaderTheme.SYSTEM)
    val themeFallback = when (theme) {
        NovelReaderTheme.SYSTEM -> if (isSystemDark) Color(0xFF121212) else Color.White
        NovelReaderTheme.LIGHT -> Color.White
        NovelReaderTheme.DARK -> Color(0xFF121212)
    }
    val themeBackground = parseReaderColor(settings.backgroundColor)
        .takeIf { settings.backgroundColor?.isNotBlank() == true }
        ?: themeFallback

    val appearanceMode = safeEnum(settings.appearanceMode, NovelReaderAppearanceMode.THEME)
    return when (appearanceMode) {
        NovelReaderAppearanceMode.THEME -> themeBackground
        NovelReaderAppearanceMode.BACKGROUND -> {
            resolveReaderBackgroundBackdropColor(
                resolveReaderBackgroundSelection(
                    backgroundSource = safeEnum(settings.backgroundSource, NovelReaderBackgroundSource.PRESET),
                    backgroundPresetId = settings.backgroundPresetId,
                    customBackgroundId = settings.customBackgroundId,
                    customBackgroundItems = emptyList(),
                    customBackgroundPath = settings.customBackgroundPath,
                    customBackgroundExists = settings.customBackgroundPath.orEmpty().isNotBlank() &&
                        File(settings.customBackgroundPath.orEmpty()).exists(),
                ),
            )
        }
    }
}

internal fun buildSourceIndexedPageReaderTextList(
    blocks: List<PlainPageReaderTextBlock>,
): List<String> {
    val maxSourceBlockIndex = blocks.maxOfOrNull { it.sourceBlockIndex } ?: return emptyList()
    return MutableList(maxSourceBlockIndex + 1) { "" }.apply {
        blocks.forEach { block ->
            this[block.sourceBlockIndex] = block.text
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> safeEnum(value: Any?, fallback: T): T {
    return if (value != null && fallback::class.java.isInstance(value)) {
        value as T
    } else {
        fallback
    }
}
