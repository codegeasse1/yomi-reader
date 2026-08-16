package eu.kanade.domain.description

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result of [DescriptionEngine.beautify]: a structured, renderer-agnostic representation of a
 * title description. All screens render these blocks (Aurora cards and classic summaries alike),
 * so the "beautiful description" is identical everywhere by construction.
 */
@Serializable
sealed interface DescriptionBlock {

    /** An ordinary prose paragraph. */
    @Serializable
    @SerialName("paragraph")
    data class Paragraph(val text: String) : DescriptionBlock

    /** A short section label, e.g. "Alternative Titles". */
    @Serializable
    @SerialName("heading")
    data class SectionHeading(val text: String) : DescriptionBlock

    /** A short "Label: value" pair on one line, e.g. "Rating: 9,46". */
    @Serializable
    @SerialName("label_row")
    data class LabelRow(val label: String, val value: String) : DescriptionBlock

    /** A bullet/numbered list item; may carry a tappable [url]. */
    @Serializable
    @SerialName("list_item")
    data class ListItem(val text: String, val url: String? = null) : DescriptionBlock

    /** A line consisting only of links (e.g. MyAnimeList / AniList). */
    @Serializable
    @SerialName("links_row")
    data class LinksRow(val links: List<Link>) : DescriptionBlock

    /** Text that could not be classified safely — rendered as a plain paragraph. */
    @Serializable
    @SerialName("fallback")
    data class Fallback(val text: String) : DescriptionBlock

    @Serializable
    data class Link(val text: String, val url: String)
}
