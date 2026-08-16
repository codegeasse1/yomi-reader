package eu.kanade.tachiyomi.ui.entries.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import tachiyomi.domain.entries.novel.model.Novel

internal object NovelEpubStyleBuilder {

    internal data class ThemeColors(
        val background: String,
        val text: String,
    )

    fun buildStylesheet(
        settings: NovelReaderSettings,
        sourceId: Long,
        applyReaderTheme: Boolean,
        includeCustomCss: Boolean,
        linkColor: String,
    ): String? {
        val themeStyles = if (applyReaderTheme) {
            val colors = resolveThemeColors(settings)
            buildString {
                appendLine("html {")
                appendLine("  scroll-behavior: smooth;")
                appendLine("  overflow-x: hidden;")
                appendLine("  word-wrap: break-word;")
                appendLine("}")
                appendLine("body {")
                val marginEm = (settings.margin / 16f).coerceAtLeast(0f)
                val fontPercent = ((settings.fontSize / 16f) * 100f).coerceAtLeast(75f)
                appendLine("  padding-left: ${formatCssNumber(marginEm)}em;")
                appendLine("  padding-right: ${formatCssNumber(marginEm)}em;")
                appendLine("  padding-bottom: 2.5em;")
                appendLine("  font-size: ${formatCssNumber(fontPercent)}%;")
                appendLine("  color: ${colors.text};")
                resolveEpubCssTextAlign(settings.textAlign)?.let { align ->
                    appendLine("  text-align: $align;")
                }
                appendLine("  line-height: ${settings.lineHeight};")
                if (settings.fontFamily.isNotBlank()) {
                    appendLine("  font-family: \"${settings.fontFamily}\";")
                }
                appendLine("  background-color: ${colors.background};")
                appendLine("}")
                appendLine("hr {")
                appendLine("  margin-top: 1.25em;")
                appendLine("  margin-bottom: 1.25em;")
                appendLine("}")
                appendLine("a {")
                appendLine("  color: $linkColor;")
                appendLine("}")
                appendLine("img {")
                appendLine("  display: block;")
                appendLine("  width: auto;")
                appendLine("  height: auto;")
                appendLine("  max-width: 100%;")
                appendLine("}")
            }
        } else {
            ""
        }

        val customStyles = if (includeCustomCss) {
            sanitizeSourceScopedCss(
                css = settings.customCSS,
                sourceId = sourceId,
            )
        } else {
            ""
        }

        val combined = (themeStyles + customStyles).trim()
        return combined.ifBlank { null }
    }

    fun buildJavaScript(
        settings: NovelReaderSettings,
        novel: Novel,
        includeCustomJs: Boolean,
    ): String? {
        if (!includeCustomJs) return null
        if (settings.customJS.isBlank()) return null

        return buildString {
            appendLine("let novelName = \"${escapeJsString(novel.title)}\";")
            appendLine("let sourceId = ${novel.source};")
            appendLine("let novelId = ${novel.id};")
            appendLine()
            append(settings.customJS)
        }.trim().ifBlank { null }
    }

    @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    fun resolveThemeColors(settings: NovelReaderSettings): ThemeColors {
        val theme = settings.theme ?: NovelReaderTheme.SYSTEM
        val resolvedBackground = settings.backgroundColor
            ?.takeIf { it.isNotBlank() }
            ?: when (theme) {
                NovelReaderTheme.LIGHT -> "#FFFFFF"
                NovelReaderTheme.DARK -> "#121212"
                NovelReaderTheme.SYSTEM -> "#121212"
            }

        val resolvedText = settings.textColor
            ?.takeIf { it.isNotBlank() }
            ?: when (theme) {
                NovelReaderTheme.LIGHT -> "#212121"
                NovelReaderTheme.DARK -> "#EAEAEA"
                NovelReaderTheme.SYSTEM -> "#EAEAEA"
            }

        return ThemeColors(
            background = resolvedBackground,
            text = resolvedText,
        )
    }

    private fun formatCssNumber(value: Float): String {
        return if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            "%.3f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
        }
    }

    private fun sanitizeSourceScopedCss(
        css: String,
        sourceId: Long,
    ): String {
        if (css.isBlank()) return ""

        val sourceScopeRegex = Regex("#sourceId-$sourceId\\s*\\{", RegexOption.IGNORE_CASE)
        val selectorCleanupRegex = Regex("#sourceId-$sourceId[^.#A-Z]*", RegexOption.IGNORE_CASE)
        val externalImportRegex = Regex("@import[^;]+;?", RegexOption.IGNORE_CASE)
        val externalUrlRegex = Regex("url\\(\\s*['\"]?(?:https?:)?//[^)]+\\)", RegexOption.IGNORE_CASE)
        val fixedPositionRegex = Regex("position\\s*:\\s*fixed\\s*;?", RegexOption.IGNORE_CASE)
        val viewportUnitsRegex = Regex("[-+]?(?:\\d*\\.)?\\d+v[whminax]", RegexOption.IGNORE_CASE)
        val animationRegex = Regex("(?:animation|transition)[^:]*:[^;]+;?", RegexOption.IGNORE_CASE)

        return css
            .replace(externalImportRegex, "")
            .replace(externalUrlRegex, "none")
            .replace(fixedPositionRegex, "")
            .replace(viewportUnitsRegex, "100%")
            .replace(animationRegex, "")
            .replace(sourceScopeRegex, "body {")
            .replace(selectorCleanupRegex, "")
    }

    private fun escapeJsString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun resolveEpubCssTextAlign(textAlign: TextAlign): String? {
        return when (textAlign) {
            TextAlign.SOURCE -> null
            TextAlign.LEFT -> "left"
            TextAlign.CENTER -> "center"
            TextAlign.JUSTIFY -> "justify"
            TextAlign.RIGHT -> "right"
        }
    }
}
