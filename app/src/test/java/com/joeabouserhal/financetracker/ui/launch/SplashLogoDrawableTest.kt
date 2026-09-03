package com.joeabouserhal.financetracker.ui.launch

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import com.joeabouserhal.financetracker.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplashLogoDrawableTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()

  private fun render(drawable: Drawable, size: Int): Bitmap =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
      drawable.setBounds(0, 0, size, size)
      drawable.draw(Canvas(it))
    }

  private fun overlap(a: Bitmap, b: Bitmap): Double {
    var intersection = 0
    var union = 0
    for (y in 0 until a.height) for (x in 0 until a.width) {
      val first = Color.alpha(a.getPixel(x, y)) >= 128
      val second = Color.alpha(b.getPixel(x, y)) >= 128
      if (first || second) union++
      if (first && second) intersection++
    }
    return intersection.toDouble() / union
  }

  @Test fun `trace retains the original silhouette and padding`() {
    val original = BitmapFactory.decodeResource(context.resources, R.drawable.ic_splash_logo)
    val vector = render(SplashLogoDrawable(context.resources, 1f), 432)
    val similarity = overlap(original, vector)
    assertTrue("Original silhouette overlap: $similarity", similarity > 0.96)
  }

  @Test fun `exit curves match native vector at three times size`() {
    val nativeVector = requireNotNull(context.getDrawable(R.drawable.ic_splash_logo_vector))
    val vector = render(nativeVector, 1296)
    val exit = render(SplashLogoDrawable(context.resources, 1f), 1296)
    val similarity = overlap(vector, exit)
    assertTrue("Native and exit vector overlap: $similarity", similarity > 0.995)
  }
}
