package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.model.IncognitoPolicy

internal object IncognitoStateLogic {
    fun resolve(
        globalIncognito: Boolean,
        policy: IncognitoPolicy,
        isNsfw: Boolean,
        inExtensionSet: Boolean,
    ): Boolean {
        if (globalIncognito) return true
        if (policy == IncognitoPolicy.NSFW_AUTO && isNsfw) return true
        return inExtensionSet
    }
}
