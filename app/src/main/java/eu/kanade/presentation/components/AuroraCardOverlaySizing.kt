package eu.kanade.presentation.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class AuroraOverlayScaleTier {
    XXLarge,
    XLarge,
    Large,
    Medium,
    Small,
}

internal data class AuroraCardOverlaySpec(
    val buttonSizeDp: Dp,
    val buttonIconSizeDp: Dp,
    val progressTextSizeSp: TextUnit,
    val footerHorizontalPaddingDp: Dp,
    val footerVerticalPaddingDp: Dp,
    val progressTextEndInsetDp: Dp,
)

internal fun resolveAuroraOverlayScaleTier(
    gridColumns: Int?,
    cardWidthDp: Float?,
): AuroraOverlayScaleTier {
    val widthDp = cardWidthDp

    val fixedColumns = gridColumns?.takeIf { it > 0 }
    if (fixedColumns != null) {
        return when {
            fixedColumns == 1 -> AuroraOverlayScaleTier.XXLarge
            fixedColumns == 2 -> AuroraOverlayScaleTier.XLarge
            fixedColumns == 3 -> AuroraOverlayScaleTier.Large
            fixedColumns == 4 -> AuroraOverlayScaleTier.Medium
            else -> AuroraOverlayScaleTier.Small
        }
    }

    return when {
        widthDp == null -> AuroraOverlayScaleTier.Large
        widthDp >= 280f -> AuroraOverlayScaleTier.XXLarge
        widthDp >= 160f -> AuroraOverlayScaleTier.XLarge
        widthDp >= 112f -> AuroraOverlayScaleTier.Large
        widthDp >= 92f -> AuroraOverlayScaleTier.Medium
        else -> AuroraOverlayScaleTier.Small
    }
}

internal fun resolveAuroraCardOverlaySpec(
    gridColumns: Int?,
    cardWidthDp: Float?,
): AuroraCardOverlaySpec {
    return when (resolveAuroraOverlayScaleTier(gridColumns, cardWidthDp)) {
        AuroraOverlayScaleTier.XXLarge -> AuroraCardOverlaySpec(
            buttonSizeDp = 48.dp,
            buttonIconSizeDp = 28.dp,
            progressTextSizeSp = 16.sp,
            footerHorizontalPaddingDp = 24.dp,
            footerVerticalPaddingDp = 24.dp,
            progressTextEndInsetDp = 6.dp,
        )
        AuroraOverlayScaleTier.XLarge -> AuroraCardOverlaySpec(
            buttonSizeDp = 36.dp,
            buttonIconSizeDp = 22.dp,
            progressTextSizeSp = 14.sp,
            footerHorizontalPaddingDp = 12.dp,
            footerVerticalPaddingDp = 11.dp,
            progressTextEndInsetDp = 4.dp,
        )
        AuroraOverlayScaleTier.Large -> AuroraCardOverlaySpec(
            buttonSizeDp = 30.dp,
            buttonIconSizeDp = 18.dp,
            progressTextSizeSp = 13.sp,
            footerHorizontalPaddingDp = 10.dp,
            footerVerticalPaddingDp = 9.dp,
            progressTextEndInsetDp = 3.dp,
        )
        AuroraOverlayScaleTier.Medium -> AuroraCardOverlaySpec(
            buttonSizeDp = 27.dp,
            buttonIconSizeDp = 16.dp,
            progressTextSizeSp = 12.sp,
            footerHorizontalPaddingDp = 8.dp,
            footerVerticalPaddingDp = 7.dp,
            progressTextEndInsetDp = 2.dp,
        )
        AuroraOverlayScaleTier.Small -> AuroraCardOverlaySpec(
            buttonSizeDp = 24.dp,
            buttonIconSizeDp = 14.dp,
            progressTextSizeSp = 11.sp,
            footerHorizontalPaddingDp = 6.dp,
            footerVerticalPaddingDp = 6.dp,
            progressTextEndInsetDp = 1.dp,
        )
    }
}
