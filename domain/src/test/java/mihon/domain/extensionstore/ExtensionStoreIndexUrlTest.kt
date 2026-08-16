package mihon.domain.extensionstore

import io.kotest.matchers.shouldBe
import mihon.domain.extensionstore.model.ExtensionStore
import org.junit.jupiter.api.Test

class ExtensionStoreIndexUrlTest {

    private fun store(indexUrl: String) = ExtensionStore(
        indexUrl = indexUrl,
        name = "Example",
        badgeLabel = "Example",
        signingKey = "abc",
        contact = ExtensionStore.Contact(website = "https://example.org", discord = null),
        isLegacy = true,
        extensionListUrl = null,
    )

    @Test
    fun `the ui model keeps the url the store is indexed by`() {
        // The copy-url action pastes this back, so a fabricated index.min.json would 404 for novel
        // plugin repos and for non-legacy store indexes.
        store("https://example.org/repo/plugins.min.json").toExtensionRepo().indexUrl shouldBe
            "https://example.org/repo/plugins.min.json"
        store("https://example.org/repo/repo.json").toExtensionRepo().indexUrl shouldBe
            "https://example.org/repo/repo.json"
    }

    @Test
    fun `the base url still strips the index file`() {
        store("https://example.org/repo/index.min.json").toExtensionRepo().baseUrl shouldBe
            "https://example.org/repo"
    }
}
