package eu.kanade.tachiyomi.data.export.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities

internal object EpubXhtmlSanitizer {

    fun parseBodyFragment(html: String): Document {
        return Jsoup.parseBodyFragment(html).also { document ->
            configureOutput(document)
            sanitize(document)
        }
    }

    fun bodyHtml(document: Document): String {
        configureOutput(document)
        sanitize(document)
        return document.body().html()
    }

    private fun configureOutput(document: Document) {
        document.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset(Charsets.UTF_8)
            .prettyPrint(false)
    }

    private fun sanitize(document: Document) {
        document.select("script").remove()
        document.allElements.forEach { element ->
            val attributesToRemove = element.attributes()
                .asList()
                .map { it.key }
                .filter { key ->
                    key.startsWith("on", ignoreCase = true) ||
                        isUnsafeUrlAttribute(key, element.attr(key))
                }
            attributesToRemove.forEach(element::removeAttr)
        }
    }

    private fun isUnsafeUrlAttribute(key: String, value: String): Boolean {
        if (!key.equals("href", ignoreCase = true) &&
            !key.equals("src", ignoreCase = true) &&
            !key.endsWith(":href", ignoreCase = true)
        ) {
            return false
        }
        val normalized = value.trim().lowercase()
        return normalized.startsWith("javascript:")
    }
}
