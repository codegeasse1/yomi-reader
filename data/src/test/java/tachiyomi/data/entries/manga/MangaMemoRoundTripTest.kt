package tachiyomi.data.entries.manga

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import `data`.History
import `data`.Mangas
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.data.Database as MangaDatabase

class MangaMemoRoundTripTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var handler: AndroidMangaDatabaseHandler
    private lateinit var repository: MangaRepositoryImpl

    private val memo = JsonObject(mapOf("slug" to JsonPrimitive("allmanga-slug-123")))

    @BeforeEach
    fun setUp() {
        Class.forName("org.sqlite.JDBC")
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MangaDatabase.Schema.create(driver)
        val database = MangaDatabase(
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
        repository = MangaRepositoryImpl(handler, AchievementEventBus())
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    private fun manga(url: String) = Manga.create().copy(
        source = 100L,
        url = url,
        title = "Test Manga",
        memo = memo,
    )

    @Test
    fun `memo survives insert and read back`() = runTest {
        val id = repository.insertManga(manga("/manga/1"))!!

        val stored = repository.getMangaById(id)

        stored.memo shouldBe memo
    }

    @Test
    fun `memo survives insertNetworkMangas and read back`() = runTest {
        val inserted = repository.insertNetworkMangas(listOf(manga("/manga/1")), autoFavorite = false).single()

        val stored = repository.getMangaById(inserted.id)

        stored.memo shouldBe memo
    }

    @Test
    fun `memo can be updated for an existing manga`() = runTest {
        val id = repository.insertManga(manga("/manga/1"))!!
        val newMemo = JsonObject(mapOf("slug" to JsonPrimitive("rotated-slug-456")))

        repository.updateManga(MangaUpdate(id = id, memo = newMemo))

        repository.getMangaById(id).memo shouldBe newMemo
    }

    @Test
    fun `an update without memo leaves the stored one alone`() = runTest {
        val id = repository.insertManga(manga("/manga/1"))!!

        repository.updateManga(MangaUpdate(id = id, title = "Renamed Manga"))

        val stored = repository.getMangaById(id)
        stored.title shouldBe "Renamed Manga"
        stored.memo shouldBe memo
    }
}
