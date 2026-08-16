package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import eu.kanade.tachiyomi.data.backup.create.creators.NovelExtensionStoreBackupCreator
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.novel.data.NovelDatabase

class NovelExtensionRepoRestorerTest {

    @Test
    fun `restore inserts novel repo when no conflicts`() {
        runTest {
            Class.forName("org.sqlite.JDBC")
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            val database = createTestNovelDatabase(driver)
            val handler = FakeNovelDatabaseHandler(database)
            val getRepos = mockk<GetNovelExtensionRepo>()
            coEvery { getRepos.getAll() } returns emptyList()

            val restorer = NovelExtensionRepoRestorer(handler, getRepos)

            restorer(
                BackupExtensionRepos(
                    baseUrl = "https://example.org",
                    name = "Example",
                    shortName = null,
                    website = "https://example.org",
                    signingKeyFingerprint = "ABC",
                ),
            )

            database.extension_storeQueries.getAll().executeAsList().size shouldBe 1
            driver.close()
        }
    }

    private fun createTestNovelDatabase(driver: JdbcSqliteDriver): NovelDatabase {
        NovelDatabase.Schema.create(driver)
        return NovelDatabase(
            driver = driver,
            novel_historyAdapter = Novel_history.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            novel_chaptersAdapter = datanovel.Novel_chapters.Adapter(memoAdapter = MemoColumnAdapter),
            novelsAdapter = Novels.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
    }

    private class FakeNovelDatabaseHandler(
        private val database: NovelDatabase,
    ) : NovelDatabaseHandler {
        override suspend fun <T> await(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> T,
        ): T = block(database)

        override suspend fun <T : Any> awaitList(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ): List<T> = error("unused")

        override suspend fun <T : Any> awaitOne(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ): T = error("unused")

        override suspend fun <T : Any> awaitOneExecutable(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> app.cash.sqldelight.ExecutableQuery<T>,
        ): T = error("unused")

        override suspend fun <T : Any> awaitOneOrNull(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ): T? = error("unused")

        override suspend fun <T : Any> awaitOneOrNullExecutable(
            inTransaction: Boolean,
            block: suspend (NovelDatabase) -> app.cash.sqldelight.ExecutableQuery<T>,
        ): T? = error("unused")

        override fun <T : Any> subscribeToList(
            block: (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")

        override fun <T : Any> subscribeToOne(
            block: (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")

        override fun <T : Any> subscribeToOneOrNull(
            block: (NovelDatabase) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")

        override fun <T : Any> subscribeToPagingSource(
            countQuery: (NovelDatabase) -> app.cash.sqldelight.Query<Long>,
            queryProvider: (NovelDatabase, Long, Long) -> app.cash.sqldelight.Query<T>,
        ) = error("unused")
    }

    @Test
    fun `store backup roundtrip via creator then restorer`() {
        runTest {
            Class.forName("org.sqlite.JDBC")
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            val database = createTestNovelDatabase(driver)
            val handler = FakeNovelDatabaseHandler(database)
            val getStore =
                mockk<mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository>(relaxed = true)
            coEvery { getStore.getAll() } returns emptyList()

            val store = mihon.domain.extensionstore.model.ExtensionStore(
                indexUrl = "https://novel.example/store.json",
                name = "Novel Example",
                badgeLabel = "NE",
                signingKey = "DEF123",
                contact = mihon.domain.extensionstore.model.ExtensionStore.Contact(
                    website = "https://novel.example",
                    discord = null,
                ),
                isLegacy = false,
                extensionListUrl = null,
            )
            coEvery { getStore.getAll() } returns listOf(store)

            val creator = NovelExtensionStoreBackupCreator(getStore)
            val backups = creator()
            backups.size shouldBe 1
            val backupStore = backups.single()

            coEvery { getStore.getAll() } returns emptyList()
            val restorer = NovelExtensionStoreRestorer(handler, getStore)
            restorer(backupStore)

            val stored = database.extension_storeQueries.getAll().executeAsList()
            stored.size shouldBe 1
            val s = stored[0]
            s.index_url shouldBe "https://novel.example/store.json"
            s.name shouldBe "Novel Example"
            s.signing_key shouldBe "DEF123"
            driver.close()
        }
    }

    @Test
    fun `restoring the same store twice is a no-op instead of an error`() {
        runTest {
            Class.forName("org.sqlite.JDBC")
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            val database = createTestNovelDatabase(driver)
            val handler = FakeNovelDatabaseHandler(database)
            val storeRepository =
                mockk<mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository>(relaxed = true)

            val existing = mihon.domain.extensionstore.model.ExtensionStore(
                indexUrl = "https://novel.example/store.json",
                name = "Novel Example",
                badgeLabel = "NE",
                signingKey = "DEF123",
                contact = mihon.domain.extensionstore.model.ExtensionStore.Contact(
                    website = "https://novel.example",
                    discord = null,
                ),
                isLegacy = false,
                extensionListUrl = null,
            )
            coEvery { storeRepository.getAll() } returns listOf(existing)

            NovelExtensionStoreRestorer(handler, storeRepository)(
                eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore(
                    indexUrl = existing.indexUrl,
                    name = existing.name,
                    badgeLabel = existing.badgeLabel,
                    signingKey = existing.signingKey,
                    contactWebsite = existing.contact.website,
                    contactDiscord = existing.contact.discord,
                    isLegacy = existing.isLegacy,
                    extensionListUrl = existing.extensionListUrl,
                ),
            )

            database.extension_storeQueries.getAll().executeAsList() shouldBe emptyList()
            driver.close()
        }
    }

    @Test
    fun `a second store without a signing key still restores`() {
        runTest {
            Class.forName("org.sqlite.JDBC")
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            val database = createTestNovelDatabase(driver)
            val handler = FakeNovelDatabaseHandler(database)
            val storeRepository =
                mockk<mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository>(relaxed = true)

            // Every store whose repo.json was unreachable carries this same placeholder key.
            val keyless = mihon.domain.extensionstore.model.ExtensionStore(
                indexUrl = "https://first.example/plugins.min.json",
                name = "First",
                badgeLabel = "First",
                signingKey = "NO_SIGNING_KEY",
                contact = mihon.domain.extensionstore.model.ExtensionStore.Contact(
                    website = "https://first.example",
                    discord = null,
                ),
                isLegacy = true,
                extensionListUrl = null,
            )
            coEvery { storeRepository.getAll() } returns listOf(keyless)

            NovelExtensionStoreRestorer(handler, storeRepository)(
                eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore(
                    indexUrl = "https://second.example/plugins.min.json",
                    name = "Second",
                    badgeLabel = "Second",
                    signingKey = "NO_SIGNING_KEY",
                    contactWebsite = "https://second.example",
                    contactDiscord = null,
                    isLegacy = true,
                    extensionListUrl = null,
                ),
            )

            database.extension_storeQueries.getAll().executeAsList().map { it.index_url } shouldBe
                listOf("https://second.example/plugins.min.json")
            driver.close()
        }
    }
}
