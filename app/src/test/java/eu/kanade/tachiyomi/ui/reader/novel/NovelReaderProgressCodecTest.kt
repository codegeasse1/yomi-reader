package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Reader progress shares one `Long` column between four formats that are told apart only by the
 * range their value falls in. These tests pin down two properties the packing depends on: every
 * encoder round-trips through its own decoder, and no encoder can produce a value another decoder
 * would claim.
 */
class NovelReaderProgressCodecTest {

    /** How many decoders accept [value]. Anything other than 1 means the ranges have collided. */
    private fun decoderCount(value: Long): Int = listOfNotNull(
        decodeNativeScrollProgress(value),
        decodeWebScrollProgressPercent(value),
        decodePageReaderProgress(value),
    ).size

    @Test
    fun `a native scroll position survives the round trip`() {
        for (index in 0..999) {
            for (offset in listOf(0, 1, 500_000, 999_999)) {
                val decoded = decodeNativeScrollProgress(
                    encodeNativeScrollProgress(index = index, offsetPx = offset),
                )
                decoded shouldBe NativeScrollProgress(index = index, offsetPx = offset, totalItems = null)
            }
        }
    }

    @Test
    fun `a native scroll position with an item count survives the round trip`() {
        for (totalItems in listOf(1, 2, 999, 1_000, 999_999)) {
            for (index in listOf(0, totalItems / 2, totalItems - 1)) {
                for (offset in listOf(0, 999_999)) {
                    val decoded = decodeNativeScrollProgress(
                        encodeNativeScrollProgress(
                            index = index,
                            offsetPx = offset,
                            totalItems = totalItems,
                        ),
                    )
                    decoded shouldBe NativeScrollProgress(
                        index = index,
                        offsetPx = offset,
                        totalItems = totalItems,
                    )
                }
            }
        }
    }

    @Test
    fun `a page reader position survives the round trip`() {
        for (totalItems in listOf(1, 2, 999, 1_000, 999_999)) {
            for (index in listOf(0, totalItems / 2, totalItems - 1)) {
                decodePageReaderProgress(
                    encodePageReaderProgress(index = index, totalItems = totalItems),
                ) shouldBe PageReaderProgress(index = index, totalItems = totalItems)
            }
        }
    }

    @Test
    fun `a web scroll percentage survives the round trip`() {
        for (percent in 0..100) {
            decodeWebScrollProgressPercent(encodeWebScrollProgressPercent(percent)) shouldBe percent
        }
    }

    @Test
    fun `no encoded value is claimed by more than one decoder`() {
        val encoded = buildList {
            for (index in listOf(0, 1, 500, 998, 999)) {
                for (offset in listOf(0, 999_999)) {
                    add(encodeNativeScrollProgress(index = index, offsetPx = offset))
                }
            }
            for (percent in listOf(0, 50, 100)) {
                add(encodeWebScrollProgressPercent(percent))
            }
            for (totalItems in listOf(1, 999, 999_999)) {
                add(encodePageReaderProgress(index = 0, totalItems = totalItems))
                add(encodePageReaderProgress(index = totalItems - 1, totalItems = totalItems))
                add(
                    encodeNativeScrollProgress(
                        index = totalItems - 1,
                        offsetPx = 999_999,
                        totalItems = totalItems,
                    ),
                )
            }
        }

        for (value in encoded) {
            decoderCount(value) shouldBe 1
        }
    }

    @Test
    fun `the page reader range stops exactly where the with-total native range starts`() {
        // These two formats are adjacent, so the boundary value itself must belong to the native
        // format and the value below it must still be a page position.
        val boundary = 8_000_000_000_000_000L

        decodePageReaderProgress(boundary) shouldBe null
        (decodeNativeScrollProgress(boundary) != null) shouldBe true

        (decodePageReaderProgress(boundary - 1) != null) shouldBe true
    }

    @Test
    fun `a native index past the format's capacity is clamped instead of escaping its range`() {
        // Regression: the no-total encoder used to leave its range once the index reached 1 000.
        // Index 1 000 landed exactly on the web scroll marker and came back as "web scroll, 0%",
        // and index 2 000 landed in the page reader range and came back as a page position.
        for (index in listOf(1_000, 1_001, 2_000, 3_000, Int.MAX_VALUE)) {
            val value = encodeNativeScrollProgress(index = index, offsetPx = 0)

            decodeWebScrollProgressPercent(value) shouldBe null
            decodePageReaderProgress(value) shouldBe null
            decodeNativeScrollProgress(value) shouldBe NativeScrollProgress(
                index = 999,
                offsetPx = 0,
                totalItems = null,
            )
        }
    }

    @Test
    fun `out of range inputs are clamped rather than wrapped`() {
        decodeNativeScrollProgress(
            encodeNativeScrollProgress(index = -5, offsetPx = -1),
        ) shouldBe NativeScrollProgress(index = 0, offsetPx = 0, totalItems = null)

        decodeWebScrollProgressPercent(encodeWebScrollProgressPercent(-10)) shouldBe 0
        decodeWebScrollProgressPercent(encodeWebScrollProgressPercent(150)) shouldBe 100

        decodePageReaderProgress(
            encodePageReaderProgress(index = 50, totalItems = 10),
        ) shouldBe PageReaderProgress(index = 9, totalItems = 10)
    }
}
