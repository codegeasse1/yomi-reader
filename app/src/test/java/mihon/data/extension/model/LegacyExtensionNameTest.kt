package mihon.data.extension.model

import io.kotest.matchers.shouldBe
import mihon.domain.extensionstore.model.ExtensionStore
import org.junit.jupiter.api.Test

class LegacyExtensionNameTest {

    private val store = ExtensionStore(
        indexUrl = "https://example.org/repo/repo.json",
        name = "Example",
        badgeLabel = "Example",
        signingKey = "abc",
        contact = ExtensionStore.Contact(website = "https://example.org", discord = null),
        isLegacy = true,
        extensionListUrl = null,
    )

    private fun decode(name: String): String? {
        return NetworkLegacyExtension(
            name = name,
            pkg = "pkg.example",
            apk = "pkg.example.apk",
            lang = "en",
            code = 1,
            version = "1.4.0",
        ).toAvailableExtensionData(store, "https://example.org/repo")?.name
    }

    @Test
    fun `the anime host prefix is stripped like the manga one`() {
        decode("Aniyomi: AnimeKai") shouldBe "AnimeKai"
        decode("Tachiyomi: MangaDex") shouldBe "MangaDex"
    }

    @Test
    fun `names without a host prefix are left alone`() {
        decode("AnimeKai") shouldBe "AnimeKai"
    }
}
