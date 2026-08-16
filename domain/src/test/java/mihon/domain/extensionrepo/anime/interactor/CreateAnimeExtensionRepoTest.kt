package mihon.domain.extensionrepo.anime.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.model.ExtensionStore
import org.junit.jupiter.api.Test

class CreateAnimeExtensionRepoTest {

    @Test
    fun `valid url inserts store using provided display name`() = runTest {
        val repository = mockk<AnimeExtensionStoreRepository>(relaxed = true)
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
        val interactor = CreateAnimeExtensionRepo(repository)

        val result = interactor.await("https://example.org/index.min.json", "Custom store")

        result shouldBe CreateAnimeExtensionRepo.Result.Success
        coVerify {
            repository.setCustomName(insertedStore.indexUrl, "Custom store")
        }
    }

    @Test
    fun `forceLocalInsert normalizes legacy index url and inserts preference store`() = runTest {
        val repository = mockk<AnimeExtensionStoreRepository>(relaxed = true)
        coEvery { repository.insert("https://example.org/repo/index.min.json") } returns Result.failure(
            IllegalStateException("offline"),
        )
        val interactor = CreateAnimeExtensionRepo(repository)

        val result = interactor.await(
            indexUrl = "https://example.org/repo/index.min.json",
            displayName = "Offline Repo",
            forceLocalInsert = true,
        )

        result shouldBe CreateAnimeExtensionRepo.Result.Success
        coVerify {
            repository.insertFromPreference(
                "https://example.org/repo/repo.json",
                "Offline Repo",
            )
        }
    }
}
