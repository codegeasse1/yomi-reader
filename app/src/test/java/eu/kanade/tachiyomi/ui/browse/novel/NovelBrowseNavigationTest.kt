package eu.kanade.tachiyomi.ui.browse.novel

import eu.kanade.tachiyomi.ui.browse.novel.extension.details.novelSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.novelExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.novel.extension.novelExtensionSettingsScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.sources.migrateNovelScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.novelSourcePreferencesScreenOrNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBrowseNavigationTest {

    @Test
    fun `extension details navigation keeps plugin id`() {
        val screen = novelExtensionDetailsScreen("plugin-id")

        screen.pluginId shouldBe "plugin-id"
    }

    @Test
    fun `extension details source preferences navigation keeps source id`() {
        val screen = novelSourcePreferencesScreen(123L)

        screen.sourceId shouldBe 123L
    }

    @Test
    fun `extension list settings navigation keeps resolved source id`() {
        val screen = novelExtensionSettingsScreen(42L)

        screen.sourceId shouldBe 42L
    }

    @Test
    fun `browse source settings navigation is available for configurable source`() {
        val screen = novelSourcePreferencesScreenOrNull(
            sourceId = 55L,
            isSourceConfigurable = true,
        )

        requireNotNull(screen)
        screen.sourceId shouldBe 55L
    }

    @Test
    fun `browse source settings navigation is hidden for non configurable source`() {
        val screen = novelSourcePreferencesScreenOrNull(
            sourceId = 55L,
            isSourceConfigurable = false,
        )

        screen.shouldBeNull()
    }

    @Test
    fun `migration source click navigation keeps source id`() {
        val screen = migrateNovelScreen(987L)

        screen.sourceId shouldBe 987L
    }
}
