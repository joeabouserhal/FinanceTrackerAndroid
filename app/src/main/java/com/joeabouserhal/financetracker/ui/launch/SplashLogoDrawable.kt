package com.joeabouserhal.financetracker.ui.launch

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser
import com.joeabouserhal.financetracker.R

/** Draws actual curves at the current screen scale, never a cached icon bitmap. */
internal class SplashLogoDrawable(resources: Resources, private val viewportScale: Float) : Drawable() {
  private val path = requireNotNull(PathParser.createPathFromPathData(resources.getString(R.string.brand_logo_path)))
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

  override fun draw(canvas: Canvas) {
    val saved = canvas.save()
    canvas.translate(bounds.exactCenterX(), bounds.exactCenterY())
    val scale = minOf(bounds.width(), bounds.height()) / 432f * viewportScale
    canvas.scale(scale, scale)
    canvas.translate(-216f, -216f)
    canvas.drawPath(path, paint)
    canvas.restoreToCount(saved)
  }

  override fun setAlpha(alpha: Int) {
    paint.alpha = alpha
    invalidateSelf()
  }

  override fun getAlpha(): Int = paint.alpha

  override fun setColorFilter(colorFilter: ColorFilter?) {
    paint.colorFilter = colorFilter
    invalidateSelf()
  }

  @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
