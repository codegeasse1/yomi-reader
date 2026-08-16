package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.lnreader.LNReaderBackup
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import eu.kanade.tachiyomi.data.backup.models.MediaRoutingPolicy
import eu.kanade.tachiyomi.data.backup.models.MihonBackup
import eu.kanade.tachiyomi.data.backup.models.RoutedBackup
import eu.kanade.tachiyomi.data.backup.models.mergeLegacyPayloadIfPresent
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

/**
 * Turns a backup file into a [Backup], together with the fact of where it came from.
 *
 * Decoding is split into two independent decisions:
 * 1. **Which schema parses the bytes** - decided purely from wire level markers by
 *    [BackupDetector]. Installed extensions are never consulted for this.
 * 2. **Which media section each entry belongs to** - decided by a [MediaRoutingPolicy]. Native and
 *    legacy files already declare their sections, a Yomi sister export carries an explicit
 *    manifest, and an external Mihon style file is treated as what it is: a manga library.
 *
 * Guessing a media type from a source id is deliberately not possible here. Source ids are hashes
 * of name/lang/version and collide across manga, novel and anime extensions, so such a guess would
 * silently move a user's entries into the wrong library.
 */
class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
) {
    /**
     * Decode a potentially-gzipped backup.
     */
    fun decode(uri: Uri): Backup = decodeDetailed(uri).backup

    /**
     * Decode a potentially-gzipped backup, also reporting the detected origin and the policy that
     * was applied.
     *
     * @param policy user supplied import rules. The default never reclassifies anything.
     */
    fun decodeDetailed(
        uri: Uri,
        policy: BackupImportPolicy = BackupImportPolicy.Default,
    ): DecodedBackup {
        val payload = context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer()
            val peeked = source.peek().apply { require(2) }
            val id1id2 = peeked.readShort()
            // 0x1f8b is the gzip magic. Everything else is read as-is: a plain protobuf backup, or
            // a LNReader JSON/ZIP container.
            when (id1id2.toInt()) {
                GZIP_MAGIC -> source.gzip().buffer()
                else -> source
            }.use { it.readByteArray() }
        }
        return decodeBytes(payload, policy)
    }

    /**
     * Decode already uncompressed backup bytes.
     *
     * Used by the backup writer to verify what it just stored without going through a Uri.
     */
    fun decodeBytes(
        payload: ByteArray,
        policy: BackupImportPolicy = BackupImportPolicy.Default,
    ): DecodedBackup {
        val origin = BackupDetector.detectOrigin(payload)
        return try {
            decodeAs(origin, payload, policy)
        } catch (e: Exception) {
            // Detection reads only the top level field numbers, so a hand-edited or unusual file
            // can still fail against the chosen schema. Retry with the most permissive schema, but
            // keep the detected origin: the retry says something about our parser, not about which
            // app wrote the file, and lying about the origin here would change how the entries are
            // routed.
            try {
                val decoded = parser.decodeFromByteArray(MihonBackup.serializer(), payload)
                val routed = routeExternal(decoded.toYomiBackup(), policy)
                DecodedBackup(routed.backup, origin, policy, routed.ambiguousEntries)
            } catch (_: Exception) {
                if (LNReaderBackup.isLNReaderContainer(payload)) {
                    // A JSON payload we could not read as a LNReader backup either.
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }
                throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
            }
        }
    }

    private fun decodeAs(
        origin: BackupOrigin,
        payload: ByteArray,
        policy: BackupImportPolicy,
    ): DecodedBackup = when (origin) {
        BackupOrigin.LNREADER -> {
            // LNReader stores a novel library only, so every entry is a novel by construction and
            // no routing decision is needed.
            DecodedBackup(LNReaderBackup.decode(payload), origin, policy)
        }

        BackupOrigin.LEGACY_ANIYOMI -> {
            val backup = parser.decodeFromByteArray(LegacyBackup.serializer(), payload).toBackup()
            val routed = MediaRoutingPolicy.PreserveDeclaredSections.route(backup)
            DecodedBackup(routed.backup, origin, policy, routed.ambiguousEntries)
        }

        BackupOrigin.TADAMI -> {
            val routed = MediaRoutingPolicy.PreserveDeclaredSections.route(decodeNativeBackup(payload))
            DecodedBackup(routed.backup, origin, policy, routed.ambiguousEntries)
        }

        BackupOrigin.TADAMI_SISTER -> {
            // Our own compatible export: the manifest states the original type of every flattened
            // entry, so novels come back as novels even when no novel extension is installed.
            val decoded = parser.decodeFromByteArray(MihonBackup.serializer(), payload)
            val routed = MediaRoutingPolicy
                .RestoreFromYomiManifest(decoded.validManifestHints().orEmpty())
                .route(decoded.toYomiBackup())
            DecodedBackup(routed.backup, origin, policy, routed.ambiguousEntries)
        }

        BackupOrigin.MIHON,
        BackupOrigin.TACHIYOMI_SY,
        BackupOrigin.KOMIKKU,
        -> {
            // Decoded with the dedicated Mihon schema so its diverging manga fields (notes at 110)
            // survive instead of being misread by the Yomi schema.
            val decoded = parser.decodeFromByteArray(MihonBackup.serializer(), payload)
            val routed = routeExternal(decoded.toYomiBackup(), policy)
            DecodedBackup(routed.backup, origin, policy, routed.ambiguousEntries)
        }
    }

    /**
     * Routing for a markerless, Mihon shaped payload.
     *
     * By default nothing moves. Only when the user explicitly stated that the file is an old Yomi
     * sister export do installed sources get consulted, and even then only to move entries whose
     * source is unambiguously a novel source.
     */
    private fun routeExternal(backup: Backup, policy: BackupImportPolicy): RoutedBackup {
        return if (policy.legacySisterFallback) {
            MediaRoutingPolicy.LegacySisterExplicitFallback(
                mangaSourceClassifier = { mangaSourceManager.get(it) != null },
                novelSourceClassifier = { novelSourceManager.get(it) != null },
                animeSourceClassifier = { animeSourceManager.get(it) != null },
            ).route(backup)
        } else {
            MediaRoutingPolicy.ExternalMihonAsManga(
                novelSourceClassifier = { novelSourceManager.get(it) != null },
                animeSourceClassifier = { animeSourceManager.get(it) != null },
            ).route(backup)
        }
    }

    private fun decodeNativeBackup(backupString: ByteArray): Backup {
        val decoded = parser.decodeFromByteArray(Backup.serializer(), backupString)
        val merged = if (BackupDetector.hasLegacyPayloadFields(backupString)) {
            try {
                decoded.mergeLegacyPayloadIfPresent(
                    parser.decodeFromByteArray(LegacyBackup.serializer(), backupString),
                )
            } catch (_: Exception) {
                decoded
            }
        } else {
            decoded
        }
        return merged
    }

    companion object {
        private const val GZIP_MAGIC = 0x1f8b
    }
}
