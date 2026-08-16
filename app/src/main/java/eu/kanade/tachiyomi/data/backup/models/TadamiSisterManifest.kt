package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Media type of a single flattened entry of a sister app compatible export.
 */
@Serializable
enum class YomiMediaType {
    MANGA,
    NOVEL,
    ANIME,
}

/**
 * Type hint for one flattened entry.
 *
 * The key is the pair (sourceId, url), never sourceId alone: a manga and a novel are allowed to
 * share a source id, and only the url separates them.
 */
@Serializable
data class YomiMediaTypeHint(
    @ProtoNumber(1) val sourceId: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val mediaType: YomiMediaType,
)

/**
 * Vendor extension carried by a Yomi sister app compatible backup.
 *
 * It lives at top level field 20000 of MihonBackup. A strict Mihon decoder does not declare that
 * field, so it skips it and keeps reading field 1 as usual: the export stays wire compatible while
 * Yomi can still recover the original media type of every flattened entry without consulting the
 * installed extensions.
 */
@Serializable
data class YomiSisterManifest(
    @ProtoNumber(1) val signature: String = SIGNATURE,
    @ProtoNumber(2) val version: Int = VERSION,
    @ProtoNumber(3) val entries: List<YomiMediaTypeHint> = emptyList(),
) {
    /**
     * A manifest is only trusted when it decodes with the expected signature and a version this
     * build understands. A bare field number is not proof of anything.
     */
    val isValid: Boolean
        get() = signature == SIGNATURE && version in 1..VERSION

    /**
     * Hints keyed by the pair (sourceId, url).
     *
     * Returns null when the manifest is self-inconsistent: duplicated keys carrying different media
     * types, or blank urls. A corrupt manifest is a validation error, never a reason to guess.
     */
    fun hintsByKey(): Map<Pair<Long, String>, YomiMediaType>? {
        val hints = mutableMapOf<Pair<Long, String>, YomiMediaType>()
        entries.forEach { hint ->
            if (hint.url.isBlank()) return null
            val key = hint.sourceId to hint.url
            val existing = hints[key]
            if (existing != null && existing != hint.mediaType) return null
            hints[key] = hint.mediaType
        }
        return hints
    }

    companion object {
        const val SIGNATURE = "TADAMI_SISTER"
        const val VERSION = 1

        /** Top level protobuf field used to carry the manifest inside a Mihon shaped payload. */
        const val PROTO_FIELD = 20000
    }
}
