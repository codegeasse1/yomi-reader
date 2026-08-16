package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookSectionStoreTest {

    private fun store(
        maxResidentEntries: Int = 3,
        disk: MutableMap<Long, String> = mutableMapOf(),
    ): Pair<NovelBookSectionStore, MutableMap<Long, String>> {
        val instance = NovelBookSectionStore(
            maxResidentEntries = maxResidentEntries,
            diskRead = { disk[it] },
            diskWrite = { id, html -> disk[id] = html },
        )
        return instance to disk
    }

    @Test
    fun `put keeps html in memory and persists it`() {
        val (sections, disk) = store()

        sections.put(1L, "<p>one</p>")

        sections.get(1L) shouldBe "<p>one</p>"
        sections.isResident(1L) shouldBe true
        disk[1L] shouldBe "<p>one</p>"
    }

    @Test
    fun `put can skip persistence`() {
        val (sections, disk) = store()

        sections.put(1L, "<p>one</p>", persist = false)

        sections.get(1L) shouldBe "<p>one</p>"
        disk.containsKey(1L) shouldBe false
    }

    @Test
    fun `blank html is ignored`() {
        val (sections, disk) = store()

        sections.put(1L, "   ")

        sections.isResident(1L) shouldBe false
        sections.get(1L) shouldBe null
        disk.containsKey(1L) shouldBe false
    }

    @Test
    fun `get falls back to disk and promotes into memory`() {
        val (sections, _) = store(disk = mutableMapOf(7L to "<p>seven</p>"))

        sections.isResident(7L) shouldBe false
        sections.get(7L) shouldBe "<p>seven</p>"
        sections.isResident(7L) shouldBe true
    }

    @Test
    fun `prepared section restores its base url from disk metadata`() {
        val htmlDisk = mutableMapOf<Long, String>()
        val baseUrlDisk = mutableMapOf<Long, String?>()
        val sections = NovelBookSectionStore(
            maxResidentEntries = 1,
            diskRead = { htmlDisk[it] },
            diskWrite = { id, html -> htmlDisk[id] = html },
            diskBaseUrlRead = { baseUrlDisk[it] },
            diskBaseUrlWrite = { id, baseUrl -> baseUrlDisk[id] = baseUrl },
        )
        sections.put(7L, "<p>seven</p>", baseUrl = "https://example.org/chapter/7/")
        sections.release(7L)

        sections.getPrepared(7L) shouldBe NovelBookPreparedSection(
            html = "<p>seven</p>",
            baseUrl = "https://example.org/chapter/7/",
        )
    }

    @Test
    fun `blank disk entries are treated as missing`() {
        val (sections, _) = store(disk = mutableMapOf(7L to ""))

        sections.get(7L) shouldBe null
        sections.isResident(7L) shouldBe false
    }

    @Test
    fun `memory evicts the least recently used section`() {
        val (sections, disk) = store(maxResidentEntries = 2)

        sections.put(1L, "<p>1</p>")
        sections.put(2L, "<p>2</p>")
        sections.get(1L)
        sections.put(3L, "<p>3</p>")

        sections.residentCount shouldBe 2
        sections.residentSectionKeys shouldBe setOf(1L, 3L)
        // Evicted from memory only; still readable from disk.
        disk[2L] shouldBe "<p>2</p>"
        sections.get(2L) shouldBe "<p>2</p>"
    }

    @Test
    fun `resident limit is at least one`() {
        val (sections, _) = store(maxResidentEntries = 0)

        sections.put(1L, "<p>1</p>")
        sections.put(2L, "<p>2</p>")

        sections.residentSectionKeys shouldBe setOf(2L)
    }

    @Test
    fun `isPrepared covers memory and disk`() {
        val (sections, _) = store(disk = mutableMapOf(9L to "<p>nine</p>"))
        sections.put(1L, "<p>1</p>", persist = false)

        sections.isPrepared(1L) shouldBe true
        sections.isPrepared(9L) shouldBe true
        sections.isPrepared(100L) shouldBe false
    }

    @Test
    fun `release and clear only drop memory`() {
        val (sections, disk) = store()
        sections.put(1L, "<p>1</p>")
        sections.put(2L, "<p>2</p>")

        sections.release(1L)
        sections.isResident(1L) shouldBe false

        sections.clear()
        sections.residentCount shouldBe 0
        disk.keys shouldBe setOf(1L, 2L)
    }

    @Test
    fun `read failures are swallowed`() {
        val sections = NovelBookSectionStore(
            diskRead = { error("boom") },
            diskWrite = { _, _ -> error("boom") },
        )

        sections.put(1L, "<p>1</p>")
        sections.get(1L) shouldBe "<p>1</p>"
        sections.get(2L) shouldBe null
        sections.isPrepared(2L) shouldBe false
    }

    @Test
    fun `the combined disk hooks replace the split ones`() {
        val splitHtml = mutableMapOf<Long, String>()
        val stored = mutableMapOf<Long, NovelBookPreparedSection>()
        val sections = NovelBookSectionStore(
            maxResidentEntries = 1,
            diskRead = { splitHtml[it] },
            diskWrite = { id, html -> splitHtml[id] = html },
            diskReadSection = { stored[it] },
            diskWriteSection = { id, section -> stored[id] = section },
        )
        val expected = NovelBookPreparedSection(
            html = "<p>one</p>",
            baseUrl = "https://example.org/chapter/1/",
        )

        sections.put(1L, expected.html, baseUrl = expected.baseUrl)
        sections.release(1L)

        splitHtml.containsKey(1L) shouldBe false
        stored[1L] shouldBe expected
        sections.getPrepared(1L) shouldBe expected
        sections.isPrepared(1L) shouldBe true
    }

    @Test
    fun `reads fall back to the split hooks when the combined hook is empty`() {
        val sections = NovelBookSectionStore(
            diskRead = { "<p>legacy $it</p>" },
            diskReadSection = { null },
        )

        sections.get(4L) shouldBe "<p>legacy 4</p>"
        sections.isPrepared(4L) shouldBe true
    }

    @Test
    fun `a blank combined disk entry is treated as missing`() {
        val sections = NovelBookSectionStore(
            diskReadSection = { NovelBookPreparedSection(html = "   ") },
        )

        sections.getPrepared(1L) shouldBe null
        sections.isPrepared(1L) shouldBe false
    }

    @Test
    fun `combined hook failures are swallowed`() {
        val sections = NovelBookSectionStore(
            diskReadSection = { error("boom") },
            diskWriteSection = { _, _ -> error("boom") },
        )

        sections.put(1L, "<p>1</p>")
        sections.get(1L) shouldBe "<p>1</p>"
        sections.get(2L) shouldBe null
        sections.isPrepared(2L) shouldBe false
    }
}
