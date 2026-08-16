package mihon.domain.extensionrepo.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.model.ExtensionStore
import org.junit.jupiter.api.Test

class CreateMangaExtensionRepoTest {

    @Test
    fun `valid url inserts store using provided display name`() = runTest {
        val repository = mockk<MangaExtensionStoreRepository>(relaxed = true)
        val insertedStore = ExtensionStore(
            indexUrl = "https://example.org/index.min.json",
            name = "Remote store",
            badgeLabel = "remote",
            signingKey = "fingerprint",
            contact = ExtensionStore.Contact(website = "https://example.org", discord = null),
            isLegacy = true,
            extensionListUrl = null,
        )
        coEvery { repository.insert("https://example.org/index.min.json") } returns Result.success(Unit)
        coEvery { repository.getAll() } returns listOf(insertedStore)
        val interactor = CreateMangaExtensionRepo(repository)

        val result = interactor.await("https://example.org/index.min.json", "Custom store")

        result shouldBe CreateMangaExtensionRepo.Result.Success
        coVerify {
            repository.setCustomName(insertedStore.indexUrl, "Custom store")
        }
    }

    @Test
    fun `forceLocalInsert normalizes legacy index url and inserts preference store`() = runTest {
        val repository = mockk<MangaExtensionStoreRepository>(relaxed = true)
        coEvery { repository.insert("https://example.org/repo/index.min.json") } returns Result.failure(
            IllegalStateException("offline"),
        )
        val interactor = CreateMangaExtensionRepo(repository)

        val result = interactor.await(
            indexUrl = "https://example.org/repo/index.min.json",
            displayName = "Offline Repo",
            forceLocalInsert = true,
        )

        result shouldBe CreateMangaExtensionRepo.Result.Success
        coVerify {
            repository.insertFromPreference(
                "https://example.org/repo/repo.json",
                "Offline Repo",
            )
        }
    }

    @Test
    fun `display name survives the url being canonicalized on insert`() = runTest {
        val repository = mockk<MangaExtensionStoreRepository>(relaxed = true)
        // insert() persists the canonical repo.json url, not the index.min.json the user pasted.
        val insertedStore = ExtensionStore(
            indexUrl = "https://example.org/repo/repo.json",
            name = "Remote store",
            badgeLabel = "remote",
            signingKey = "fingerprint",
            contact = ExtensionStore.Contact(website = "https://example.org", discord = null),
            isLegacy = true,
            extensionListUrl = null,
        )
        coEvery { repository.insert("https://example.org/repo/index.min.json") } returns Result.success(Unit)
        coEvery { repository.getAll() } returns listOf(insertedStore)
        val interactor = CreateMangaExtensionRepo(repository)

        interactor.await("https://example.org/repo/index.min.json", "Custom store")

        coVerify {
            repository.setCustomName(insertedStore.indexUrl, "Custom store")
        }
    }

    @Test
    fun `a network failure is reported as an error, not as an invalid url`() = runTest {
        val repository = mockk<MangaExtensionStoreRepository>(relaxed = true)
        coEvery { repository.insert("https://example.org/repo/index.min.json") } returns Result.failure(
            java.io.IOException("offline"),
        )
        coEvery { repository.getAll() } returns emptyList()
        val interactor = CreateMangaExtensionRepo(repository)

        val result = interactor.await("https://example.org/repo/index.min.json")

        result shouldBe CreateMangaExtensionRepo.Result.Error
    }
}
