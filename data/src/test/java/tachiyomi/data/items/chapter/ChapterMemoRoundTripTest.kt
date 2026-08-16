package tachiyomi.data.items.chapter

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
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.data.Database as MangaDatabase

/**
 * `memo` is how a 1.6 extension carries its own context (e.g. a rotating slug) between calls: it
 * returns it with the chapter and reads it back when the chapter is opened. Losing it on the way to
 * or from the database makes the source refuse the request.
 */
class ChapterMemoRoundTripTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var handler: AndroidMangaDatabaseHandler
    private lateinit var repository: ChapterRepositoryImpl

    private val memo = JsonObject(mapOf("mangaSlug" to JsonPrimitive("abc-123")))

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
        repository = ChapterRepositoryImpl(handler)
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    private fun chapter(url: String) = Chapter.create().copy(
        mangaId = 1L,
        url = url,
        name = "Chapter 1",
        memo = memo,
    )

    @Test
    fun `memo survives insert and read back`() = runTest {
        repository.addAllChapters(listOf(chapter("/chapter/1")))

        val stored = repository.getChapterByMangaId(1L).single()

        stored.memo shouldBe memo
    }

    @Test
    fun `memo can be updated for an existing chapter`() = runTest {
        val inserted = repository.addAllChapters(listOf(chapter("/chapter/1"))).single()
        val newMemo = JsonObject(mapOf("mangaSlug" to JsonPrimitive("rotated-999")))

        repository.updateChapter(ChapterUpdate(id = inserted.id, memo = newMemo))

        repository.getChapterByMangaId(1L).single().memo shouldBe newMemo
    }

    @Test
    fun `an update without memo leaves the stored one alone`() = runTest {
        val inserted = repository.addAllChapters(listOf(chapter("/chapter/1"))).single()

        repository.updateChapter(ChapterUpdate(id = inserted.id, name = "Renamed"))

        val stored = repository.getChapterByMangaId(1L).single()
        stored.name shouldBe "Renamed"
        stored.memo shouldBe memo
    }
}
