package eu.kanade.tachiyomi.ui.reader.novel.replace

import kotlinx.serialization.Serializable

/**
 * A user-defined text replacement rule, compatible with the Legado replace-rule JSON format.
 *
 * Rules are applied to the displayed chapter text only — stored content, downloads and book
 * artifacts are never modified, so toggling a rule off restores the original text.
 */
@Serializable
data class ReplaceRule(
    val id: Long = 0L,
    val name: String = "",
    val group: String? = null,
    val pattern: String = "",
    val replacement: String = "",
    val scopeTitle: Boolean = false,
    val scopeContent: Boolean = true,
    val isEnabled: Boolean = true,
    val isRegex: Boolean = true,
    val timeoutMillisecond: Long = 3000L,
    val order: Int = 0,
) {
    val scope: Scope
        get() = when {
            scopeTitle && scopeContent -> Scope.BOTH
            scopeTitle -> Scope.TITLE
            else -> Scope.CONTENT
        }

    /** Pre-compiled regex, compiled lazily so a broken rule only fails on first use. */
    val regex: Regex? by lazy {
        if (!isRegex || pattern.isBlank()) return@lazy null
        runCatching { Regex(pattern) }.getOrNull()
    }

    fun isValid(): Boolean {
        if (pattern.isBlank()) return false
        if (isRegex && regex == null) return false
        return true
    }

    /**
     * Applies this rule to [text].
     *
     * Regex replacements follow `Regex.replace` semantics: `$1`-style group references are
     * expanded, and a `$` sequence that names no group makes the replacement fail — in that case
     * the original text is returned unchanged. Note that [timeoutMillisecond] is kept for Legado
     * JSON compatibility only: the JVM regex engine cannot be preempted mid-match, so a timeout
     * cannot be enforced and a pathological pattern runs to completion.
     */
    fun apply(text: String): String {
        if (!isEnabled || pattern.isBlank()) return text
        return try {
            if (isRegex) {
                regex?.replace(text, replacement) ?: text
            } else {
                text.replace(pattern, replacement)
            }
        } catch (_: Exception) {
            // Broken backreference, malformed pattern or any other regex failure: keep the text.
            text
        }
    }

    enum class Scope { TITLE, CONTENT, BOTH }
}

/**
 * Stable identity of the visible effect of [rules], for disk-cache keys: any rule change must
 * invalidate cached markup, or a toggled rule would keep serving stale text.
 */
fun replaceRulesFingerprint(rules: List<ReplaceRule>): String =
    rules.joinToString(separator = "|") { rule ->
        "${rule.id}:${rule.isEnabled}:${rule.isRegex}:${rule.scopeTitle}:${rule.scopeContent}:" +
            "${rule.order}:${rule.pattern}->${rule.replacement}"
    }

/**
 * Applies [rules] to [text] in `order` sequence. Disabled, invalid and blank rules are skipped.
 */
fun applyReplaceRulesToText(text: String, rules: List<ReplaceRule>): String {
    if (text.isEmpty() || rules.isEmpty()) return text
    val enabled = rules.filter { it.isEnabled && it.isValid() }
    if (enabled.isEmpty()) return text
    var result = text
    for (rule in enabled.sortedBy { it.order }) {
        result = rule.apply(result)
    }
    return result
}
