package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class EpisodeListDensity(
    val titleRes: StringResource,
) {
    Comfortable(
        titleRes = AYMR.strings.pref_episode_list_density_comfortable,
    ),
    Compact(
        titleRes = AYMR.strings.pref_episode_list_density_compact,
    ),
    Dense(
        titleRes = AYMR.strings.pref_episode_list_density_dense,
    ),
}
