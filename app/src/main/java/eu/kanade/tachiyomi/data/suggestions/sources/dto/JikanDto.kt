package eu.kanade.tachiyomi.data.suggestions.sources.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JikanSearchResponse(
    val data: List<JikanSearchEntry> = emptyList(),
)

@Serializable
data class JikanSearchEntry(
    @SerialName("mal_id") val malId: Int,
    val title: String,
    val type: String? = null,
)

@Serializable
data class JikanRecommendationResponse(
    val data: List<JikanRecommendationItem> = emptyList(),
)

@Serializable
data class JikanRecommendationItem(
    val entry: JikanRecommendationEntry,
    val url: String,
    val votes: Int = 0,
)

@Serializable
data class JikanRecommendationEntry(
    @SerialName("mal_id") val malId: Int,
    val title: String,
    val url: String,
    val images: JikanImages? = null,
)

@Serializable
data class JikanImages(
    val jpg: JikanJpgImages? = null,
)

@Serializable
data class JikanJpgImages(
    @SerialName("image_url") val imageUrl: String? = null,
)
