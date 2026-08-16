package tachiyomi.core.common.util.system

import android.app.Application
import android.graphics.Bitmap
import okio.Buffer
import okio.BufferedSource
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ImageUtil.isTallImage decodes through tachiyomi.decoder.ImageDecoder, whose static initializer
 * loads the native "imagedecoder" library. That AAR ships Android ABI binaries only - there is no
 * host build - so these cases cannot run on the JVM, Robolectric included. They are skipped rather
 * than deleted: run them as an instrumentation test when the behaviour needs verifying.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ImageUtilTest {

    @Before
    fun requireNativeDecoder() {
        val decoderAvailable = runCatching {
            ImageUtil.isTallImage(createImageSource(width = 10, height = 10))
        }.isSuccess
        Assume.assumeTrue("native imagedecoder is not available on the host JVM", decoderAvailable)
    }

    @Test
    fun `very tall image is detected as tall`() {
        val imageSource = createImageSource(width = 200, height = 900)

        assertTrue(ImageUtil.isTallImage(imageSource))
    }

    @Test
    fun `normal image is not detected as tall`() {
        val imageSource = createImageSource(width = 900, height = 200)

        assertFalse(ImageUtil.isTallImage(imageSource))
    }

    @Test
    fun `borderline tall image is detected as tall`() {
        val imageSource = createImageSource(width = 200, height = 700)

        assertTrue(ImageUtil.isTallImage(imageSource))
    }

    private fun createImageSource(width: Int, height: Int): BufferedSource {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val output = Buffer()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output.outputStream())
        bitmap.recycle()
        return output
    }
}
