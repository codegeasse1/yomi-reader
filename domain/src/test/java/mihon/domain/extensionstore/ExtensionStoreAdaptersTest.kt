package mihon.domain.extensionstore

import io.kotest.matchers.shouldBe
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionstore.model.ExtensionStore
import org.junit.jupiter.api.Test

class ExtensionStoreAdaptersTest {

    @Test
    fun `toLegacyExtensionStore maps repo fields for migration and backup`() {
        val repo = ExtensionRepo(
            baseUrl = "https://repo.example",
            name = "Example Repo",
            shortName = "example",
            website = "https://repo.example/site",
            signingKeyFingerprint = "ABC123",
            discord = "https://discord.gg/invite",
        )

        val store = repo.toLegacyExtensionStore()

        store.indexUrl shouldBe "https://repo.example/repo.json"
        store.name shouldBe "Example Repo"
        store.badgeLabel shouldBe "example"
        store.signingKey shouldBe "ABC123"
        store.contact.website shouldBe "https://repo.example/site"
        store.contact.discord shouldBe "https://discord.gg/invite"
        store.isLegacy shouldBe true
    }

    @Test
    fun `toExtensionRepo exposes legacy base url to existing UI`() {
        val store = ExtensionStore(
            indexUrl = "https://repo.example/repo.json",
            name = "Store",
            badgeLabel = "badge",
            signingKey = "fp",
            contact = ExtensionStore.Contact(website = "https://repo.example", discord = "https://discord.gg/invite"),
            isLegacy = true,
            extensionListUrl = null,
        )

        val repo = store.toExtensionRepo()

        repo.baseUrl shouldBe "https://repo.example"
        repo.name shouldBe "Store"
        repo.shortName shouldBe "badge"
        repo.signingKeyFingerprint shouldBe "fp"
        repo.discord shouldBe "https://discord.gg/invite"
    }
}
