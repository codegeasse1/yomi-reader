package eu.kanade.tachiyomi.data.backup.create.creators

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import org.junit.jupiter.api.Test

class NovelExtensionStoreBackupCreatorTest {

    @Test
    fun `invoke maps novel extension stores`() {
        runTest {
            val store = ExtensionStore(
                indexUrl = "https://example.org/store.json",
                name = "Example Store",
                badgeLabel = "ex",
                signingKey = "ABC",
                contact = ExtensionStore.Contact(website = "https://example.org", discord = null),
                isLegacy = false,
                extensionListUrl = null,
            )
            val getStores = mockk<NovelExtensionStoreRepository>()
            coEvery { getStores.getAll() } returns listOf(store)

            val creator = NovelExtensionStoreBackupCreator(getStores)

            val result = creator()
            result.size shouldBe 1
            result.single().apply {
                indexUrl shouldBe "https://example.org/store.json"
                name shouldBe "Example Store"
                badgeLabel shouldBe "ex"
                signingKey shouldBe "ABC"
            }
        }
    }
}
