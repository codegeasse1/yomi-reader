package eu.kanade.presentation.browse.novel

import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionItem
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelExtensionsScreenTest {

    @Test
    fun `shouldLoadNovelPluginIcon returns false for null`() {
        shouldLoadNovelPluginIcon(null) shouldBe false
    }

    @Test
    fun `shouldLoadNovelPluginIcon returns false for blank`() {
        shouldLoadNovelPluginIcon("   ") shouldBe false
    }

    @Test
    fun `shouldLoadNovelPluginIcon returns true for non blank value`() {
        shouldLoadNovelPluginIcon("https://example.org/icon.png") shouldBe true
    }

    @Test
    fun `uniqueNovelExtensionItemKeys separates installed and untrusted rows with same plugin id`() {
        val pluginId = "eu.kanade.tachiyomi.novelextension.all.shosetsu"
        val keys = uniqueNovelExtensionItemKeys(
            section = "installed",
            items = listOf(
                item(installed(id = pluginId), NovelExtensionItem.Status.Installed),
                item(untrusted(id = pluginId), NovelExtensionItem.Status.Untrusted),
            ),
        )

        keys shouldHaveSize 2
        keys.toSet() shouldHaveSize 2
    }

    @Test
    fun `uniqueNovelExtensionItemKeys adds occurrence suffix for exact duplicate rows`() {
        val duplicate = item(installed(id = "plugin.id"), NovelExtensionItem.Status.Installed)

        val keys = uniqueNovelExtensionItemKeys(
            section = "installed",
            items = listOf(duplicate, duplicate),
        )

        keys shouldHaveSize 2
        keys.toSet() shouldHaveSize 2
        keys[1] shouldBe "${keys[0]}#1"
    }

    @Test
    fun `uniqueNovelExtensionItemKeys keeps single row key stable without occurrence suffix`() {
        val keys = uniqueNovelExtensionItemKeys(
            section = "available-en",
            items = listOf(item(available(id = "plugin.id"), NovelExtensionItem.Status.Available)),
        )

        keys.single().endsWith("#1") shouldBe false
    }

    private fun item(
        plugin: NovelPlugin,
        status: NovelExtensionItem.Status,
    ) = NovelExtensionItem(
        plugin = plugin,
        status = status,
        installStep = InstallStep.Idle,
        settingsSourceId = null,
    )

    private fun available(
        id: String,
    ) = NovelPlugin.Available(
        id = id,
        name = "Plugin",
        site = "https://example.org",
        lang = "en",
        versionCode = 1,
        versionName = "1",
        url = "https://example.org/plugin.js",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "sha",
        repoUrl = "https://repo.example/index.json",
        pkgName = id,
    )

    private fun installed(
        id: String,
    ) = NovelPlugin.Installed(
        id = id,
        name = "Plugin",
        site = "https://example.org",
        lang = "en",
        versionCode = 1,
        versionName = "1",
        url = "https://example.org/plugin.js",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "sha",
        repoUrl = "https://repo.example/index.json",
        pkgName = id,
    )

    private fun untrusted(
        id: String,
    ) = NovelPlugin.Untrusted(
        id = id,
        name = "Plugin",
        site = "https://example.org",
        lang = "en",
        versionCode = 1,
        versionName = "1",
        url = "https://example.org/plugin.js",
        iconUrl = null,
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = "sha",
        repoUrl = "https://repo.example/index.json",
        pkgName = id,
        signatureHash = "signature",
    )
}
