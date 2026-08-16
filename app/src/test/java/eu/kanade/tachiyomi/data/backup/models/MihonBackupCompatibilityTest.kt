package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.data.backup.BackupDetector
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MihonBackupCompatibilityTest {

    @Test
    fun `sister app export omits Yomi-only root fields and decodes with strict Mihon schema`() {
        val tadamiBackup = sampleYomiBackup().copy(
            isLegacy = false,
            backupNovel = listOf(sampleNovel()),
            backupAchievements = listOf(sampleAchievement()),
        )

        val nativeBytes = ProtoBuf.encodeToByteArray(Backup.serializer(), tadamiBackup)
        val nativeDecoded = ProtoBuf.decodeFromByteArray(StrictMihonBackup.serializer(), nativeBytes)
        assertEquals(listOf("Manga title"), nativeDecoded.backupManga.map { it.title })

        val compatibleBytes = ProtoBuf.encodeToByteArray(
            MihonBackup.serializer(),
            tadamiBackup.toMihonBackup(),
        )
        val decoded = ProtoBuf.decodeFromByteArray(StrictMihonBackup.serializer(), compatibleBytes)

        assertEquals(listOf("Manga title", "Novel title"), decoded.backupManga.map { it.title })
        assertEquals("Chapter 1", decoded.backupManga.first().chapters.first().name)
        assertEquals(1, decoded.backupExtensionRepo.size)
    }

    @Test
    fun `opt-in legacy sister routing only moves novel-only sources and never invents anime`() {
        val mihonBackup = MihonBackup(
            backupManga = listOf(
                sampleManga().copy(source = 1, title = "Manga title").toMihonBackupManga(),
                sampleManga().copy(source = 2, title = "Novel title").toMihonBackupManga(),
                sampleManga().copy(source = 3, title = "Anime title").toMihonBackupManga(),
            ),
            backupCategories = listOf(BackupCategory(name = "Default", order = 0)),
            backupSources = listOf(
                BackupSource(name = "Manga source", sourceId = 1),
                BackupSource(name = "Novel source", sourceId = 2),
                BackupSource(name = "Anime source", sourceId = 3),
            ),
        )

        val decodedAsYomi = ProtoBuf.decodeFromByteArray(
            Backup.serializer(),
            ProtoBuf.encodeToByteArray(MihonBackup.serializer(), mihonBackup),
        )
        val routed = MediaRoutingPolicy.LegacySisterExplicitFallback(
            mangaSourceClassifier = { it == 1L },
            novelSourceClassifier = { it == 2L },
            animeSourceClassifier = { it == 3L },
        ).route(decodedAsYomi)

        // Only the novel-only source moves. The anime-only source stays manga: a Mihon-shaped file
        // never declares anime, so moving it there would be a guess.
        assertEquals(
            listOf("Manga title", "Anime title"),
            routed.backup.backupManga.map { it.title },
        )
        assertEquals(listOf("Novel title"), routed.backup.backupNovel.map { it.title })
        assertTrue(routed.backup.backupAnime.isEmpty())
        assertEquals(0, routed.ambiguousEntries)
        assertEquals(listOf("Default"), routed.backup.backupNovelCategories.map { it.name })
        assertEquals(listOf("Novel source"), routed.backup.backupNovelSources.map { it.name })
    }

    @Test
    fun `sister manifest routes flattened entries back without looking at installed sources`() {
        val tadamiBackup = sampleYomiBackup().copy(
            isLegacy = false,
            backupNovel = listOf(sampleNovel()),
        )

        val bytes = ProtoBuf.encodeToByteArray(
            MihonBackup.serializer(),
            tadamiBackup.toMihonBackup(),
        )
        val decodedMihon = ProtoBuf.decodeFromByteArray(MihonBackup.serializer(), bytes)
        val hints = decodedMihon.validManifestHints()
        assertTrue(hints != null && hints.isNotEmpty())

        val routed = MediaRoutingPolicy.RestoreFromYomiManifest(hints!!)
            .route(decodedMihon.toYomiBackup())

        assertEquals(listOf("Manga title"), routed.backup.backupManga.map { it.title })
        assertEquals(listOf("Novel title"), routed.backup.backupNovel.map { it.title })
        assertEquals(0, routed.ambiguousEntries)
    }

    @Test
    fun `Mihon backup extension repos use proto field 106 and restore into Yomi manga repos`() {
        val mihonBackup = MihonBackup(
            backupManga = listOf(sampleManga().toMihonBackupManga()),
            backupExtensionRepo = listOf(sampleRepo()),
        )

        val bytes = ProtoBuf.encodeToByteArray(MihonBackup.serializer(), mihonBackup)
        val decoded = ProtoBuf.decodeFromByteArray(MihonBackup.serializer(), bytes)
        val tadamiBackup = decoded.toYomiBackup()

        assertEquals(1, tadamiBackup.backupMangaExtensionRepo.size)
        assertEquals("Repo", tadamiBackup.backupMangaExtensionRepo.first().name)
    }

    @Test
    fun `foreign SY and Komikku fields are accepted by Mihon fallback decoding`() {
        val foreignBackup = ForeignBackup(
            backupManga = listOf(
                ForeignManga(
                    source = 1,
                    url = "/manga",
                    title = "Foreign title",
                    chapters = listOf(BackupChapter(url = "/chapter-1", name = "Chapter 1")),
                    mergedMangaReferences = listOf("sy-extra"),
                ),
            ),
            savedSearches = listOf("sy-search"),
            feeds = listOf("komikku-feed"),
        )
        val bytes = ProtoBuf.encodeToByteArray(ForeignBackup.serializer(), foreignBackup)

        assertFalse(BackupDetector.isLegacyBackup(bytes))
        assertTrue(BackupDetector.isMihonBackup(bytes))

        val decoded = ProtoBuf.decodeFromByteArray(MihonBackup.serializer(), bytes)

        assertEquals("Foreign title", decoded.backupManga.first().title)
        assertEquals("Chapter 1", decoded.backupManga.first().chapters.first().name)
    }

    @Test
    fun `origin detector identifies TachiyomiSY from its saved-search field`() {
        val bytes = ProtoBuf.encodeToByteArray(
            ForeignBackup.serializer(),
            ForeignBackup(
                backupManga = listOf(sampleForeignManga()),
                savedSearches = listOf("sy-search"),
            ),
        )

        assertEquals(BackupOrigin.TACHIYOMI_SY, BackupDetector.detectOrigin(bytes))
    }

    @Test
    fun `origin detector identifies Komikku from its feed field`() {
        val bytes = ProtoBuf.encodeToByteArray(
            ForeignBackup.serializer(),
            ForeignBackup(
                backupManga = listOf(sampleForeignManga()),
                feeds = listOf("komikku-feed"),
            ),
        )

        assertEquals(BackupOrigin.KOMIKKU, BackupDetector.detectOrigin(bytes))
    }

    @Test
    fun `origin detector gives native Yomi marker precedence over fork fields`() {
        val bytes = ProtoBuf.encodeToByteArray(
            Backup.serializer(),
            Backup(
                backupManga = listOf(sampleManga()),
                backupAchievements = listOf(sampleAchievement()),
                isLegacy = false,
            ),
        )

        assertEquals(BackupOrigin.TADAMI, BackupDetector.detectOrigin(bytes))
    }

    @Test
    fun `Mihon manga diverging fields 110 notes 111 initialized 112 memo decode losslessly via Mihon schema`() {
        val realMihonBytes = ProtoBuf.encodeToByteArray(
            RealMihonBackup.serializer(),
            RealMihonBackup(
                backupManga = listOf(
                    RealMihonManga(
                        source = 1,
                        url = "/manga",
                        title = "Manga title",
                        chapters = listOf(
                            RealMihonChapter(url = "/c1", name = "Chapter 1", memo = byteArrayOf(1, 2, 3)),
                        ),
                        excludedScanlators = listOf("BadGroup"),
                        version = 7,
                        notes = "My note",
                        initialized = true,
                        memo = byteArrayOf(9, 9),
                    ),
                ),
            ),
        )

        // The dedicated Mihon schema preserves the diverging fields and skips the
        // Mihon-only ones (initialized/memo) without crashing.
        val viaMihon = ProtoBuf.decodeFromByteArray(MihonBackup.serializer(), realMihonBytes)
            .toYomiBackup()
        val manga = viaMihon.backupManga.single()
        assertEquals("Manga title", manga.title)
        assertEquals(7L, manga.version)
        assertEquals("My note", manga.notes)
        assertEquals(listOf("BadGroup"), manga.excludedScanlators)
        assertEquals("Chapter 1", manga.chapters.single().name)
        // Mihon's chapter memo (field 13) must NOT leak into Yomi's dateUploadRaw.
        assertEquals(null, manga.chapters.single().dateUploadRaw)

        // Decoding the same bytes with the Yomi schema fails on the wire-type
        // mismatch at field 110 (rating Float vs notes String) / 111 (notes String
        // vs initialized Boolean) - exactly why BackupDecoder falls back to Mihon.
        assertFailsWith<SerializationException> {
            ProtoBuf.decodeFromByteArray(Backup.serializer(), realMihonBytes)
        }
    }

    @Test
    fun `Mihon manga round-trips through MihonBackupManga mapper`() {
        val original = sampleManga().copy(
            version = 3,
            notes = "Note",
            excludedScanlators = listOf("Group A", "Group B"),
        )
        val restored = original.toMihonBackupManga().toBackupManga()
        assertEquals(original.title, restored.title)
        assertEquals(original.version, restored.version)
        assertEquals(original.notes, restored.notes)
        assertEquals(original.excludedScanlators, restored.excludedScanlators)
    }

    @Test
    fun `isMihonBackup detects a Mihon-shaped backup`() {
        val bytes = ProtoBuf.encodeToByteArray(
            MihonBackup.serializer(),
            MihonBackup(
                backupManga = listOf(sampleManga().toMihonBackupManga()),
                backupExtensionRepo = listOf(sampleRepo()),
            ),
        )
        assertTrue(BackupDetector.isMihonBackup(bytes))
    }

    @Test
    fun `isMihonBackup rejects a native Yomi backup (isLegacy field 500 present)`() {
        // BackupCreator always writes isLegacy=false, which is a non-default value
        // and therefore serialized at field 500 - the native marker.
        val bytes = ProtoBuf.encodeToByteArray(
            Backup.serializer(),
            Backup(backupManga = listOf(sampleManga()), isLegacy = false),
        )
        assertFalse(BackupDetector.isMihonBackup(bytes))
    }

    @Test
    fun `isMihonBackup rejects a Yomi backup containing novel entries (field 5)`() {
        val bytes = ProtoBuf.encodeToByteArray(
            Backup.serializer(),
            Backup(
                backupManga = listOf(sampleManga()),
                backupNovel = listOf(sampleNovel()),
                isLegacy = false,
            ),
        )
        assertFalse(BackupDetector.isMihonBackup(bytes))
    }

    private fun sampleYomiBackup(): Backup {
        return Backup(
            backupManga = listOf(sampleManga()),
            backupCategories = listOf(BackupCategory(name = "Default", order = 0)),
            backupSources = listOf(BackupSource(name = "Source", sourceId = 1)),
            backupMangaExtensionRepo = listOf(sampleRepo()),
        )
    }

    private fun sampleManga(): BackupManga {
        return BackupManga(
            source = 1,
            url = "/manga",
            title = "Manga title",
            chapters = listOf(BackupChapter(url = "/chapter-1", name = "Chapter 1")),
        )
    }

    private fun sampleNovel(): BackupNovel {
        return BackupNovel(
            source = 2,
            url = "/novel",
            title = "Novel title",
            chapters = listOf(BackupChapter(url = "/novel-chapter-1", name = "Novel chapter 1")),
        )
    }

    private fun sampleRepo(): BackupExtensionRepos {
        return BackupExtensionRepos(
            baseUrl = "https://example.invalid",
            name = "Repo",
            shortName = null,
            website = "https://example.invalid",
            signingKeyFingerprint = "fingerprint",
        )
    }

    private fun sampleAchievement(): BackupAchievement {
        return BackupAchievement(
            id = "first_backup",
            title = "First backup",
            description = "Create a backup",
            category = 0,
            type = 0,
        )
    }

    private fun sampleForeignManga(): ForeignManga {
        return ForeignManga(
            source = 1,
            url = "/foreign",
            title = "Foreign title",
        )
    }

    @Serializable
    private data class StrictMihonBackup(
        @ProtoNumber(1) val backupManga: List<BackupManga>,
        @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
        @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
        @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
        @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
        @ProtoNumber(106) var backupExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    )

    @Serializable
    private data class ForeignBackup(
        @ProtoNumber(1) val backupManga: List<ForeignManga> = emptyList(),
        @ProtoNumber(600) var savedSearches: List<String> = emptyList(),
        @ProtoNumber(610) var feeds: List<String> = emptyList(),
    )

    @Serializable
    private data class ForeignManga(
        @ProtoNumber(1) var source: Long,
        @ProtoNumber(2) var url: String,
        @ProtoNumber(3) var title: String = "",
        @ProtoNumber(16) var chapters: List<BackupChapter> = emptyList(),
        @ProtoNumber(600) var mergedMangaReferences: List<String> = emptyList(),
    )

    // Mirrors the REAL Mihon / Tachiyomi(-derived) manga + chapter numbering, which
    // diverges from Yomi on manga fields 110/111/112 and chapter field 13.
    @Serializable
    private data class RealMihonBackup(
        @ProtoNumber(1) val backupManga: List<RealMihonManga> = emptyList(),
    )

    @Serializable
    private data class RealMihonManga(
        @ProtoNumber(1) var source: Long,
        @ProtoNumber(2) var url: String,
        @ProtoNumber(3) var title: String = "",
        @ProtoNumber(16) var chapters: List<RealMihonChapter> = emptyList(),
        @ProtoNumber(108) var excludedScanlators: List<String> = emptyList(),
        @ProtoNumber(109) var version: Long = 0,
        @ProtoNumber(110) var notes: String = "",
        @ProtoNumber(111) var initialized: Boolean = false,
        @ProtoNumber(112) var memo: ByteArray = ByteArray(0),
    )

    @Serializable
    private data class RealMihonChapter(
        @ProtoNumber(1) var url: String,
        @ProtoNumber(2) var name: String,
        @ProtoNumber(13) var memo: ByteArray = ByteArray(0),
    )
}
