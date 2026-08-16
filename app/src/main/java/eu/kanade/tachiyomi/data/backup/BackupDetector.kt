package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.lnreader.LNReaderBackup
import eu.kanade.tachiyomi.data.backup.models.MihonBackup
import eu.kanade.tachiyomi.data.backup.models.YomiSisterManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Try to guess if the backup is an old aniyomi backup.
 *
 * Returns true if it's (probably) an old aniyomi backup, or false if it's a mihon backup
 * or a new aniyomi backup.
 */
object BackupDetector {
    @Serializable
    data class BackupDetector(
        @ProtoNumber(103) val backupAnimeSources: List<DetectAnimeSource> = emptyList(),
        @ProtoNumber(500) val isLegacy: Boolean = true,
    ) {
        @Serializable
        data class DetectAnimeSource(
            @ProtoNumber(1) val name: String = "",
            @ProtoNumber(2) val sourceId: Long,
        )
    }

    fun isLegacyBackup(bytes: ByteArray): Boolean {
        return try {
            val fields = topLevelFieldNumbers(bytes)
            // Legacy Aniyomi/Yomi stores anime/novel at top-level fields 3 and 5.
            if (LEGACY_ANIME_FIELD in fields || LEGACY_NOVEL_FIELD in fields) {
                return true
            }
            val detect = ProtoBuf.decodeFromByteArray(BackupDetector.serializer(), bytes)
            detect.isLegacy && detect.backupAnimeSources.isNotEmpty()
        } catch (_: SerializationException) {
            false
        }
    }

    /** True when the wire format still carries legacy anime/novel payload fields. */
    fun hasLegacyPayloadFields(bytes: ByteArray): Boolean {
        return try {
            val fields = topLevelFieldNumbers(bytes)
            LEGACY_ANIME_FIELD in fields || LEGACY_NOVEL_FIELD in fields
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Positively identify a Mihon / Tachiyomi(-derived) backup (as opposed to a
     * native Aniyomi/Yomi backup).
     *
     * Native Aniyomi/Yomi backups always carry at least one "native marker" field
     * at the top level: legacy backupAnime(3), backupAnimeCategories(4),
     * legacy backupNovel(5), backupNovelCategories(6), or one of Yomi's
     * unambiguous native fields. BackupCreator always writes isLegacy=false at
     * field 500. Forks such as SY and Komikku also use fields in the 600+ range,
     * so treating every field >= 500 as native would reject compatible backups.
     *
     * So a backup with no native marker but recognizable Mihon content is a Mihon
     * backup. Must be checked AFTER [isLegacyBackup].
     */
    fun isMihonBackup(bytes: ByteArray): Boolean {
        return detectOrigin(bytes) in MIHON_DERIVED_ORIGINS
    }

    /**
     * Deterministically classify the backup from its content markers only.
     *
     * Installed extensions are never consulted: which app wrote a file is a property of the file.
     */
    fun detectOrigin(bytes: ByteArray): BackupOrigin {
        // LNReader does not use protobuf at all: it writes a JSON document (1.x) or a ZIP
        // container (2.x), so it is recognisable before any protobuf parsing is attempted.
        if (LNReaderBackup.isLNReaderContainer(bytes)) return BackupOrigin.LNREADER

        if (isLegacyBackup(bytes)) return BackupOrigin.LEGACY_ANIYOMI

        val fields = try {
            topLevelFieldNumbers(bytes)
        } catch (_: Exception) {
            return BackupOrigin.TADAMI
        }

        return when {
            fields.any { it in NATIVE_MARKER_FIELDS } -> BackupOrigin.TADAMI
            // Our own sister-app export: Mihon shaped, but carrying a manifest we can verify.
            hasConfirmedSisterManifest(bytes, fields) -> BackupOrigin.TADAMI_SISTER
            KOMIKKU_FEED_FIELD in fields -> BackupOrigin.KOMIKKU
            TACHIYOMI_SY_SAVED_SEARCH_FIELD in fields -> BackupOrigin.TACHIYOMI_SY
            fields.any { it in MIHON_CONTENT_FIELDS } -> BackupOrigin.MIHON
            else -> BackupOrigin.TADAMI
        }
    }

    /**
     * True only when field 20000 is present *and* decodes into a manifest with our signature and a
     * version this build understands. The presence of the field number alone proves nothing, since
     * any other app is free to use it.
     */
    private fun hasConfirmedSisterManifest(bytes: ByteArray, fields: Set<Int>): Boolean {
        if (YomiSisterManifest.PROTO_FIELD !in fields) return false
        return try {
            ProtoBuf.decodeFromByteArray(MihonBackup.serializer(), bytes)
                .tadamiManifest
                ?.isValid == true
        } catch (_: Exception) {
            false
        }
    }

    private val MIHON_CONTENT_FIELDS = setOf(1, 2, 101, 104, 105, 106)
    private val MIHON_DERIVED_ORIGINS =
        setOf(BackupOrigin.MIHON, BackupOrigin.TACHIYOMI_SY, BackupOrigin.KOMIKKU)
    private val NATIVE_MARKER_FIELDS =
        setOf(LEGACY_ANIME_FIELD, 4, LEGACY_NOVEL_FIELD, 6) +
            (500..510) +
            (620..622) +
            (650..652)

    private const val LEGACY_ANIME_FIELD = 3
    private const val LEGACY_NOVEL_FIELD = 5
    private const val TACHIYOMI_SY_SAVED_SEARCH_FIELD = 600
    private const val KOMIKKU_FEED_FIELD = 610

    /**
     * Walk the top level of a protobuf message and collect the field numbers present.
     * Nested messages are skipped wholesale (not recursed into).
     */
    private fun topLevelFieldNumbers(bytes: ByteArray): Set<Int> {
        val fields = mutableSetOf<Int>()
        var pos = 0
        while (pos < bytes.size) {
            val (tag, afterTag) = readVarint(bytes, pos)
            pos = afterTag
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            if (fieldNumber == 0) throw SerializationException("Invalid protobuf field number 0")
            fields += fieldNumber
            pos = when (wireType) {
                0 -> readVarint(bytes, pos).second // varint
                1 -> pos + 8 // 64-bit
                2 -> { // length-delimited
                    val (len, afterLen) = readVarint(bytes, pos)
                    afterLen + len.toInt()
                }
                5 -> pos + 4 // 32-bit
                else -> throw SerializationException("Unsupported protobuf wire type $wireType")
            }
            if (pos > bytes.size) throw SerializationException("Truncated protobuf message")
        }
        return fields
    }

    /** Reads a base-128 varint. Returns (value, indexAfterVarint). */
    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i].toInt()
            result = result or ((b.toLong() and 0x7F) shl shift)
            i++
            if (b and 0x80 == 0) return result to i
            shift += 7
            if (shift >= 64) throw SerializationException("Varint too long")
        }
        throw SerializationException("Truncated varint")
    }
}
