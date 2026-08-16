package eu.kanade.tachiyomi.ui.browse

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Regression for the `BadParcelableException` crash: the carousel screen is a Voyager
 * [cafe.adriel.voyager.core.screen.Screen] and is Java-serialized into the saved navigation state
 * whenever the activity stops. The previous `TitleCarouselType` sealed interface with `data
 * object`s did not implement Serializable, so the write threw
 * `NotSerializableException: TitleCarouselType$Novel` inside `Parcel.writeSerializable`.
 */
class TitleCarouselScreenTest {

    @Test
    fun `title carousel screen survives java serialization`() {
        val screen = TitleCarouselScreen(
            type = TitleCarouselType.Novel,
            sourceId = 123L,
            initialTitleIds = listOf(1L, 2L, 3L),
            initialIndex = 1,
            listingQuery = "popular",
        )

        roundTrip(screen).shouldNotBeNull()
    }

    @Test
    fun `every carousel type survives java serialization`() {
        for (type in TitleCarouselType.entries) {
            roundTrip(type) shouldBe type
        }
    }

    private fun <T> roundTrip(value: T): T =
        ObjectInputStream(
            ByteArrayInputStream(
                ByteArrayOutputStream().use { bytes ->
                    ObjectOutputStream(bytes).use { it.writeObject(value) }
                    bytes.toByteArray()
                },
            ),
        ).use { input ->
            @Suppress("UNCHECKED_CAST")
            input.readObject() as T
        }
}
