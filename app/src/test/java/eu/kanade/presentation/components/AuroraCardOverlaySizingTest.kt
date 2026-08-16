package eu.kanade.presentation.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraCardOverlaySizingTest {

    @Test
    fun `unspecified grid columns falls back to card width thresholds`() {
        resolveAuroraOverlayScaleTier(
            gridColumns = null,
            cardWidthDp = 120f,
        ) shouldBe AuroraOverlayScaleTier.Large

        resolveAuroraOverlayScaleTier(
            gridColumns = null,
            cardWidthDp = 72f,
        ) shouldBe AuroraOverlayScaleTier.Small
    }

    @Test
    fun `wide cards trigger xxlarge and xlarge overlay tier`() {
        resolveAuroraOverlayScaleTier(
            gridColumns = 1,
            cardWidthDp = 350f,
        ) shouldBe AuroraOverlayScaleTier.XXLarge

        resolveAuroraOverlayScaleTier(
            gridColumns = 2,
            cardWidthDp = 220f,
        ) shouldBe AuroraOverlayScaleTier.XLarge

        resolveAuroraOverlayScaleTier(
            gridColumns = null,
            cardWidthDp = 290f,
        ) shouldBe AuroraOverlayScaleTier.XXLarge

        resolveAuroraOverlayScaleTier(
            gridColumns = null,
            cardWidthDp = 200f,
        ) shouldBe AuroraOverlayScaleTier.XLarge
    }

    @Test
    fun `fixed columns map to expected overlay tiers`() {
        resolveAuroraOverlayScaleTier(
            gridColumns = 3,
            cardWidthDp = null,
        ) shouldBe AuroraOverlayScaleTier.Large

        resolveAuroraOverlayScaleTier(
            gridColumns = 4,
            cardWidthDp = null,
        ) shouldBe AuroraOverlayScaleTier.Medium

        resolveAuroraOverlayScaleTier(
            gridColumns = 5,
            cardWidthDp = null,
        ) shouldBe AuroraOverlayScaleTier.Small
    }

    @Test
    fun `auto columns fall back to card width thresholds`() {
        resolveAuroraOverlayScaleTier(
            gridColumns = 0,
            cardWidthDp = 120f,
        ) shouldBe AuroraOverlayScaleTier.Large

        resolveAuroraOverlayScaleTier(
            gridColumns = 0,
            cardWidthDp = 100f,
        ) shouldBe AuroraOverlayScaleTier.Medium

        resolveAuroraOverlayScaleTier(
            gridColumns = 0,
            cardWidthDp = 80f,
        ) shouldBe AuroraOverlayScaleTier.Small
    }

    @Test
    fun `overlay spec uses matching button and progress sizes for each tier`() {
        resolveAuroraCardOverlaySpec(
            gridColumns = 1,
            cardWidthDp = 350f,
        ).let { spec ->
            spec.buttonSizeDp.value shouldBe 48f
            spec.buttonIconSizeDp.value shouldBe 28f
            spec.progressTextSizeSp.value shouldBe 16f
            spec.footerHorizontalPaddingDp.value shouldBe 24f
            spec.footerVerticalPaddingDp.value shouldBe 24f
            spec.progressTextEndInsetDp.value shouldBe 6f
        }

        resolveAuroraCardOverlaySpec(
            gridColumns = 2,
            cardWidthDp = 200f,
        ).let { spec ->
            spec.buttonSizeDp.value shouldBe 36f
            spec.buttonIconSizeDp.value shouldBe 22f
            spec.progressTextSizeSp.value shouldBe 14f
            spec.footerHorizontalPaddingDp.value shouldBe 12f
            spec.footerVerticalPaddingDp.value shouldBe 11f
            spec.progressTextEndInsetDp.value shouldBe 4f
        }

        resolveAuroraCardOverlaySpec(
            gridColumns = 3,
            cardWidthDp = null,
        ).let { spec ->
            spec.buttonSizeDp.value shouldBe 30f
            spec.buttonIconSizeDp.value shouldBe 18f
            spec.progressTextSizeSp.value shouldBe 13f
            spec.footerHorizontalPaddingDp.value shouldBe 10f
            spec.footerVerticalPaddingDp.value shouldBe 9f
            spec.progressTextEndInsetDp.value shouldBe 3f
        }

        resolveAuroraCardOverlaySpec(
            gridColumns = 4,
            cardWidthDp = null,
        ).let { spec ->
            spec.buttonSizeDp.value shouldBe 27f
            spec.buttonIconSizeDp.value shouldBe 16f
            spec.progressTextSizeSp.value shouldBe 12f
            spec.footerHorizontalPaddingDp.value shouldBe 8f
            spec.footerVerticalPaddingDp.value shouldBe 7f
            spec.progressTextEndInsetDp.value shouldBe 2f
        }

        resolveAuroraCardOverlaySpec(
            gridColumns = 6,
            cardWidthDp = null,
        ).let { spec ->
            spec.buttonSizeDp.value shouldBe 24f
            spec.buttonIconSizeDp.value shouldBe 14f
            spec.progressTextSizeSp.value shouldBe 11f
            spec.footerHorizontalPaddingDp.value shouldBe 6f
            spec.footerVerticalPaddingDp.value shouldBe 6f
            spec.progressTextEndInsetDp.value shouldBe 1f
        }
    }
}
