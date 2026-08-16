package eu.kanade.presentation.reader.components

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChapterNavigatorVerticalSliderTest {

    private val range = 1f..10f
    private val height = 100f

    @Test
    fun `top edge of the bar maps to the start value`() {
        verticalSliderValueFromPosition(0f, height, range) shouldBe 1f
    }

    @Test
    fun `bottom edge of the bar maps to the end value`() {
        verticalSliderValueFromPosition(height, height, range) shouldBe 10f
    }

    @Test
    fun `position below the middle maps to the first half of the range`() {
        verticalSliderValueFromPosition(25f, height, range) shouldBe 3.25f
    }

    @Test
    fun `positions outside the bar are clamped`() {
        verticalSliderValueFromPosition(-10f, height, range) shouldBe 1f
        verticalSliderValueFromPosition(height + 10f, height, range) shouldBe 10f
    }

    @Test
    fun `zero height falls back to the start value`() {
        verticalSliderValueFromPosition(50f, 0f, range) shouldBe 1f
    }

    @Test
    fun `thumb sits at the top for the start value and at the bottom for the end value`() {
        verticalSliderThumbOffset(1, height, range) shouldBe 0f
        verticalSliderThumbOffset(10, height, range) shouldBe height
    }

    @Test
    fun `thumb moves downward as the value grows`() {
        (verticalSliderThumbOffset(2, height, range) > verticalSliderThumbOffset(1, height, range)).shouldBeTrue()
    }
}
