package eu.kanade.presentation.more.settings.screen.browse

import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MangaExtensionStoreScreenModelTest {

    private val getExtensionRepo: GetMangaExtensionRepo = mockk()
    private val createExtensionRepo: CreateMangaExtensionRepo = mockk(relaxed = true)
    private val deleteExtensionRepo: DeleteMangaExtensionRepo = mockk(relaxed = true)
    private val replaceExtensionRepo: ReplaceMangaExtensionRepo = mockk(relaxed = true)
    private val updateExtensionRepo: UpdateMangaExtensionRepo = mockk(relaxed = true)
    private val extensionManager: MangaExtensionManager = mockk(relaxed = true)
    private val activeScreenModels = mutableListOf<MangaExtensionStoreScreenModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        // The screen model calls getAll() first to trigger the legacy store port.
        coEvery { getExtensionRepo.getAll() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.clear()
        runBlocking {
            repeat(5) { yield() }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `loads repos into success state`() {
        runBlocking {
            val repo = repo()
            every { getExtensionRepo.subscribeAll() } returns flowOf(listOf(repo))

            val screenModel = MangaExtensionStoreScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,

            ).also(activeScreenModels::add)

            // WhileSubscribed stateIn needs an active collector to drive the upstream flow
            val collectJob = launch { screenModel.state.collect() }
            yield()

            withTimeout(1_000) {
                while (screenModel.state.value !is RepoScreenState.Success) {
                    yield()
                }
            }

            val state = screenModel.state.value
            state.shouldBeInstanceOf<RepoScreenState.Success>()
            (state as RepoScreenState.Success).repos.first() shouldBe repo
            collectJob.cancel()
        }
    }

    @Test
    fun `create repo forwards display name and refreshes available plugins`() {
        runBlocking {
            every { getExtensionRepo.subscribeAll() } returns flowOf(emptyList())
            coEvery {
                createExtensionRepo.await(
                    "https://example.org/index.min.json",
                    "Custom repo",
                )
            } returns CreateMangaExtensionRepo.Result.Success
            coEvery { extensionManager.findAvailableExtensions() } returns Unit

            val screenModel = MangaExtensionStoreScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,
            ).also(activeScreenModels::add)

            screenModel.createRepo("https://example.org/index.min.json", "Custom repo")

            withTimeout(1_000) {
                while (true) {
                    runCatching {
                        coVerify(exactly = 1) {
                            createExtensionRepo.await(
                                "https://example.org/index.min.json",
                                "Custom repo",
                            )
                        }
                        coVerify(exactly = 1) { extensionManager.findAvailableExtensions() }
                    }.onSuccess { return@withTimeout }
                    yield()
                }
            }
        }
    }

    @Test
    fun `rename repo refreshes available plugins`() {
        runBlocking {
            val repo = repo(name = "Old name")
            every { getExtensionRepo.subscribeAll() } returns flowOf(listOf(repo))
            coEvery { replaceExtensionRepo.await(repo.copy(name = "New name")) } returns Unit
            coEvery { extensionManager.findAvailableExtensions() } returns Unit

            val screenModel = MangaExtensionStoreScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,
            ).also(activeScreenModels::add)

            screenModel.renameRepo(repo, "New name")

            withTimeout(1_000) {
                while (true) {
                    runCatching {
                        coVerify(exactly = 1) { replaceExtensionRepo.await(repo.copy(name = "New name")) }
                        coVerify(exactly = 1) { extensionManager.findAvailableExtensions() }
                    }.onSuccess { return@withTimeout }
                    yield()
                }
            }
        }
    }

    private fun repo(
        name: String = "Repo",
    ): ExtensionRepo {
        return ExtensionRepo(
            baseUrl = "https://example.org",
            name = name,
            shortName = "repo",
            website = "https://example.org",
            signingKeyFingerprint = "fingerprint",
        )
    }

    @Test
    fun `a dialog requested while still loading is applied once the repos arrive`() {
        runBlocking {
            // Deep links (tachiyomi://add-repo) hit the screen on the first frame, before the list.
            val repos = MutableSharedFlow<List<ExtensionRepo>>()
            every { getExtensionRepo.subscribeAll() } returns repos

            val screenModel = MangaExtensionStoreScreenModel(
                getExtensionRepo = getExtensionRepo,
                createExtensionRepo = createExtensionRepo,
                deleteExtensionRepo = deleteExtensionRepo,
                replaceExtensionRepo = replaceExtensionRepo,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = extensionManager,
            ).also(activeScreenModels::add)

            // WhileSubscribed stateIn needs an active collector to drive the upstream flow
            val collectJob = launch { screenModel.state.collect() }
            yield()

            screenModel.showDialog(RepoDialog.Confirm("https://example.org/index.min.json"))
            withTimeout(1_000) {
                while (repos.subscriptionCount.value == 0) {
                    yield()
                }
            }
            repos.emit(listOf(repo()))

            withTimeout(1_000) {
                while (screenModel.state.value !is RepoScreenState.Success) {
                    yield()
                }
            }

            val state = screenModel.state.value as RepoScreenState.Success
            state.dialog.shouldBeInstanceOf<RepoDialog.Confirm>()
            collectJob.cancel()
        }
    }
}
