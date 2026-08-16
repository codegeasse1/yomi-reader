package eu.kanade.tachiyomi.data.backup

/**
 * Where a backup file came from, as detected by [BackupDetector] before the payload is decoded.
 *
 * The origin is a wire level fact: it is derived from the top level protobuf markers (or the
 * container magic bytes for foreign apps) and never from the set of extensions installed on this
 * device. It is the single input of the media routing policy, so a backup restores the same way on
 * every device.
 */
enum class BackupOrigin {
    /** Native Yomi backup: sections are already split (field 1 manga, 501 anime, 508 novel). */
    TADAMI,

    /** Yomi sister app compatible export: Mihon shaped, but carries a Yomi manifest. */
    TADAMI_SISTER,

    /** Legacy Aniyomi/Yomi backup: anime at field 3, novel at field 5. */
    LEGACY_ANIYOMI,

    /** External Mihon backup. Field 1 is manga, always. */
    MIHON,

    /** External TachiyomiSY backup. Mihon compatible, plus SY-only fields. */
    TACHIYOMI_SY,

    /** External Komikku backup. Mihon compatible, plus Komikku-only fields. */
    KOMIKKU,

    /** External LNReader backup (JSON or ZIP container). Every entry is a novel. */
    LNREADER,
    ;

    /** Backups written by Yomi itself, where the declared sections are authoritative. */
    val isNative: Boolean
        get() = this == TADAMI || this == LEGACY_ANIYOMI

    /** Mihon and its forks: one shared manga section, no reliable media type information. */
    val isMihonDerived: Boolean
        get() = this == MIHON || this == TACHIYOMI_SY || this == KOMIKKU
}

/**
 * Explicit, user chosen rules applied to a single restore.
 *
 * A markerless Yomi sister backup is byte for byte indistinguishable from a real Mihon backup, so
 * the novels inside it cannot be recovered without the user telling us that the file came from
 * Yomi. That decision travels with the restore as this policy instead of a global flag, so a
 * background or auto restore can never silently reclassify a library.
 */
data class BackupImportPolicy(
    /**
     * When true, a markerless Mihon shaped payload is treated as an old Yomi sister export and a
     * conservative novel fallback is allowed: an entry moves to the novel section only when its
     * source is unambiguously known as a novel source and not as a manga or anime source.
     */
    val legacySisterFallback: Boolean = false,
) {
    companion object {
        /** No guessing: declared sections and origin only. */
        val Default = BackupImportPolicy()
    }
}

/**
 * Exact per media type counts of a backup payload.
 *
 * Used to assert that what was serialized is what actually landed in the destination file, and that
 * what was decoded is what actually reached the database.
 */
data class BackupContentSummary(
    val mangaCount: Int = 0,
    val animeCount: Int = 0,
    val novelCount: Int = 0,
    val categoriesCount: Int = 0,
) {
    val totalEntries: Int
        get() = mangaCount + animeCount + novelCount
}

/**
 * Proof that a backup was written to its destination intact.
 *
 * Produced by the staged writer after it re-reads the destination URI: the digest and byte length
 * are compared against the staging file, and the summary is re-derived from a full decode of the
 * destination, not from the in-memory model.
 */
data class BackupWriteReceipt(
    val byteLength: Long,
    val sha256: String,
    val origin: BackupOrigin,
    val summary: BackupContentSummary,
)
