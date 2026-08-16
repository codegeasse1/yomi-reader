package eu.kanade.domain.description

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializes [DescriptionBlock]s to/from the JSON stored in the database
 * (`description_blocks` column).
 */
object DescriptionBlockCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun encode(blocks: List<DescriptionBlock>): String = json.encodeToString(blocks)

    fun decode(raw: String?): List<DescriptionBlock>? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<List<DescriptionBlock>>(raw) }
            .getOrNull()
    }
}
