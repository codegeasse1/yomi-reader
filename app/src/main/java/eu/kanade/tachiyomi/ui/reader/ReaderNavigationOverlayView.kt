package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import tachiyomi.core.common.i18n.stringResource
import kotlin.math.abs

class ReaderNavigationOverlayView(context: Context, attributeSet: AttributeSet) : View(
    context,
    attributeSet,
) {

    private var viewPropertyAnimator: ViewPropertyAnimator? = null

    private var navigation: ViewerNavigation? = null

    fun setNavigation(navigation: ViewerNavigation, showOnStart: Boolean) {
        this.navigation = navigation
        invalidate()

        // The listener can fire more than once while the reader starts (each registered
        // preference triggers updateNavigation), so auto-show must depend on the setting alone:
        // a firstLaunch-only guard would let a second call show the grid with the setting off.
        if (isVisible || !showOnStart || navigation is DisabledNavigation) {
            return
        }

        viewPropertyAnimator = animate()
            .alpha(1f)
            .setDuration(FADE_DURATION)
            .withStartAction {
                isVisible = true
            }
            .withEndAction {
                viewPropertyAnimator = null
            }
        viewPropertyAnimator?.start()
    }

    private val regionPaint = Paint()

    private val textPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val textBorderPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        color = Color.BLACK
        style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        if (navigation == null) return

        navigation?.getRegions()?.forEach { region ->
            val rect = region.rectF

            // Scale rect from 1f,1f to screen width and height
            canvas.withScale(width.toFloat(), height.toFloat()) {
                regionPaint.color = region.type.color
                drawRect(rect, regionPaint)
            }

            // Don't want scale anymore because it messes with drawText
            // Translate origin to rect start (left, top)
            canvas.withTranslation(x = (width * rect.left), y = (height * rect.top)) {
                val zoneWidth = width * abs(rect.left - rect.right)
                val zoneHeight = height * abs(rect.top - rect.bottom)

                // Calculate center of rect width & height on screen
                val x = zoneWidth / 2f
                val y = zoneHeight / 2f

                val text = context.stringResource(region.type.nameRes)
                val density = resources.displayMetrics.density
                val fontScale = resources.configuration.fontScale
                val spToPx = fontScale * density

                val baseTextSize = 16f * spToPx
                val maxAllowedWidth = zoneWidth * 0.85f

                textPaint.textSize = baseTextSize
                val textWidth = textPaint.measureText(text)
                if (textWidth > maxAllowedWidth && maxAllowedWidth > 0f) {
                    val scaledSize = baseTextSize * (maxAllowedWidth / textWidth)
                    textPaint.textSize = maxOf(scaledSize, 10f * spToPx)
                }
                textBorderPaint.textSize = textPaint.textSize
                textBorderPaint.strokeWidth = (textPaint.textSize / 8f).coerceAtLeast(3f)

                drawText(text, x, y, textBorderPaint)
                drawText(text, x, y, textPaint)
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()

        if (viewPropertyAnimator == null && isVisible) {
            viewPropertyAnimator = animate()
                .alpha(0f)
                .setDuration(FADE_DURATION)
                .withEndAction {
                    isVisible = false
                    viewPropertyAnimator = null
                }
            viewPropertyAnimator?.start()
        }

        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Hide overlay if user start tapping or swiping
        performClick()
        return super.onTouchEvent(event)
    }
}

private const val FADE_DURATION = 1000L
