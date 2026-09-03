package com.joeabouserhal.financetracker.ui.launch

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class SplashExitAnimationTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val splash = View(context)
  private val logo = View(context)
  private var removals = 0
  private val exit = SplashExitAnimation(splash, logo) { removals++ }

  @Test fun `logo expands evenly while splash fades then is removed`() {
    // Seek the timeline explicitly: Robolectric may finish animators immediately.
    val timeline = createSplashAnimator(splash, logo)
    assertEquals(680L, timeline.totalDuration)
    timeline.currentPlayTime = 16L
    assertTrue("First frame must not jump in size", logo.scaleX < 1.02f)
    timeline.currentPlayTime = 340L
    assertTrue(logo.scaleX > 1f)
    assertEquals(logo.scaleX, logo.scaleY, 0.001f)
    assertTrue("Mid-transition alpha was ${splash.alpha}", splash.alpha in 0.01f..0.99f)
    timeline.currentPlayTime = 520L
    assertEquals(3.5f, logo.scaleX, 0.001f)
    assertTrue("Fade should outlast the expansion", splash.alpha > 0f)
    timeline.currentPlayTime = 680L
    assertEquals(0f, splash.alpha, 0.001f)
    exit.start(animate = true)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    assertEquals(3.5f, logo.scaleX, 0.001f)
    assertEquals(0f, splash.alpha, 0.001f)
    assertEquals(1, removals)
    exit.cancel()
    assertEquals(1, removals)
  }

  @Test fun `disabled motion removes splash immediately without scaling`() {
    exit.start(animate = false)
    assertEquals(1, removals)
    assertEquals(1f, logo.scaleX, 0f)
    exit.start(animate = true)
    exit.cancel()
    assertEquals(1, removals)
  }

  @Test fun `backgrounding during transition removes overlay once`() {
    exit.start(animate = true)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150))
    exit.cancel()
    exit.cancel()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    assertEquals(1, removals)
  }

  @Test fun `cancel before start prevents later overlay animation`() {
    exit.cancel()
    exit.start(animate = true)
    assertEquals(1, removals)
    assertEquals(1f, logo.scaleX, 0f)
  }
}
