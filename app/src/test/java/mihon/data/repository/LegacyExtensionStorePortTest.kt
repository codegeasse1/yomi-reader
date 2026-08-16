package mihon.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import `data`.History
import `data`.Mangas
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.service.ExtensionStoreService
import mihon.data.repository.manga.MangaExtensionStoreRepositoryImpl
import mihon.domain.extensionstore.model.ExtensionStore
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.Database as MangaDatabase

/**
 * Covers the legacy `extension_repos` -> `extension_store` port that trust checks depend on.
 *
 * Extension loading resolves trusted fingerprints through the store repository, and that happens
 * before the app migrator runs, so the port has to be reachable from a plain read.
 */
class LegacyExtensionStorePortTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MangaDatabase
    private lateinit var handler: AndroidMangaDatabaseHandler
    private lateinit var service: ExtensionStoreService
    private lateinit var preferenceStore: MutableBooleanPreferenceStore
    private lateinit var repository: MangaExtensionStoreRepositoryImpl

    @BeforeEach
    fun setUp() {
        Class.forName("org.sqlite.JDBC")

        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MangaDatabase.Schema.create(driver)
        database = MangaDatabase(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        handler = AndroidMangaDatabaseHandler(
            db = database,
            driver = driver,
            queryDispatcher = Dispatchers.Default,
            transactionDispatcher = Dispatchers.Default,
        )
        service = ExtensionStoreService(OkHttpClient(), Json { ignoreUnknownKeys = true }, ProtoBuf)
        preferenceStore = MutableBooleanPreferenceStore()
        repository = newRepository()
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    /** Same database and same persisted preferences, fresh instance: simulates a process restart. */
    private fun newRepository() = MangaExtensionStoreRepositoryImpl(handler, service, preferenceStore)

    private fun insertLegacyRepo(
        baseUrl: String = "https://example.org/repo",
        name: String = "Legacy Repo",
        fingerprint: String = "aabbccdd",
    ) {
        database.extension_reposQueries.insert(
            base_url = baseUrl,
            name = name,
            short_name = "Legacy",
            website = "https://example.org",
            fingerprint = fingerprint,
        )
    }

    @Test
    fun `getAll ports legacy repos so the first trust check never sees an empty table`() = runTest {
        insertLegacyRepo(fingerprint = "trustedfingerprint")

        val stores = repository.getAll()

        stores.single().signingKey shouldBe "trustedfingerprint"
        stores.single().isLegacy shouldBe true
    }

    @Test
    fun `stores the user deleted are not resurrected from the legacy table`() = runTest {
        insertLegacyRepo()
        val ported = repository.getAll().single()

        repository.remove(ported.indexUrl)

        newRepository().getAll() shouldBe emptyList()
    }

    @Test
    fun `an already populated store table is never overwritten by legacy rows`() = runTest {
        val existing = ExtensionStore(
            indexUrl = "https://example.org/repo/repo.json",
            name = "Renamed by user",
            badgeLabel = "Renamed",
            signingKey = "currentfingerprint",
            contact = ExtensionStore.Contact(website = "https://example.org", discord = null),
            isLegacy = false,
            extensionListUrl = null,
        )
        repository.upsertStore(existing)
        insertLegacyRepo(name = "Old legacy name")

        val stores = newRepository().getAll()

        stores.single().name shouldBe "Renamed by user"
        stores.single().isLegacy shouldBe false
    }

    /**
     * `InMemoryPreferenceStore` hands out a fresh preference per lookup, so a write is invisible to
     * the next instance. The port guard has to survive a restart, hence this store.
     */
    private class MutableBooleanPreferenceStore : PreferenceStore {
        private val booleans = mutableMapOf<String, Boolean>()

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
            return object : Preference<Boolean> {
                override fun key(): String = key
                override fun get(): Boolean = booleans[key] ?: defaultValue
                override fun set(value: Boolean) {
                    booleans[key] = value
                }
                override fun isSet(): Boolean = key in booleans
                override fun delete() {
                    booleans.remove(key)
                }
                override fun defaultValue(): Boolean = defaultValue
                override fun changes(): Flow<Boolean> = flowOf(get())
                override fun stateIn(scope: CoroutineScope): StateFlow<Boolean> = MutableStateFlow(get())
            }
        }

        override fun getString(key: String, defaultValue: String) = error("unused")
        override fun getLong(key: String, defaultValue: Long) = error("unused")
        override fun getInt(key: String, defaultValue: Int) = error("unused")
        override fun getFloat(key: String, defaultValue: Float) = error("unused")
        override fun getStringSet(key: String, defaultValue: Set<String>) = error("unused")
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ) = error("unused")

        override fun getAll(): Map<String, *> = booleans
    }
}
