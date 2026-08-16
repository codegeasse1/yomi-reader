package eu.kanade.tachiyomi.data.backup.lnreader

import eu.kanade.tachiyomi.data.backup.BackupDetector
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LNReaderBackupTest {

    @Test
    fun `v1 flat json backup is detected and imported as novels`() {
        val bytes = V1_JSON.toByteArray()

        assertTrue(LNReaderBackup.isLNReaderContainer(bytes))
        assertEquals(BackupOrigin.LNREADER, BackupDetector.detectOrigin(bytes))

        val backup = LNReaderBackup.decode(bytes)

        // LNReader only ever holds novels, so nothing may land in the manga or anime sections.
        assertTrue(backup.backupManga.isEmpty())
        assertTrue(backup.backupAnime.isEmpty())
        assertEquals(listOf("Novel A", "Novel B"), backup.backupNovel.map { it.title })

        val first = backup.backupNovel.first()
        assertEquals("/novel-a", first.url)
        assertEquals(listOf("Chapter 1", "Chapter 2"), first.chapters.map { it.name })
        assertTrue(first.chapters.first().read)
        assertFalse(first.chapters.last().read)
        assertTrue(first.favorite)

        // A source name must resolve to one stable id, otherwise a second import would duplicate
        // the whole library.
        assertEquals(
            LNReaderBackup.sourceIdFor("novelupdates"),
            LNReaderBackup.sourceIdFor("NovelUpdates"),
        )
        assertTrue(backup.backupNovelSources.any { it.sourceId == first.source })
    }

    @Test
    fun `v2 zip backup with separate chapter and category files is imported`() {
        val zip = zipOf(
            "novels.json" to """
                [
                  {"id": 7, "pluginId": "novelupdates", "path": "/novel-c",
                   "name": "Novel C", "inLibrary": true, "categoryIds": [3]}
                ]
            """.trimIndent(),
            "chapters.json" to """
                [
                  {"novelId": 7, "path": "/novel-c/1", "name": "Ch 1", "unread": false,
                   "bookmark": true}
                ]
            """.trimIndent(),
            "categories.json" to """[{"id": 3, "name": "Reading", "sort": 1}]""",
        )

        assertTrue(LNReaderBackup.isLNReaderContainer(zip))
        assertEquals(BackupOrigin.LNREADER, BackupDetector.detectOrigin(zip))

        val backup = LNReaderBackup.decode(zip)

        assertEquals(listOf("Novel C"), backup.backupNovel.map { it.title })
        val novel = backup.backupNovel.single()
        assertEquals(listOf("Ch 1"), novel.chapters.map { it.name })
        assertTrue(novel.chapters.single().read)
        assertTrue(novel.chapters.single().bookmark)
        assertEquals(listOf("Reading"), backup.backupNovelCategories.map { it.name })
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private companion object {
        val V1_JSON = """
            [
              {"novelId": 1, "sourceId": 50, "source": "NovelUpdates",
               "novelUrl": "/novel-a", "novelName": "Novel A", "author": "Author A",
               "followed": 1, "categoryIds": [1],
               "chapters": [
                 {"chapterId": 11, "novelId": 1, "chapterUrl": "/novel-a/1",
                  "chapterName": "Chapter 1", "read": 1, "bookmark": 0},
                 {"chapterId": 12, "novelId": 1, "chapterUrl": "/novel-a/2",
                  "chapterName": "Chapter 2", "read": 0, "bookmark": 0}
               ]},
              {"novelId": 2, "sourceId": 50, "source": "NovelUpdates",
               "novelUrl": "/novel-b", "novelName": "Novel B", "followed": 1,
               "chapters": []}
            ]
        """.trimIndent()
    }
}
