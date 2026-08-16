package eu.kanade.presentation.more.settings.screen.about

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DonationOption(
    val id: String,
    val type: String, // "link" | "crypto"
    val title: String,
    val description: String,
    val tags: List<String>,
    val value: String,
)

object DonationOptionsHelper {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadFromAssets(context: Context): List<DonationOption> {
        return try {
            val jsonString = context.assets.open("donation_options.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<DonationOption>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
