package eu.kanade.tachiyomi.ui.reader.novel.replace

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class ReplaceRuleTest {

    @Test
    fun `plain pattern replaces substring`() {
        val rule = ReplaceRule(
            pattern = "本章未完，请点击下一页继续阅读",
            replacement = "",
            isRegex = false,
        )
        rule.apply("Текст. 本章未完，请点击下一页继续阅读 И дальше.") shouldBe "Текст.  И дальше."
    }

    @Test
    fun `regex pattern supports backreference`() {
        val rule = ReplaceRule(
            pattern = "([。，！？；])\\1+",
            replacement = "$1",
            isRegex = true,
        )
        rule.apply("Что？？  Да。。") shouldBe "Что？  Да。"
    }

    @Test
    fun `regex lookahead does not touch longer names`() {
        val rule = ReplaceRule(
            pattern = "Рейнар(?!д)",
            replacement = "Райнар",
            isRegex = true,
        )
        rule.apply("Рейнар встал, Рейнард кивнул.") shouldBe "Райнар встал, Рейнард кивнул."
    }

    @Test
    fun `disabled rule returns text unchanged`() {
        val rule = ReplaceRule(
            pattern = "старое",
            replacement = "новое",
            isEnabled = false,
            isRegex = false,
        )
        rule.apply("старое слово") shouldBe "старое слово"
    }

    @Test
    fun `invalid regex is not valid and apply is a no-op`() {
        val rule = ReplaceRule(pattern = "([unclosed", replacement = "x")
        rule.isValid() shouldBe false
        rule.apply("abc") shouldBe "abc"
    }

    @Test
    fun `blank pattern is not valid`() {
        ReplaceRule(pattern = "", replacement = "x").isValid() shouldBe false
        ReplaceRule(pattern = "  ", replacement = "x").isValid() shouldBe false
    }

    @Test
    fun `trailing pipe is a valid regex`() {
        ReplaceRule(pattern = "a|", replacement = "x").isValid() shouldBe true
    }

    @Test
    fun `scope is derived from title and content flags`() {
        ReplaceRule().scope shouldBe ReplaceRule.Scope.CONTENT
        ReplaceRule(scopeTitle = true).scope shouldBe ReplaceRule.Scope.BOTH
        ReplaceRule(scopeTitle = true, scopeContent = false).scope shouldBe ReplaceRule.Scope.TITLE
        ReplaceRule(scopeTitle = false, scopeContent = true).scope shouldBe ReplaceRule.Scope.CONTENT
    }

    @Test
    fun `applyReplaceRulesToText chains rules in order field order`() {
        val rules = listOf(
            ReplaceRule(pattern = "a", replacement = "b", isRegex = false, order = 1),
            ReplaceRule(pattern = "b", replacement = "c", isRegex = false, order = 2),
        )
        applyReplaceRulesToText("a", rules) shouldBe "c"
    }

    @Test
    fun `applyReplaceRulesToText skips disabled and blank rules`() {
        val rules = listOf(
            ReplaceRule(pattern = "x", replacement = "y", isRegex = false, isEnabled = false),
            ReplaceRule(pattern = "", replacement = "z", isRegex = false),
            ReplaceRule(pattern = "a", replacement = "c", isRegex = false),
        )
        applyReplaceRulesToText("a", rules) shouldBe "c"
    }

    @Test
    fun `applyReplaceRulesToText returns input for empty rules or text`() {
        applyReplaceRulesToText("текст", emptyList()) shouldBe "текст"
        applyReplaceRulesToText("", listOf(ReplaceRule(pattern = "a", replacement = "b"))) shouldBe ""
    }

    @Test
    fun `rule with zero timeout still applies`() {
        val rule = ReplaceRule(pattern = "x", replacement = "y", isRegex = false, timeoutMillisecond = 0)
        rule.apply("x") shouldBe "y"
    }

    @Test
    fun `dollar backreference naming no group no-ops instead of crashing`() {
        val rule = ReplaceRule(pattern = "(a)", replacement = "$5", isRegex = true)
        rule.apply("a") shouldBe "a"
    }

    @Test
    fun `fingerprint changes with any rule change and stays stable otherwise`() {
        val rules = listOf(
            ReplaceRule(id = 1L, pattern = "a", replacement = "b", isRegex = false),
        )
        replaceRulesFingerprint(rules) shouldBe replaceRulesFingerprint(rules)
        replaceRulesFingerprint(rules) shouldNotBe replaceRulesFingerprint(
            rules.map { it.copy(isEnabled = false) },
        )
        replaceRulesFingerprint(rules) shouldNotBe replaceRulesFingerprint(
            rules.map { it.copy(replacement = "c") },
        )
        replaceRulesFingerprint(rules) shouldNotBe replaceRulesFingerprint(
            rules.map { it.copy(order = it.order + 1) },
        )
        replaceRulesFingerprint(emptyList()) shouldBe ""
    }

    @Test
    fun `kotlinx json roundtrip keeps all fields`() {
        val rule = ReplaceRule(
            id = 42L,
            name = "Имя",
            group = "Мусор",
            pattern = "p",
            replacement = "r",
            scopeTitle = true,
            scopeContent = true,
            isEnabled = false,
            isRegex = false,
            timeoutMillisecond = 1000L,
            order = 7,
        )
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(listOf(rule))
        json.decodeFromString<List<ReplaceRule>>(encoded) shouldBe listOf(rule)
    }
}
