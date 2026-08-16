package eu.kanade.presentation.more.settings.screen.about

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AboutFooterLinksTest {

    @Test
    fun `footer sections stay split between Aniyomi and Yomi`() {
        val sections = buildAboutFooterSections()

        sections shouldHaveSize 2
        sections[0] shouldBe
            AboutFooterLinkSection(
                title = "Aniyomi",
                links = listOf(
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.Website,
                        icon = AboutFooterLinkIcon.Website,
                        url = "https://aniyomi.org",
                    ),
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.Discord,
                        icon = AboutFooterLinkIcon.Discord,
                        url = "https://discord.gg/F32UjdJZrR",
                    ),
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.GitHub,
                        icon = AboutFooterLinkIcon.Github,
                        url = "https://github.com/aniyomiorg/aniyomi",
                    ),
                ),
            )
        sections[1] shouldBe
            AboutFooterLinkSection(
                title = "Yomi",
                links = listOf(
                    AboutFooterLink(
                        label = AboutFooterLinkLabel.Yomi,
                        icon = AboutFooterLinkIcon.Github,
                        url = "https://github.com/codegeasse1/yomi-reader",
                    ),
                ),
            )
    }
}
