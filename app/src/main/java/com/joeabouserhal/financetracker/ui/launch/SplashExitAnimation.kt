package com.joeabouserhal.financetracker.ui.launch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageView

internal fun launchAnimationsEnabled(context: Context): Boolean =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    ValueAnimator.areAnimatorsEnabled()
  } else {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
  }

/** Animates the native splash itself, so there is no second splash or logo jump. */
internal class SplashExitAnimation(
  private val splash: View,
  private val logo: View,
  private val onFinished: () -> Unit,
) {
  private var animator: AnimatorSet? = null
  private var finished = false

  fun start(animate: Boolean) {
    if (finished || animator != null) return
    if (!animate) {
      finish()
      return
    }
    if (logo is ImageView) {
      // Android 12+ snapshots even static vectors into a low-resolution bitmap.
      // Replace that snapshot before expanding, retaining the platform's 1.5x
      // adaptive foreground inset. The pre-12 compat implementation has no inset.
      logo.scaleType = ImageView.ScaleType.FIT_XY
      logo.setImageDrawable(SplashLogoDrawable(logo.resources, if (Build.VERSION.SDK_INT >= 31) 1.5f else 1f))
    }
    // Keep taps away from the destination until the visual hand-off is complete.
    splash.isClickable = true
    splash.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    animator = createSplashAnimator(splash, logo).apply {
      addListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) = finish()
        override fun onAnimationCancel(animation: Animator) = finish()
      })
    }
    animator?.start()
  }

  /** Also removes the overlay when the activity leaves foreground mid-animation. */
  fun cancel() {
    animator?.cancel()
    finish()
  }

  private fun finish() {
    if (finished) return
    finished = true
    animator?.removeAllListeners()
    animator = null
    onFinished()
  }
}

internal fun createSplashAnimator(splash: View, logo: View): AnimatorSet {
  // Start at rest instead of immediately jumping toward the enlarged size.
  // Overlapping curves give the expansion and reveal one continuous movement.
  val expansion = PathInterpolator(0.32f, 0f, 0.18f, 1f)
  val scaleX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 1f, 3.5f).apply {
    duration = 520L
    interpolator = expansion
  }
  val scaleY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 1f, 3.5f).apply {
    duration = 520L
    interpolator = expansion
  }
  val fade = ObjectAnimator.ofFloat(splash, View.ALPHA, 1f, 0f).apply {
    startDelay = 60L
    duration = 620L
    interpolator = PathInterpolator(0.3f, 0f, 0.3f, 1f)
  }
  return AnimatorSet().apply {
    playTogether(scaleX, scaleY, fade)
  }
}
